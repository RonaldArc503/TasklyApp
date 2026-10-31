# Buscador y streaming en Música — 2026-10-31

## Resultado esperado

Buscar canciones de YouTube desde Música, escucharlas en Marea sin esperar una descarga completa, agregarlas a la cola y guardarlas en favoritos/listas o descargarlas para escuchar sin conexión. Conservar los audios, listas, sesión y cambios locales existentes.

## Plan de ejecución

1. **Validar proveedor y compatibilidad.** Probar búsqueda y resolución de una canción con un extractor mantenido y una petición real de audio. Mantener la toolchain actual si es posible. No usar el servidor de conversión para simular reproducción instantánea.
2. **Identidad y resolución.** Guardar una referencia estable de YouTube, resolver las URLs temporales al abrir el medio y conservar por separado cabeceras/caducidad. Reutilizar el servicio Media3 para cola, segundo plano y restauración.
3. **Buscador.** Incorporar una sección de descubrimiento dentro de Música con búsqueda, portada, artista, reproducción y acciones de cola. Mostrar carga, resultados vacíos y errores recuperables.
4. **Guardado.** Reutilizar favoritos y playlists de Firestore con referencias estables. Integrar descarga explícita con el catálogo local; una canción guardada no debe figurar como descargada.
5. **Caché y recuperación.** Usar una caché de reproducción acotada, resolver de nuevo URLs rechazadas/caducadas y mantener los archivos descargados fuera de la expulsión automática de caché.
6. **Validación.** Pruebas de identidad/serialización, resolución, errores y selección de stream; compilación debug y prueba real de búsqueda/audio. Validación Android si hay dispositivo o emulador disponible. Documentar resultados y límites comprobados.

## Decisiones y límites

- La reproducción existente de audios del chat y las descargas del conversor deben seguir funcionando.
- El módulo actual usa Kotlin 2.0.21 y Media3 1.5.1; copiar la toolchain de Metrolist no es un requisito.
- Evaluar NewPipe Extractor (Java) como alternativa compatible antes de incorporar InnerTubeX, que en la versión analizada requiere Kotlin más reciente.
- Los extractores de YouTube están sujetos a cambios del servicio. Metrolist/InnerTubeX y NewPipe Extractor tienen licencias GPL; conservar avisos y documentar las obligaciones antes de distribuir una versión que los incorpore.
- Sin despliegues, commits ni modificaciones al servidor externo como parte de esta implementación local.

## Estado

- [x] Plan creado y revisión del código actual.
- [x] Proveedor verificado.
- [x] Resolución integrada en Media3.
- [x] Buscador y acciones.
- [x] Favoritos, playlists y descargas implementados.
- [x] Caché, pruebas y compilación.

## Implementación final

- Nueva pestaña **Descubrir**; la lupa de Inicio abre esa sección. Búsqueda explícita por canción/artista, paginación, portada, artista, duración, estados de carga, mensajes de error y reintento. Las búsquedas canceladas no sustituyen resultados posteriores.
- NewPipe Extractor **v0.26.5**, mediante JitPack restringido al grupo de la dependencia. Se mantiene Kotlin 2.0.21 / Media3 1.5.1 / Java 11 del proyecto. Se activa desugaring NIO 2.1.5 para las APIs Java que usa el extractor.
- Audio progresivo M4A, representación **itag 140**. Mantener una representación fija evita mezclar rangos de distintos formatos/calidades cuando se renueva un enlace. Si YouTube no ofrece ese formato, se muestra un error; no se convierte a MP3 ni se usa el backend del conversor.
- Referencia persistida: `https://www.youtube.com/watch?v={videoId}`; ID: `youtube:{videoId}`. URLs de CDN, cabeceras y caducidad permanecen en memoria, con hasta 64 entradas y margen de renovación de 60 segundos.
- El origen de red resuelve al abrir y reintenta una vez con extracción nueva ante HTTP 403/410/416. Comunica la URI pública a Media3 para impedir que la caché persista la URL firmada como redirección. Reintentar desde Marea conserva la posición actual.
- Caché de reproducción de **256 MiB** en `cacheDir/music_streams`, con expulsión LRU. Los archivos locales pasan directamente por la fuente local de Media3. Las descargas explícitas conservan el catálogo y almacenamiento existentes en `filesDir/Taskly`, separados de la caché LRU.
- Favoritos/listas reutilizan las operaciones de Firestore existentes. Se añade `artist` como campo opcional; sesión, historial, biblioteca, descarga y metadatos del reproductor lo conservan. Los registros anteriores siguen siendo compatibles. Una favorita en línea no se marca como descargada.
- El conflicto entre Protobuf 4 del extractor y `protolite-well-known-types` 18 se resuelve usando **protolite-well-known-types 19.0.0**, que no duplica DescriptorProtos. No se excluyen los tipos de Firebase ni se cambia todo el BOM. Se verifica serialización de Document, Timestamp y LatLng.
- Avisos y GPL del extractor incluidos en `app/src/main/assets/licenses/`; reglas R8 preparadas en ProGuard. No se incorpora código de Metrolist.

## Validación realizada

1. `:app:assembleDebug`: correcto. APK: `app/build/outputs/apk/debug/app-debug.apk`.
2. `:app:testDebugUnitTest`: **64 pruebas, 0 fallos, 0 omitidas**, habilitando la prueba de red con `TASKLY_MUSIC_LIVE_TEST=1`.
3. Prueba real JVM: 20 resultados iniciales, 20 en la siguiente página, respuesta HTTP 206 `audio/mp4` y descarga íntegra de **3 322 543 bytes**. Canción utilizada: Kevin MacLeod, “Carefree”, ID `qapK8Gx6ZTo`.
4. `MusicStreamingDeviceTest`, emulador Android 15/API 35: **2 pruebas, 0 fallos**. El servicio real reproduce y avanza, continúa con la actividad detenida, permite saltar a 45 segundos y decodifica el archivo descargado. La otra prueba guarda un rango de 65 536 bytes, verifica que no persista una redirección firmada y lo lee íntegro con un origen de red que falla si se invoca.
5. `git diff --check`: correcto (configuración de CRLF del repositorio).

Para las pruebas instrumentadas con red se requiere `-Pandroid.testInstrumentationRunnerArguments.musicLive=1`; sin ese argumento se omiten. La prueba prepara los permisos de arranque de la aplicación de prueba antes de lanzar MainActivity. En Windows se usó Android Studio JBR, `-Djava.net.preferIPv4Stack=true` y `-Djdk.net.unixdomain.tmpdir=C:/Windows/Temp` para evitar el fallo local de loopback de Gradle.

Se corrigió la llamada previa de `ReminderRepeatTest` para pasar `after = ignoredAtNoon` por nombre, en lugar de entregar un Long al argumento Int `anchorDay`. No se modificó la lógica de recordatorios.

## Límites y comprobaciones manuales pendientes

- Falta revisión visual/táctil del buscador y comprobación de favoritos/listas con una cuenta real. Las pruebas no escriben datos en Firebase de producción.
- La disponibilidad de canciones, regiones y formatos depende de YouTube. No se garantiza acceso a contenido restringido ni que el extractor permanezca compatible tras cambios del servicio.
- Las descargas utilizan el gestor interno actual de Taskly; no se añade persistencia/reanudación de trabajos después de que Android mate el proceso.
- No se validó una release minificada ni se hizo una auditoría global de lint. La validación corresponde al APK debug y los escenarios descritos.
- Antes de distribuir la app con NewPipe, resolver las obligaciones GPL de la aplicación combinada y las condiciones aplicables al proveedor. Los avisos incluidos no sustituyen la entrega de código fuente cuando corresponda.
