# Talleres360 — Microservicio de auditoría

Servicio independiente que registra eventos de negocio de las órdenes: quién las creó, aceptó, actualizó, entregó o canceló y cuándo ocurrió. Ofrece una línea de tiempo consultable por orden, actor, tipo y fecha. No calcula ventas ni reemplaza el microservicio de reportería.

## Tecnologías

Java 21, Spring Boot 3.5.6, Spring Web, Spring Data JPA, Bean Validation, Lombok y OpenAPI. PostgreSQL en Compose y H2 en memoria. Incluye controlador JDBC y perfil de conexión Oracle.

## Estructura

```text
src/main/java/com/talleres360/auditoria/
├── controller/    Ingesta interna y consultas HTTP de la línea de tiempo.
├── dto/           Evento de entrada, vista pública y paginación.
├── model/         Registro de auditoría inmutable para Hibernate.
├── repository/    Persistencia y consultas filtradas.
├── service/       Validación, deduplicación y reglas de consulta.
└── config/        Credencial interna, errores y OpenAPI.
src/main/resources/application.yml
Dockerfile         Construcción por etapas; usuario no root.
compose.yml        Aplicación y PostgreSQL con volumen independiente.
.env.example       Configuración sin credenciales reales.
```

## Funcionamiento

Desde el 21 de octubre de 2026 Kafka y su infraestructura fueron retirados del
repositorio. Órdenes envía los eventos mediante su outbox HTTP persistente a
`POST /internal/events`, confirma cada respuesta exitosa y reintenta lo pendiente.
Los eventos históricos permanecen en la base. La línea de tiempo y las consultas
del frontend a través del BFF no dependen del broker.

1. Recibe un evento por HTTP interno y valida su contenido.
2. Usa `eventId` como identidad estable: repetir un evento no duplica la línea de tiempo. Reutilizar la identidad con datos diferentes se rechaza.
3. Guarda actor, motivo, orden, tipo, estado, total, instante del evento, origen e identificador de correlación.
4. Expone consultas paginadas ordenadas por fecha descendente y por identidad para desempatar.

Las fechas se almacenan en UTC con precisión de microsegundos. Las consultas usan un intervalo **desde inclusive, hasta exclusivo**. Un actor se busca por coincidencia exacta sin distinguir mayúsculas. Los tipos admitidos son `CREADA`, `ACTUALIZADA`, `ACEPTADA`, `EN_REPARACION`, `LISTA_PARA_ENTREGA`, `ENTREGADA`, `CANCELADA`, `ELIMINADA` e `INFORME_ACTUALIZADO`.

No hay operaciones públicas de creación, edición ni eliminación de auditorías. Hibernate trata la entidad como inmutable. Esto no vuelve la base inviolable: las credenciales y permisos de base de datos, los respaldos y la retención también deben proteger el historial.

La clave primaria impide sobrescribir eventos durante inserciones concurrentes. En la primera inserción simultánea HTTP puede devolver `409`; el outbox reintenta con el mismo `eventId` y contenido.

## API

Puerto predeterminado: **8085**.

| Método | Ruta | Función |
| --- | --- | --- |
| POST | `/internal/events` | Registrar evento; respuesta `204`. |
| GET | `/api/audit/events` | Línea de tiempo paginada y filtrable. |
| GET | `/api/audit` | Alias de la consulta paginada. |
| GET | `/api/reports/audit` | Compatibilidad: lista de hasta 100 eventos recientes, filtro `orderId`. |
| GET | `/actuator/health` | Salud sin detalles internos. |
| GET | `/swagger-ui/index.html` | Documentación interactiva. |
| GET | `/v3/api-docs` | Especificación OpenAPI. |

La ingesta y las consultas requieren `X-Internal-Key`. Las consultas también requieren `X-Actor-Role: Admin`. El rol debe proceder del BFF después de validar al usuario, no de un encabezado enviado libremente por el navegador.

Filtros de la API paginada: `orderId`, `actor`, `type`, `from`, `to`, `page` y `size`. `page` empieza en 0, `size` es 20 por defecto y su máximo es 100. La respuesta contiene `content`, `page`, `size`, `totalElements` y `totalPages`.

Ejemplo de evento HTTP:

```json
{
  "eventId": "6aff7699-5367-42df-9fbc-232de2627c80",
  "orderId": 15,
  "type": "ACEPTADA",
  "actor": "operador@ejemplo.cl",
  "reason": "Solicitud aceptada por el taller",
  "occurredAt": "2026-10-08T15:30:00Z",
  "status": "ACEPTADA",
  "total": 35000,
  "source": "ms-talleres360-orders",
  "correlationId": "solicitud-15"
}
```

Los nombres externos en inglés conservan el contrato de eventos de Órdenes. `source` y `correlationId` son opcionales; si se omite el origen se utiliza `ms-talleres360-orders`. El productor conserva `eventId` durante todos sus reintentos. Se valida que el total sea no negativo y tenga como máximo diez dígitos enteros y dos decimales.

## Ejecutar y probar

Requisitos: JDK 21 y acceso para descargar dependencias en la primera ejecución. Maven Wrapper está incluido.

En PowerShell, desde la carpeta del proyecto:

```powershell
$env:INTERNAL_API_KEY='una-clave-interna-larga-para-pruebas-locales'
.\mvnw.cmd '-DskipTests' package
.\mvnw.cmd spring-boot:run
```

En Linux/macOS, usar `./mvnw` y exportar la variable. El perfil `local` utiliza H2 en memoria: los registros se pierden al detener el proceso. Maven no carga `.env` automáticamente; ese archivo se utiliza con Compose.

## Docker Compose independiente

1. Copiar `.env.example` como `.env`.
2. Completar `DB_PASSWORD` e `INTERNAL_API_KEY` con valores propios. Elegir `DB_USERNAME` o conservar `talleres360`.
3. Ejecutar:

```sh
docker compose up -d --build
```

Se construye la imagen `talleres360/ms-auditoria:local`. PostgreSQL no publica su puerto al host; la aplicación espera su chequeo de salud. Ambos contenedores usan `restart: unless-stopped`, que requiere que el motor Docker arranque con el equipo. Los registros permanecen en el volumen `talleres360-auditoria_datos_postgres`.

## Configuración

| Variable | Uso |
| --- | --- |
| `SERVER_PORT` | Puerto Java; 8085 por defecto. Compose usa 8085. |
| `INTERNAL_API_KEY` | Clave de los emisores autorizados. Sin clave se rechazan las operaciones protegidas. |
| `SPRING_PROFILES_ACTIVE` | `local`, `postgres` u `oracle`; Compose usa `postgres`. |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexión externa con perfiles PostgreSQL u Oracle; Compose configura su propia URL PostgreSQL. |
| `DDL_AUTO` | Gestión de esquema, `update` en este laboratorio. |

El perfil Oracle utiliza `DB_URL=jdbc:oracle:thin:@//HOST:1521/SERVICIO`, usuario y contraseña propios. No transforma el Compose ni migra datos. Las pruebas automatizadas usan H2; Oracle requiere comprobar conexión y esquema con una base real. Para un entorno productivo se deben versionar las migraciones y utilizar permisos de base de datos acotados.

## Conexión con el sistema

La ingesta HTTP es ahora el transporte principal. Los reintentos corresponden al
outbox de Órdenes y son independientes de Reportería y Notificaciones. Las
operaciones públicas son de lectura. El productor deriva el actor de la identidad
autenticada. No se borran ni recrean las tablas durante este cambio.

API Gateway sigue teniendo al BFF como destino. Órdenes envía todos sus eventos mediante un outbox persistente a `/internal/events`. Configurar `AUDIT_URL=http://IP_PRIVADA:8085` en Órdenes y BFF y la misma clave interna. El BFF enruta `/api/audit`, `/api/audit/events` y el alias del dashboard `/api/reports/audit` a este servicio, exclusivamente para Admin. El frontend incluye una pantalla de solo lectura con filtros por orden, usuario, evento y fechas.

El permiso Admin corresponde a la pantalla de auditoría. No se crea un rol adicional en Azure. Gateway conserva al BFF como destino. Esta modificación local no cambia EC2, Gateway ni Azure.

## Seguridad y pruebas

No publicar `.env`, claves internas, contraseñas ni PEM. No utilizar la clave interna desde el frontend. Restringir el puerto de este servicio a conexiones autorizadas y utilizar TLS cuando corresponda. La validación de usuarios Azure pertenece al BFF; este servicio valida la credencial de comunicación interna y el rol recibido de ese emisor confiable.

Los archivos de pruebas no forman parte de esta versión. El empaquetado local no sustituye comprobar el despliegue real ni la conexión Oracle.
