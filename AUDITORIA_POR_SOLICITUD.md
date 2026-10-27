# Consulta de auditoría por solicitud

`GET /api/audit/solicitudes` ofrece bloques de solicitudes para el dashboard y
el historial de auditoría. Exige la misma clave interna y rol Admin que la
consulta de eventos. El navegador accede mediante el BFF con JWT.

Acepta `orderId`, `actor`, `type`, `from`, `to`, `page` y `size`.
La página empieza en cero; su tamaño predeterminado es diez y el máximo es cien.
La respuesta conserva el contrato `content`, `page`, `size`, `totalElements` y
`totalPages`. Cada elemento contiene `orderId`, `eventos` y `ultimaActividad`.
Los conteos y la paginación corresponden a solicitudes, no a eventos.

La base agrupa los movimientos que coinciden con los filtros y ordena por última
actividad descendente, con ID de solicitud descendente para desempatar. No se
carga todo el historial en memoria para construir los grupos.

Al abrir un bloque se usa la consulta existente `GET /api/audit?orderId=...`,
aplicando también los filtros seleccionados. El historial puede paginarse dentro
del bloque sin repetir la solicitud en la lista principal. Los eventos, sus IDs
y la ingesta permanecen intactos; no se requieren migraciones de datos.
