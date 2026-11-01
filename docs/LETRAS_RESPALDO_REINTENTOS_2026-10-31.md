# Letras: proveedores de respaldo y control de solicitudes

## Objetivo

Resolver la espera estática de LRCLIB y permitir letras desde LRCLIB, KuGou y BetterLyrics sin lanzar consultas indiscriminadas.

## Plan

1. Separar proveedores, conservar la caché existente y registrar la fuente real de cada letra. Probar las rutas de KuGou y BetterLyrics de forma acotada.
2. Consultar proveedores por turnos y terminar con la primera letra válida. Saltar proveedores en enfriamiento, aplicar presupuesto por minuto/tiempo y respetar Retry-After.
3. Compartir la consulta en curso de una misma canción. Cancelar la llamada HTTP cuando ya no haya observadores; evitar que el botón de reintento fuerce peticiones duplicadas o descarte letras cacheadas.
4. Mostrar cuenta regresiva real y hacer como máximo dos reintentos automáticos por consulta visible. Mantener lectura, animación y cambio de pista.
5. Añadir pruebas de respaldo, límites, deduplicación/cancelación, cuenta regresiva y parsers. Compilar y validar Android.

## Estado

- [x] Revisión de fuentes y plan.
- [x] Proveedores y parsers.
- [x] Control de solicitudes, caché y diagnóstico.
- [x] Espera y reintentos en el reproductor.
- [x] Pruebas y APK.

## Implementación

- Orden: LRCLIB → KuGou → BetterLyrics. Una letra válida, incluso sin sincronización, termina la búsqueda; no se consulta a todos para obtener el mismo resultado. KuGou valida título, artista y duración antes de descargar el LRC; BetterLyrics recibe esos metadatos y convierte TTML a líneas sincronizadas. No requiere claves configuradas por el usuario ni dependencias de producción adicionales.
- Cada proveedor tiene su propio control: llamadas secuenciales, separación mínima de 500 ms y hasta 20 solicitudes HTTP por ventana móvil de un minuto. KuGou necesita normalmente tres solicitudes; el respaldo directo permite un máximo de cuatro. Cada proveedor tiene un presupuesto de 6,5 segundos; cada llamada HTTP, 6 segundos. Se respeta `Retry-After` en 429/503 y se añade espera progresiva con una pequeña variación aleatoria.
- Las consultas simultáneas de una canción comparten el trabajo. Cancelar un observador conserva el trabajo de los demás; salir del último cancela la llamada de OkHttp. Ocultar letras, salir de la pantalla o cambiar canción cancela la observación.
- La cuenta regresiva usa la fecha real de disponibilidad, deshabilita el botón durante la espera y realiza como máximo dos reintentos automáticos por observación visible. Después se habilita el reintento manual. Los errores recientes se reutilizan durante al menos cinco segundos para evitar peticiones repetidas.
- La caché conserva las letras anteriores y registra el proveedor. Los negativos antiguos de LRCLIB no impiden probar los respaldos. No se guarda un fallo temporal como «sin letra»; las ausencias comprobadas de cada proveedor se recuerdan 15 minutos para no repetirlas durante los reintentos.
- Diagnóstico `MusicLyrics`: fuente, código de respuesta, duración y llamadas en el último minuto. No registra letras, título, URL ni claves de acceso de KuGou. Las respuestas están limitadas a 1 MiB y el parser TTML rechaza declaraciones externas.

## Validación final

- Compilación limpia y validación completas: `:app:clean :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest`, restringiendo instrumentación a `MusicLyricsDeviceTest`.
- Pruebas unitarias: **88 casos, 87 aprobados, 1 omitido y 0 fallos**. Se activaron `TASKLY_LYRICS_LIVE_TEST=1` y `TASKLY_LYRICS_BACKUP_LIVE_TEST=1`; el omitido es el smoke test optativo de YouTube de la integración anterior.
- Red real, consulta acotada por proveedor: KuGou respondió con tres HTTP 200 y 75 líneas sincronizadas; BetterLyrics, un HTTP 200 y 46 líneas sincronizadas. LRCLIB entregó texto válido sin sincronización y se comprobó reapertura desde disco sin red. No se imprimió el contenido de las letras.
- Pruebas controladas: respaldo tras 503, enfriamiento independiente, retorno inmediato del texto simple, persistencia del proveedor, diez observadores compartiendo una consulta, cancelación individual sin perjudicar a los demás, cancelación efectiva de OkHttp al salir el último, cuota de 20 solicitudes por minuto, reintentos rápidos sin tráfico adicional y rechazo de entidades XML externas.
- Android 15/API 35, emulador `codex_taskly_music_api35`: **1 prueba aprobada** en 28,9 segundos. Conserva la alternancia animación/letras, seguimiento y cambio de pista; comprueba cuenta regresiva decreciente, botón deshabilitado, recuperación automática después de un 503 y límite de tres intentos totales ante fallo persistente. Usa audio local y letras ficticias, sin escribir en Firestore de producción.
- `git diff --check`: correcto. APK generado en `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `595AA3E6961A39440634E9D4B79875459A25B8E79E320BF9A5A3A14852A32FBB`.
- Durante la validación se detectó y corrigió una referencia de observador que podía cancelar una consulta todavía compartida; la prueba concurrente pasó en la ejecución final. Un artefacto DEX vacío de una compilación interrumpida se resolvió mediante la tarea estándar `:app:clean`.

## Límites

La corrección posterior del caso «Necio» se describe a continuación. Los resultados y el hash de la sección de validación anterior corresponden al APK previo a esa corrección.

- Los servicios externos pueden cambiar o estar temporalmente inaccesibles. Tener más proveedores aumenta cobertura, pero no garantiza letras para todas las versiones.
- Se mantiene sincronización por línea y texto simple cuando está disponible. No se añade sincronización por palabra ni traducción.
- Las validaciones de Android se realizan en emulador API 35; no equivalen a una prueba en teléfono físico ni a una release minificada.

## Corrección de colaboraciones y errores: «Necio»

- Caso reproducido: Taskly busca `Necio (feat. Carlos Santana)`; KuGou devuelve `Necio`, artista `Romeo Santos、Carlos Santana`, duración 264 segundos. El filtro anterior descartaba el registro antes de solicitar sus letras.
- Se reconocen créditos `feat.`, `ft.`, `featuring` y `con`, incluidos los separados en el campo de artistas. Se conserva el artista principal, se rechazan colaboradores conocidos diferentes y se mantienen etiquetas de versión como Live, Remix y Acoustic. Se conserva la tolerancia de duración de ±2 segundos.
- LRCLIB y BetterLyrics reciben el título base y el artista principal. KuGou conserva la búsqueda de catálogo con los créditos originales y añade una búsqueda directa de letras si no obtiene un candidato válido por catálogo/hash. El respaldo exige título, artista y duración compatibles; no descarga candidatos sin metadatos verificables ni recorre todos los resultados.
- La clave de caché normaliza los créditos para reutilizar una letra entre `feat.` y `con` y entre distintos separadores de artistas. Se migran las letras positivas de la caché anterior y se ignoran los negativos anteriores para permitir la búsqueda corregida.
- Los errores distinguen proveedor ocupado, límite de consultas, espera agotada, conexión fallida, rechazo HTTP y respuesta inválida. Un rechazo 401/403 no bloquea globalmente las siguientes canciones ni activa reintentos automáticos. Se conserva la espera mínima para consultas repetidas.
- El parser TTML también admite segundos decimales sin sufijo, como `17.393`, formato observado en BetterLyrics; así se conservan las líneas del primer minuto.
- Prueba real con el repositorio de producción, habilitando `TASKLY_LYRICS_NECIO_LIVE_TEST=1`: **tres HTTP 200 de KuGou, 68 líneas sincronizadas**. La variante `Necio (con Carlos Santana)` con artistas separados por `、` se reabrió con un cliente que prohíbe toda conexión, comprobando reutilización desde disco. No se imprime contenido de letras ni claves de acceso.
- Suite final: **98 casos, 94 aprobados, 4 omitidos, 0 fallos**. Los cuatro omitidos son pruebas optativas de red; la específica de «Necio» se ejecutó correctamente en la comprobación real anterior. Incluye regresiones de colaboración, versiones diferentes, caché antigua, respaldo por palabra clave, candidatos incompletos, rechazo HTTP sin bloquear otra canción y motivo de timeout conservado durante el enfriamiento.
- `:app:assembleDebug` y `MusicLyricsDeviceTest` en Android 15/API 35: correctos. La prueba de interfaz también comprueba el mensaje de 401, ausencia de reintento automático y una sola solicitud durante la espera, además de las interacciones existentes de letras, portada y controles.
