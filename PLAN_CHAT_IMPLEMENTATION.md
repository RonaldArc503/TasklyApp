# Plan de implementacion del modulo Chat

Alcance exclusivo: mejoras CHAT A-D aprobadas. No modificar la semantica de
"Eliminar para todos" ni agregar seleccion multiple de mensajes.

## CHAT A - Base y navegacion historica

- [x] Agregar carga acotada de una ventana de mensajes por ID en Repository/UseCase.
- [x] Exponer solicitud reusable de navegacion desde ChatViewModel.
- [x] Conectar replies y fijados al cargador, scroll exacto y resaltado temporal.
- [x] Implementar busqueda de perfiles y creacion/reuso idempotente de DM.
- [x] Mostrar presencia real solo para participantes de conversaciones visibles.
- [x] Ejecutar `:app:compileDebugKotlin` y `testDebugUnitTest`.
- [x] Revisar listeners, lecturas acotadas, duplicados y regresiones de CHAT A.

## CHAT B - Organizacion de conversaciones

- [x] Busqueda escalable de conversaciones con debounce y normalizacion compartida.
- [x] Buscador local en ForwardSheet con la misma normalizacion.
- [x] Estado por usuario para fijar/desfijar conversaciones.
- [x] Estado por usuario para archivar/desarchivar y reapertura por mensaje nuevo.
- [x] Ejecutar compilacion/tests y revision funcional de CHAT B.

## CHAT C - Busqueda y lectura real

- [x] Indice incremental compatible con mensajes existentes para busqueda textual.
- [x] UI de busqueda dentro de conversacion reutilizando navegacion por ID.
- [x] Cursor exacto del primer no leido y marcador en la conversacion.
- [x] Cursores eficientes SENT/DELIVERED/READ sin mapas crecientes por mensaje.
- [x] Ejecutar compilacion/tests y revision funcional de CHAT C.

## CHAT D - Audio y busqueda global

- [x] Grabacion de nota de voz con permiso contextual y archivos temporales seguros.
- [x] Enviar la grabacion por el pipeline existente de adjuntos/audio/cache.
- [x] Busqueda global tipada reutilizando el mismo indice de CHAT C.
- [x] Ejecutar compilacion/tests y revision final de regresion.

## Restricciones verificables

- [x] Un solo AudioPlayerController, ExoPlayer y MediaSession globales.
- [x] Sin Room ni WorkManager.
- [x] Sin listeners permanentes por fila.
- [x] Sin descargar colecciones completas para buscar.
- [x] Sin cambios fuera de Chat y su infraestructura estrictamente necesaria.
