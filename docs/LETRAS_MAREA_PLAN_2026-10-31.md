# Letras en Marea — 2026-10-31

> La ampliación posterior con KuGou, BetterLyrics, nuevos límites y cuenta regresiva se documenta en [Letras: respaldo y reintentos](LETRAS_RESPALDO_REINTENTOS_2026-10-31.md). Los tiempos y resultados siguientes corresponden a la primera integración de LRCLIB.

## Resultado esperado

Icono de letras junto al título de la canción. Al activarlo, las letras sustituyen la animación central (que deja de ejecutarse); título, progreso y controles siguen disponibles. El mismo icono vuelve al efecto elegido. El modo se conserva al cambiar de canción y se carga la letra correspondiente.

## Plan

1. Integrar LRCLIB con el cliente HTTP existente, identificando Taskly y respetando `Retry-After`. Buscar por título, artista y duración, sin claves ni SDK adicional.
2. Añadir parseo LRC, selección prudente de versión y caché local acotada. Usar letras sincronizadas cuando existan, texto simple como alternativa, y distinguir instrumental, no encontrado y error de conexión. No inventar letras ni elegir versiones de duración incompatible.
3. Añadir estado de letras al reproductor y el botón junto al título. Mostrar línea activa y desplazamiento automático, conservar lectura manual con opción de volver a seguir la canción, y responder a pausas/saltos/cambios de pista sin resultados tardíos de canciones anteriores.
4. Verificar parser, coincidencias, caché y respuestas de API con pruebas; compilar APK y comprobar interacción en Android. Documentar resultados y límites.

## Alcance

- Las letras se consultan al abrir su modo, no durante toda búsqueda musical.
- La caché persistida permite volver a leer letras ya consultadas sin red.
- Una coincidencia de letra no está garantizada para todos los temas, remixes o grabaciones en vivo.
- No modificar las funciones existentes de búsqueda, descarga, cola, colores ni el servicio de audio.

## Estado

- [x] Plan creado y documentación oficial revisada.
- [x] Modelo, matching y parser.
- [x] Cliente LRCLIB y caché.
- [x] Vista y alternancia con la animación.
- [x] Pruebas, APK y resultados.

## Implementación

- `SongLyrics.kt`: normalización de metadatos, comparación de título/artista y duración (±2 segundos cuando se conoce), rechazo de versiones ambiguas sin artista, parser LRC y búsqueda binaria de línea activa. Conserva las etiquetas de versión (live/remix/acoustic) en el título. Un registro sincronizado incompleto no desplaza el texto completo.
- `LyricsRepository.kt`: API HTTPS de LRCLIB sin clave, `User-Agent` de Taskly con enlace al proyecto, peticiones secuenciales y separación de 350 ms. Respeta `Retry-After` en 429/503. Limita respuestas a 1 MiB y cada petición a 12 segundos. Primero consulta `/api/get`; recurre a `/api/search` si falta una coincidencia o sincronización válida.
- Caché en `filesDir/lyrics_lrclib_v1`, hasta 128 registros, con reemplazo atómico. Las letras encontradas se reutilizan sin red; los resultados negativos caducan en una hora; los errores no se guardan como ausencias. Una consulta manual fallida conserva la letra ya disponible en caché.
- `MusicLyricsViewModel.kt`: modo restaurable mediante SavedStateHandle, consultas solo con el modo abierto, cancelación al ocultar/cambiar pista y descarte de resultados tardíos mediante `collectLatest`.
- `MusicLyricsPane.kt` y `MusicPlayerScreen.kt`: botón de 48 dp junto al título, letras en la zona central y controles conservados. La rama de la animación sale de la composición al abrir las letras, por lo que se detienen su reloj y sus efectos/sensores. Línea activa, desplazamiento automático, lectura manual y botón «Seguir canción». El color de la línea activa se ajusta para conservar contraste con fondos claros/oscuros incluso con paletas muy brillantes. También muestra texto sin sincronizar, instrumental, ausencia y error/reintento.
- Sin cambios al servicio de audio, descargas, playlists, favoritos ni esquemas de Firestore. No se incorpora código de Metrolist ni un SDK adicional de letras. La dependencia nueva es exclusivamente de pruebas de interfaz Compose.

## Validación

- `:app:assembleDebug`: correcto. APK: `app/build/outputs/apk/debug/app-debug.apk`.
- `:app:testDebugUnitTest`: 79 casos, 78 ejecutados correctamente y 1 omitido. El omitido es el smoke test optativo de red de YouTube de la integración anterior; no se activó su variable. Se habilitó `TASKLY_LYRICS_LIVE_TEST=1`.
- Parser/matching: fracciones de tiempo, marcas repetidas, líneas simultáneas, pausas instrumentales, saltos hacia delante/atrás, normalización, versiones incompatibles, ambigüedad de artista y sincronización incompleta.
- Repositorio: identificación del cliente, fallback, persistencia/reapertura sin red, recuperación de archivo corrupto, caché negativa y caducidad, errores no cacheados y cumplimiento de `Retry-After` sin solicitudes adicionales.
- Consulta real LRCLIB: “Never Gonna Give You Up”, Rick Astley, 213 s. La primera coincidencia tenía sincronización incompleta; la búsqueda alternativa encontró **59 líneas sincronizadas**. Se verificó reapertura desde disco con un cliente que rechaza toda conexión. No se imprimió el contenido de las letras.
- `MusicLyricsDeviceTest`, emulador Android 15/API 35: correcto. Audio PCM local y letras ficticias para comprobar botón, eliminación de la animación, modo de letras, desplazamiento manual/reanudación, selección al saltar a 11 s, cambio a la siguiente pista conservando el modo y regreso a la animación. No se escribieron datos en Firestore de producción.
- Revisión visual de la captura nativa: `app/build/reports/lyrics/lyrics-ui-test.png`. El texto de la captura es ficticio; muestra el diseño del modo sincronizado y el icono al lado del título.
- `git diff --check`: correcto.

## Límites

- La cobertura y exactitud dependen de las contribuciones y disponibilidad de LRCLIB; no se garantiza letra para cada versión ni sincronización perfecta. Si no existe sincronización válida, se usa texto simple cuando está disponible.
- Se sincroniza por línea, no por palabra. No se añade traducción ni generación de letras.
- El emulador valida Android 15. No se comprobó todavía un teléfono físico, Android 8–14 ni una release minificada.
- Documentación del proveedor consultada: https://lrclib.net/docs.md.
