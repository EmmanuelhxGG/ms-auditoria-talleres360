# Kafka de Talleres360

Despliegue independiente para `ec2-talleres360-kafka`: tres ZooKeeper, tres brokers,
Kafka UI y un administrador Spring Boot. Los cinco microservicios mantienen sus
EC2 independientes; no se incorpora `ec2-apps`.

Se fija Confluent Platform 7.9.6 porque esta versión admite ZooKeeper, requerido
por la pauta. No cambiar directamente a Kafka 4, que usa KRaft y no ZooKeeper.

## Configuración

1. Copiar `.env.example` como `.env` en esta carpeta.
2. Poner la IP privada de esta EC2 en `KAFKA_PRIVATE_IP` y la clave interna en
   `INTERNAL_API_KEY`. No publicar el archivo.
3. Ejecutar `docker compose up -d --build`.
4. Con tres brokers listos, inicializar mediante
   `POST /api/messaging/kafka/initialize`, con `X-Internal-Key` y
   `X-Actor-Role: Admin`. El acceso público pasa por el BFF y su JWT Admin.

Para no almacenar las capas de Maven en el disco de 8 GB, puede compilarse el
administrador con Java 21/Maven y construir su imagen con
`administrador/Dockerfile.runtime`, utilizando un contexto separado que contenga
ese Dockerfile y el JAR renombrado `app.jar`.

## Eventos y consumidores

| Tópico | Particiones | Réplicas | Política |
|---|---:|---:|---|
| `orders.events` | 3 | 3 | delete, 7 días |
| `audit.timeline` | 3 | 3 | compact,delete, 14 días |
| `orders.events.report.DLT` | 3 | 3 | delete, 7 días |
| `orders.events.audit.DLT` | 3 | 3 | delete, 7 días |

Órdenes publica desde su outbox con clave de partición igual al ID de orden,
`acks=all` y productor idempotente. Reportería y Auditoría usan grupos distintos,
por lo que ambos reciben cada evento. Sus escrituras son idempotentes por eventId.
Auditoría también publica `audit.timeline`, con eventId como clave de compactación.
El offset avanza tras el procesamiento; después de los reintentos se publica al
DLT específico del consumidor. Los DLT conservan el mensaje original y metadatos
de error de Spring Kafka.

La entrega es al menos una vez, no una transacción distribuida exactamente una
vez: un reintento puede repetir el mensaje, pero no debe duplicar el efecto en la
base de datos. La clave de orden conserva la partición de sus eventos.

## Administración y acceso

Bajo `/api/messaging/kafka`: GET `nodes`, `topics`, `topics/{nombre}/config`;
POST `topics`, `initialize`; PUT `topics/{nombre}/config`; DELETE
`topics/{nombre}`. Solo se eliminan tópicos `demo.*`; los de negocio se protegen.
Se validan nombres, particiones, replicación y configuraciones admitidas.

- 9092–9094: solo grupos de seguridad de Órdenes, Reportería y Auditoría.
- 8087: solo grupo de seguridad del BFF.
- 22: solo IP pública del administrador `/32`.
- Kafka UI: `127.0.0.1:8088`; acceder por túnel SSH, no abrirlo a Internet.
- ZooKeeper y listeners entre brokers: únicamente red interna de Compose.

## Recursos y límites

La EC2 necesita RAM suficiente para los siete procesos Java y Kafka UI; el tamaño
del disco (8 GB) no equivale a memoria RAM. Se limita cada partición a 32 MiB y cada
segmento a 8 MiB, además de rotar logs. El límite de tamaño puede eliminar eventos
antes de los 7/14 días: es una configuración pequeña para el laboratorio, no para
retención productiva. Supervisar espacio y evitar grandes pruebas de carga.

Todos los nodos están en una sola EC2: las tres réplicas no protegen contra la
pérdida de esa máquina o su disco. En producción se separan brokers y ZooKeeper
en hosts diferentes. Los listeners externos son privados PLAINTEXT, aislados por
grupos de seguridad; una implantación productiva debe incorporar TLS/SASL.
