# Talleres360 — Microservicio de auditoría

Servicio independiente que registra eventos de negocio de las órdenes: quién las creó, aceptó, actualizó, entregó o canceló y cuándo ocurrió. Ofrece una línea de tiempo consultable por orden, actor, tipo y fecha. No calcula ventas ni reemplaza el microservicio de reportería.

Documentación actualizada el **22 de octubre de 2026**. Repositorio independiente,
rama `emmanuel-audi`, puerto **8085**. Registra las acciones de Talleres360, un único
taller, conservando también las órdenes históricas. No necesita seleccionar región
o sucursal ni configura usuarios en Entra ID.

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

Ejemplo de evento HTTP con datos ficticios. Sustituye el marcador por un UUID
válido generado para ese evento; no es un ID de cuenta o aplicación:

```json
{
  "eventId": "<UUID_DEL_EVENTO>",
  "orderId": 15,
  "type": "ACEPTADA",
  "actor": "operador@example.invalid",
  "reason": "Solicitud aceptada por el taller",
  "occurredAt": "2026-10-08T15:30:00Z",
  "status": "ACEPTADA",
  "total": 35000,
  "source": "ms-talleres360-orders",
  "correlationId": "solicitud-15"
}
```

Los nombres externos en inglés conservan el contrato de eventos de Órdenes. `source` y `correlationId` son opcionales; si se omite el origen se utiliza `ms-talleres360-orders`. El productor conserva `eventId` durante todos sus reintentos. Se valida que el total sea no negativo y tenga como máximo diez dígitos enteros y dos decimales.

## Ejecución local

Requisitos: JDK 21 y acceso para descargar dependencias en la primera ejecución. Maven Wrapper está incluido.

En PowerShell, desde la carpeta del proyecto:

```powershell
$env:INTERNAL_API_KEY='<CLAVE_INTERNA_PRIVADA>'
.\mvnw.cmd '-DskipTests' package
.\mvnw.cmd spring-boot:run
```

Sustituye el marcador por una clave privada. En Linux/macOS, usar `./mvnw` y exportar
la variable. El perfil `local` utiliza H2 en memoria: los registros se pierden al
detener el proceso. Maven no carga `.env` automáticamente; ese archivo se utiliza
con Compose.

## Docker Compose independiente

1. Copiar `.env.example` como `.env`.
2. Completar `DB_PASSWORD` e `INTERNAL_API_KEY` con valores propios. Elegir `DB_USERNAME` o conservar `talleres360`.
3. Ejecutar:

```sh
docker compose up -d --build
```

Se construye la imagen `talleres360/ms-auditoria:local`. PostgreSQL no publica su puerto al host; la aplicación espera su chequeo de salud. Ambos contenedores usan `restart: unless-stopped`, que requiere que el motor Docker arranque con el equipo. Los registros permanecen en el volumen `talleres360-auditoria_datos_postgres`.

El archivo privado es **`.env` en la raíz de este repositorio**, junto a `compose.yml`:

```dotenv
DB_USERNAME=<USUARIO_BASE_AUDITORIA>
DB_PASSWORD=<CONTRASENA_PRIVADA_BASE>
INTERNAL_API_KEY=<CLAVE_INTERNA_COMPARTIDA>
```

Los marcadores no son credenciales utilizables: reemplázalos fuera de Git. Compose
configura el perfil PostgreSQL y la base `audit_db`; no necesitas escribir IDs de
Entra, URLs del frontend ni credenciales SMTP o RabbitMQ en este servicio.

## Configuración

| Variable | Uso |
| --- | --- |
| `SERVER_PORT` | Puerto Java; 8085 por defecto. Compose usa 8085. |
| `INTERNAL_API_KEY` | Clave de los emisores autorizados. Sin clave se rechazan las operaciones protegidas. |
| `SPRING_PROFILES_ACTIVE` | `local`, `postgres` u `oracle`; Compose usa `postgres`. |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexión externa con perfiles PostgreSQL u Oracle; Compose configura su propia URL PostgreSQL. |
| `DDL_AUTO` | Gestión de esquema, `update` en este laboratorio. |

El perfil Oracle utiliza `DB_URL=jdbc:oracle:thin:@//HOST:1521/SERVICIO`, usuario y
contraseña propios. No transforma el Compose ni migra datos. Un chequeo con H2 no
verifica conexión o esquema Oracle. Para producción se deben versionar las
migraciones y utilizar permisos de base acotados.

## Conexión con el sistema

La ingesta HTTP es ahora el transporte principal. Los reintentos corresponden al
outbox de Órdenes y son independientes de Reportería y Notificaciones. Las
operaciones públicas son de lectura. El productor deriva el actor de la identidad
autenticada. No se borran ni recrean las tablas durante este cambio.

API Gateway sigue teniendo al BFF como destino. Órdenes envía sus eventos mediante
un outbox persistente a `/internal/events`. Configura
`AUDIT_URL=http://<IP_PRIVADA_AUDITORIA>:8085` en **Órdenes y BFF**, junto con la
misma clave interna. BFF enruta `/api/audit`, `/api/audit/events` y el alias del
dashboard `/api/reports/audit` a este servicio, exclusivamente para Admin. El
frontend muestra filtros por orden, usuario, evento y fechas. El navegador no
envía directamente eventos al servicio.

El permiso Admin corresponde a la pantalla de auditoría; no requiere otro rol.
Autoriza el puerto 8085 solo desde BFF y Órdenes. Kafka no participa en esta versión;
la conservación del outbox permite reintentar cuando Auditoría vuelve a estar disponible.

## Actualización y conservación

Comprueba rama y cambios locales antes de actualizar:

```bash
git pull --ff-only origin emmanuel-audi
docker compose up -d --build
docker compose ps
```

Si existen archivos Compose adicionales, conserva los mismos `-f` al actualizar.
No sobrescribas `.env` ni uses `docker compose down -v` para una actualización:
el volumen contiene la trazabilidad. Eliminar una orden no debe borrar su auditoría
ni reutilizar sus identificadores. El reinicio automático de contenedores requiere
Docker activo; no enciende una EC2 apagada ni actualiza código de GitHub.

## Seguridad y pruebas

No publicar `.env`, claves internas, contraseñas ni PEM. No utilizar la clave interna desde el frontend. Restringir el puerto de este servicio a conexiones autorizadas y utilizar TLS cuando corresponda. La validación de usuarios Azure pertenece al BFF; este servicio valida la credencial de comunicación interna y el rol recibido de ese emisor confiable.

Los archivos de pruebas no forman parte de esta versión. El empaquetado local no sustituye comprobar el despliegue real ni la conexión Oracle.

Los ejemplos contienen datos ficticios. Mantén fuera del README direcciones reales,
IDs de cuenta/tenant, identidades de clientes, contenido privado de eventos y
volcados de base. No hay CRUD público de auditoría: es ingesta interna y consulta,
porque permitir editar o borrar eventos desde el panel destruiría su trazabilidad.
