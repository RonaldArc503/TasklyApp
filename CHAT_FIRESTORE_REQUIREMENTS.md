# Requisitos Firestore de Chat

## Datos agregados

- `users/{uid}/chatStates/{conversationId}`: preferencias privadas `pinned`,
  `pinnedAt`, `archived`, `archivedAt`.
- `conversations/{conversationId}/receipts/{uid}`: cursores monotónicos
  `lastDeliveredAt`, `lastDeliveredMessageId`, `lastReadAt` y
  `lastReadMessageId`.
- Mensajes: `conversationId`, `searchTerms` normalizados y `contentKinds`.
- `conversationSearch/{hash}`: índice por usuario para buscar conversaciones.
- `globalChatSearch/{hash}`: índice por usuario para localizar mensajes y
  contenido sin descargar historiales.

Los mensajes antiguos se indexan incrementalmente al cargar páginas existentes.
Esto no borra ni reescribe contenido. Una migración administrativa opcional puede
recorrer los mensajes antiguos por lotes para obtener cobertura global completa
antes de que cada conversación sea visitada.

## Reglas requeridas

El repositorio no contiene un archivo de reglas Firestore, por lo que no se marca
ningún cambio de reglas como desplegado. En el proyecto Firebase deben garantizarse
estas condiciones con mínimo privilegio:

- Solo participantes pueden leer conversaciones, mensajes y recibos.
- Cada usuario solo puede escribir su propio recibo y su propio documento
  `users/{uid}/chatStates/{conversationId}`.
- Un usuario solo puede leer documentos `conversationSearch` y
  `globalChatSearch` cuyo `userId` sea el suyo.
- La creación/actualización de índices de búsqueda debe permitirse únicamente si
  el usuario autenticado participa en la conversación referenciada.
- La escritura de mensajes debe validar que `senderId` coincide con el usuario
  autenticado y que pertenece a la conversación.

## Coste esperado

- Abrir un chat añade un listener a `receipts` y como máximo una escritura de
  entrega por lote nuevo recibido.
- La lectura usa un cursor con debounce; no escribe por mensaje ni por píxel.
- Enviar un mensaje añade un documento de búsqueda global por participante y una
  actualización de desarchivado por receptor.
- Buscar usa consultas limitadas (30/50/60/100 según pantalla) y nunca descarga
  colecciones completas.
- La carga histórica usa una lectura directa y dos consultas limitadas alrededor
  de `createdAt`.
