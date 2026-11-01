# Canción activa y continuación con música similar

## Alcance

1. Actualizar título, artista, portada, duración y letra cuando Media3 avance automáticamente, aunque el catálogo de la pantalla haya cambiado.
2. Reproducir recomendaciones de la canción elegida fuera de playlists guardadas. Dentro de una playlist, conservar únicamente su cola explícita y sus controles de repetición.

## Implementación

- La cola y el elemento activo de Media3 son la fuente de los datos mostrados. Se sincronizan al recibir cambios de cola, canción o metadatos; el reloj de progreso también verifica el elemento activo. Ya no se conserva la canción anterior cuando falta la nueva en la copia del catálogo.
- La selección se captura antes de conectar con MediaSession para que una actualización de la sesión no sustituya la cola elegida antes de cargarla. Los metadatos propios pueden borrar una portada o un identificador de playlist anterior; un valor vacío no recupera datos obsoletos de otra selección.
- Los MediaItem conservan la referencia original, duración, artista, portada, identificadores y permiso de continuación. Ese permiso se guarda en la sesión; las sesiones antiguas lo restauran desactivado.
- Una selección online desde buscador, biblioteca o historial, sin identificador de playlist guardada, inicia una cola con la canción elegida. Los otros resultados de búsqueda no se convierten automáticamente en siguientes canciones. Los archivos locales y las vistas previas mantienen sus colas finitas.
- Metrolist utiliza una cola de radio de YouTube basada en el identificador de la canción (`RDAMVM`), con continuación. Se adopta ese mecanismo mediante el extractor NewPipe ya instalado, sin copiar clases de Metrolist ni incorporar dependencias de producción.
- `RelatedPlaybackQueue` pertenece al servicio de audio, continúa con la pantalla cerrada y prepara hasta cinco canciones cuando queda como máximo una después de la actual. No solicita la siguiente tanda en repetición de una canción, mientras está pausado ni dentro de una playlist guardada.
- Solo existe una extracción pendiente. Se descartan respuestas de colas sustituidas o editadas; se eliminan duplicados y la canción semilla. Los errores no crean bucles de reintento. Si la petición termina después de finalizar la canción, la reproducción continúa siempre que el usuario no la haya pausado o cambiado de contexto.
- Se conservan entre 20 y 50 elementos anteriores al recortar el historial para limitar el crecimiento de la cola. La cola generada no modifica las playlists guardadas.

## Validación

- Pruebas de selección de cola y persistencia del permiso de radio, incluidas sesiones antiguas: correctas.
- Consulta real de radio de «Necio» con `TASKLY_MUSIC_RADIO_LIVE_TEST=1`, sin descargar audio: 10 canciones con nombres y portadas válidos; resultados iniciales de Romeo Santos y Aventura. En esa ejecución se aprobaron 97 pruebas unitarias de 101 casos, con 4 pruebas optativas omitidas.
- Android API 35, `MusicLyricsDeviceTest`: transición automática actualizando título/portada/letra tras vaciar deliberadamente la copia del catálogo. Se conservan las interacciones de letras, portada y controles existentes.
- Android API 35, `RelatedPlaybackQueueDeviceTest`: radio con audio PCM local y recomendaciones controladas, continuación después de finalizar la semilla mientras carga la respuesta, eliminación de duplicados, playlist finita sin consultas, resultados tardíos descartados y fallo de red sin reintentos infinitos.
- Integración real con `-Pandroid.testInstrumentationRunnerArguments.musicRadioLive=1`: la actividad pasa a estado CREATED y la canción continúa sonando; el servicio añade cinco recomendaciones reales, la cola llega al controlador y no aparecen identificadores de playlist guardada. El audio de la semilla es PCM local; la consulta de recomendaciones utiliza la red.
- Ejecución final sobre el APK corregido: `:app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest`, con instrumentación restringida a las dos clases anteriores y `musicRadioLive=1`: correcta. **96 pruebas unitarias aprobadas de 101 casos, 5 optativas de red omitidas; 2 pruebas Android aprobadas en 54,4 segundos**. La consulta de radio del servicio sí usa la red en esta ejecución; la optativa de radio JVM se había aprobado en la verificación anterior.
- `git diff --check`: correcto. APK: `app/build/outputs/apk/debug/app-debug.apk`; SHA-256 `206B1EFFC585580CAA71E08DC9BA6A1A947B401679216E5BF8EFA3B66CD0E506`.

## Referencias

- Metrolist local: `C:/Users/HP VICTUS/Desktop/Metrolist-main/app/src/main/kotlin/com/metrolist/music/playback/queues/YouTubeQueue.kt`.
- [Eventos de Media3](https://developer.android.com/media/media3/exoplayer/listening-to-player-events).
- [Extractor de mezclas de NewPipe](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeMixPlaylistExtractor.java).

## Límites

- La disponibilidad y selección de similares dependen de YouTube y de la conectividad. No se generan recomendaciones locales cuando falla la consulta.
- Las playlists guardadas conservan su propia cola, incluso si no hay recomendaciones o conexión.
- La validación en emulador no sustituye una prueba en teléfono físico ni una release minificada.
