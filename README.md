# Talleres360 — Microservicio de auditoría

Servicio independiente que registra eventos de negocio de las órdenes: quién las creó, aceptó, actualizó, entregó o canceló y cuándo ocurrió. Ofrece una línea de tiempo consultable por orden, actor, tipo y fecha. No calcula ventas ni reemplaza el microservicio de reportería.

## Tecnologías

Java 21, Spring Boot 3.5.6, Spring Web, Spring Data JPA, Bean Validation, Lombok, Spring Kafka y OpenAPI. PostgreSQL en Compose, H2 en memoria para desarrollo y pruebas. Incluye controlador JDBC y perfil de conexión Oracle.

## Estructura

```text
src/main/java/com/talleres360/auditoria/
├── controller/    Ingesta interna y consultas HTTP de la línea de tiempo.
├── dto/           Evento de entrada, vista pública y paginación.
├── model/         Registro de auditoría inmutable para Hibernate.
├── repository/    Persistencia y consultas filtradas.
├── service/       Validación, deduplicación y reglas de consulta.
├── messaging/     Consumidor Kafka.
└── config/        Credencial interna, errores, OpenAPI y recuperación Kafka.
src/main/resources/application.yml
Dockerfile         Construcción por etapas; usuario no root.
compose.yml        Aplicación y PostgreSQL con volumen independiente.
.env.example       Configuración sin credenciales reales.
```

## Funcionamiento

1. Recibe un evento por HTTP interno o Kafka y valida su contenido.
2. Usa `eventId` como identidad estable: repetir un evento no duplica la línea de tiempo. Reutilizar la identidad con datos diferentes se rechaza.
3. Guarda actor, motivo, orden, tipo, estado, total, instante del evento, origen e identificador de correlación.
4. Expone consultas paginadas ordenadas por fecha descendente y por identidad para desempatar.

Las fechas se almacenan en UTC con precisión de microsegundos. Las consultas usan un intervalo **desde inclusive, hasta exclusivo**. Un actor se busca por coincidencia exacta sin distinguir mayúsculas. Los tipos admitidos son `CREADA`, `ACTUALIZADA`, `ACEPTADA`, `EN_REPARACION`, `LISTA_PARA_ENTREGA`, `ENTREGADA`, `CANCELADA`, `ELIMINADA` e `INFORME_ACTUALIZADO`.

No hay operaciones públicas de creación, edición ni eliminación de auditorías. Hibernate trata la entidad como inmutable. Esto no vuelve la base inviolable: las credenciales y permisos de base de datos, los respaldos y la retención también deben proteger el historial.

La clave primaria impide sobrescribir eventos durante inserciones concurrentes. En la primera inserción simultánea HTTP puede devolver `409`; se reintenta con el mismo `eventId` y contenido. Kafka reintenta los errores mediante su manejador configurado.

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

Ejemplo de evento HTTP o mensaje Kafka:

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
| `KAFKA_ENABLED` | Activar el consumidor Kafka; desactivado por defecto. |
| `KAFKA_BOOTSTRAP_SERVERS` | Direcciones de los brokers accesibles desde el servicio. |
| `KAFKA_ORDERS_TOPIC` | Topic de entrada, `orders.events` por defecto. |
| `KAFKA_GROUP_ID` | Grupo exclusivo del consumidor, `talleres360-auditoria` por defecto. |
| `DDL_AUTO` | Gestión de esquema, `update` en este laboratorio. |

El perfil Oracle utiliza `DB_URL=jdbc:oracle:thin:@//HOST:1521/SERVICIO`, usuario y contraseña propios. No transforma el Compose ni migra datos. Las pruebas automatizadas usan H2; Oracle requiere comprobar conexión y esquema con una base real. Para un entorno productivo se deben versionar las migraciones y utilizar permisos de base de datos acotados.

## Kafka y conexión con el sistema

Al activar Kafka, el consumidor utiliza un grupo independiente de Reportería: ambos pueden leer los mismos eventos sin repartírselos entre sí. El desplazamiento se confirma después de que el método termina y se confirma la transacción de base de datos. Un reinicio entre ambas confirmaciones puede repetir un evento; la deduplicación evita otro registro.

Un fallo se reintenta dos veces con un segundo entre intentos. Si continúa, el recuperador publica el mensaje en `<topic>.audit.DLT`, conservando la partición. El topic de recuperación debe existir con suficientes particiones; el servicio espera confirmación de publicación antes de considerar recuperado el mensaje. El servicio no crea brokers, topics, particiones, ACL ni clústeres.

La ingesta HTTP permite pruebas independientes sin Kafka. No sustituye el flujo Kafka del caso. Las operaciones públicas son de lectura; el productor de eventos es Órdenes. No se aceptan actores obtenidos libremente del navegador: el productor debe derivarlos de la identidad autenticada.

API Gateway sigue teniendo al BFF como destino. Órdenes envía todos sus eventos mediante un outbox persistente a `/internal/events`. Configurar `AUDIT_URL=http://IP_PRIVADA:8085` en Órdenes y BFF y la misma clave interna. El BFF enruta `/api/audit`, `/api/audit/events` y el alias del dashboard `/api/reports/audit` a este servicio, exclusivamente para Admin. El frontend incluye una pantalla de solo lectura con filtros por orden, usuario, evento y fechas.

El permiso Admin corresponde a la sección específica de la pantalla de auditoría del caso. La tabla general menciona Auditor, pero no se crea ese rol adicional en Azure. Si Gateway usa rutas explícitas, debe admitir las nuevas lecturas con el authorizer existente. Esta integración de código no configura EC2, Gateway, Kafka ni Azure.

## Seguridad y pruebas

No publicar `.env`, claves internas, contraseñas ni PEM. No utilizar la clave interna desde el frontend. Restringir el puerto de este servicio a conexiones autorizadas y utilizar TLS cuando corresponda. La validación de usuarios Azure pertenece al BFF; este servicio valida la credencial de comunicación interna y el rol recibido de ese emisor confiable.

La revisión local del 8 de octubre verificó persistencia, idempotencia, permisos, filtros, paginación y lectura JSON de eventos con H2. No verificó un broker Kafka, Oracle ni EC2. Los archivos de pruebas no forman parte de esta versión; el empaquetado no sustituye la validación del despliegue.
