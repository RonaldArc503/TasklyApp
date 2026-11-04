# Favoritos del reproductor y metadatos de radio

## Alcance aplicado

- El doble toque en la animación guarda o quita el favorito mediante `IMusicRepository.setFavorite`, igual que el buscador. El icono junto al título refleja la colección de favoritos; permite la misma acción cuando se muestran letras. La animación del corazón y el mensaje aparecen al confirmar el guardado o eliminación. Los errores y la falta de sesión se muestran sin confirmar un guardado ficticio.
- La cuenta se observa de forma reactiva; se evita repetir una escritura mientras hay otra en curso y se cancela la observación anterior al cambiar de usuario. Se captura la canción al pulsar para no guardar otra si la reproducción avanza durante la operación.
- La radio consulta la cola musical WEB_REMIX/RDAMVM. Solo las entradas con navegación `MUSIC_PAGE_TYPE_ARTIST` identifican artistas. Si no existe esa identificación, el artista queda vacío y la procedencia se conserva en `sourceChannel`; el reproductor muestra `Canal: …`. No se adivina el cantante a partir del nombre del canal ni del título del vídeo.
- El canal de origen se conserva en la sesión, historial, favoritos, listas y catálogo de descargas, separado del artista y con valores vacíos compatibles con registros antiguos.
- Los estados de búsqueda de similares, ausencia de resultados, timeout y fallo se comunican desde el servicio al reproductor. Se conserva el comportamiento de las playlists cerradas y la ausencia de reintentos automáticos infinitos. Un subtítulo de estado acompaña los extras porque Media3 1.5.1 no compara sus valores al decidir si hay cambios de metadatos.
- No se añaden dependencias de producción ni se despliegan cambios de reglas de Firestore. No se incorporan las demás mejoras del análisis.

## Validación

- `:app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest`: correctos en la ejecución final, con instrumentación restringida a `MusicLyricsDeviceTest` y `RelatedPlaybackQueueDeviceTest`, y `musicRadioLive=1`.
- Suite final: 104 casos unitarios, 99 aprobados, 5 optativos de red omitidos y cero fallos. La consulta JVM real de radio se ejecutó previamente con `TASKLY_MUSIC_RADIO_LIVE_TEST=1`: 10 resultados; ejemplos con artistas identificados: Hilito/Romeo Santos, Dile al amor/Aventura y Corazon Sin Cara/Prince Royce.
- Android 15/API 35: dos pruebas aprobadas en 59,2 segundos. Se comprobaron doble toque, guardar/quitar, icono persistente, confirmación, fallo de escritura, falta de sesión, canal sin artista y conservación de ambos campos al guardar. El backend de favoritos de estas pruebas es un repositorio en memoria; no escribe en Firestore de producción.
- El control de radio comprueba indicadores de carga, comunicación de fallos, limpieza al entrar en una playlist y descarte de respuestas antiguas. La integración de servicio consulta recomendaciones reales con la actividad detenida: cinco canciones añadidas y reproducción activa.
- Las pruebas anteriores de portada, letras, reintentos y transición automática también pasan. `git diff --check`: correcto.
- APK: `app/build/outputs/apk/debug/app-debug.apk`; SHA-256 `90360E4512FC7F00F06CAC0F0058251743EBA385CA188D28E7813815AAFB2222`.

## Límites

- La identificación del artista depende de los datos que publique el catálogo; se muestra el canal cuando no hay identificación musical verificable. No se reescriben automáticamente favoritos antiguos con metadatos ambiguos.
- Sin prueba en teléfono físico ni escritura con una cuenta real en esta validación. Las recomendaciones sí se verificaron con red real.
- Referencia de igualdad de metadatos: https://github.com/androidx/media/blob/1.5.1/libraries/common/src/main/java/androidx/media3/common/MediaMetadata.java.
