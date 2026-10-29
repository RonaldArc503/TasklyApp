# Auditoría de Taskly — 18 de octubre de 2026

## Alcance y método

Revisión estática del árbol Android/Kotlin/Compose, configuración Gradle y manifest, reglas locales de Firestore, repositorios, receptores, navegación y pruebas visibles. Se comparó con `AUDITORIA_PROFUNDA_TASKLY_2026-10-16.md`, pero cada hallazgo incluido aquí se contrastó con el código actual. No se modificó código de producto ni se desplegaron reglas. **El árbol está en desarrollo:** `git status --short` mostró 38 archivos modificados y 10 entradas nuevas sin seguimiento; `git diff --shortstat` mostró 4,433 inserciones y 891 eliminaciones en archivos ya versionados. Los archivos nuevos no están incluidos en esa estadística.

**Estado de prueba:** se intentó `:app:compileDebugKotlin :app:testDebugUnitTest --offline --console=plain`; no emitió salida durante la espera y se interrumpió. Compilación y tests del árbol actual quedan **sin verificar**. Tampoco se inspeccionaron dispositivo, Firebase desplegado, Cloudinary ni backend real. Los hallazgos de código son confirmados en este checkout; el impacto en producción depende de lo desplegado y de los flujos reales.

## Resumen ejecutivo

La prioridad inmediata es cerrar el tráfico HTTP del conversor y la exposición de datos personales/reglas de borrado. El cambio reciente de alarmas agrega un riesgo propio: una agenda local compartida entre cuentas puede seguir mostrando títulos después de cerrar sesión y puede reconciliarse mal cuando Firestore emite una lista vacía. En descargas, el registro pendiente se consume antes de confirmar el resultado y su escritura final se deja en una corrutina sin ciclo de vida durable. Chat y música concentran miles de líneas y varias responsabilidades. Hay duplicación real de la lógica de completar/posponer recordatorios en tres superficies.

| Prioridad | Hallazgo | Estado |
|---|---|---|
| P0 | Conversor propio por HTTP/IP fija y cleartext habilitado globalmente | Confirmado en código |
| P1 | Lectura de perfiles e índice telefónico para cualquier usuario autenticado | Confirmado en reglas locales |
| P1 | Un participante puede borrar el documento raíz del chat | Confirmado en reglas locales |
| P1 | Agenda local de recordatorios sin separación por cuenta ni limpieza al salir | Confirmado en código; efecto por cambio de cuenta requiere dispositivo |
| P1 | Reglas y operaciones críticas sin pruebas específicas; release sin R8 | Confirmado en configuración/árbol |
| P2 | Descarga: seguimiento consumido temprano y persistencia posterior al broadcast no durable | Confirmado en código |
| P2 | Completar/posponer recordatorios replicado tres veces | Confirmado en código |
| P2 | Archivos grandes de música/chat, consultas N+1 y lotes sin partición | Confirmado en código |

## Hallazgos detallados

### P0-01 — Conversor sin TLS

**Evidencia:** [`ConverterViewModel.kt`](app/src/main/java/com/ronaldcolocho/taskly/ui/screen/converter/ConverterViewModel.kt) líneas 342, 359, 373-374 y 690 usa `http://` con una IP fija; [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) línea 26 habilita `usesCleartextTraffic` para toda la app. El ViewModel acepta `download_url` absoluto con `http://` o `https://` sin lista de hosts permitidos (líneas 345-374).

**Efecto:** solicitudes, respuestas y archivos pueden observarse o alterarse en tránsito; cambiar la IP exige publicar una app nueva. **Acción:** servir el API y las descargas por HTTPS y dominio estable; definir endpoint por variante de build; rechazar URL de descarga fuera del esquema/host esperado; deshabilitar cleartext en release. Validar el backend real antes de cambiar el cliente.

### P1-01 — Datos personales expuestos por las reglas

**Evidencia:** [`firestore.rules`](firestore.rules) líneas 114-125 permite `read` de `/profiles/{uid}` y `/phoneIndex/{phone}` a cualquier usuario autenticado. [`UserProfile.kt`](app/src/main/java/com/ronaldcolocho/taskly/domain/model/UserProfile.kt) contiene correo, correo normalizado y teléfono en el perfil; [`ProfileRepositoryImpl.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/repository/ProfileRepositoryImpl.kt) líneas 127-153 implementa búsqueda sobre esos datos.

**Efecto:** un cliente autenticado puede consultar información de personas fuera de sus chats. **Acción:** separar perfil público de datos privados, limitar los resultados de búsqueda y probar reglas con Firebase Emulator. Confirmar las reglas efectivamente desplegadas; este informe verifica el archivo local.

### P1-02 — Borrado de conversación raíz con subcolecciones e índices aparte

**Evidencia:** [`firestore.rules`](firestore.rules) línea 143 concede `delete` al participante sobre `/conversations/{id}`. Mensajes y recibos viven bajo el documento padre (líneas 145-168); los índices `conversationSearch` y `globalChatSearch` están en colecciones separadas (líneas 182-219). [`ChatRepositoryImpl.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/repository/ChatRepositoryImpl.kt) líneas 1099-1119 limpia índices al borrar un mensaje, pero esa limpieza no cubre necesariamente el borrado del documento raíz por otro cliente.

**Efecto:** el chat desaparece para los participantes mientras quedan datos e índices huérfanos. **Acción:** quitar el borrado global desde clientes; mantener “ocultar para mí” en un estado personal; si se requiere eliminación global, hacerla desde servidor con autorización, paginación y reconciliación.

### P1-03 — Alarmas locales mezcladas entre cuentas

**Evidencia:** [`ReminderScheduleStore.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/alarm/ReminderScheduleStore.kt) líneas 21-25 usa una única preferencia `reminder_schedule` sin UID; líneas 67-74 guarda título y fecha. [`BootReceiver.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/receiver/BootReceiver.kt) líneas 16-26 restaura toda la agenda al arrancar sin comprobar cuenta. [`ProfileRepositoryImpl.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/repository/ProfileRepositoryImpl.kt) líneas 156-158 cierra Firebase Auth sin limpiar alarmas. [`ReminderReceiver.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/receiver/ReminderReceiver.kt) líneas 117-179 muestra el título recibido en notificación y líneas 236-266 solo consulta el usuario para el saludo.

**Efecto:** tras cerrar sesión o cambiar de cuenta, el teléfono puede seguir notificando títulos de la cuenta anterior. Las acciones “Completar” y “Posponer” usan el UID activo al ejecutarse (líneas 40-41 y 102-107), por lo que pueden intentar actuar sobre un recordatorio de otra cuenta si coinciden IDs. **Acción:** guardar propietario en agenda e intents, validar UID antes de mostrar/operar, cancelar al salir y reconstruir al entrar. Probar cambio A→B, salida de sesión, reinicio y modo offline.

### P1-04 — Reconciliación local/remota de alarmas puede cancelar la agenda restaurada

**Evidencia:** [`ReminderAlarmManager.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/alarm/ReminderAlarmManager.kt) líneas 25-30 calcula IDs ausentes, cancela y reemplaza todo el espejo local. [`RemindersViewModel.kt`](app/src/main/java/com/ronaldcolocho/taskly/ui/screen/reminders/RemindersViewModel.kt) líneas 64-74 llama a ese reemplazo ante la primera emisión de Firestore, aunque sea lista vacía. [`ReminderRepositoryImpl.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/repository/ReminderRepositoryImpl.kt) líneas 25-29 usa snapshots sin distinguir caché, servidor o carga inicial. El comentario de [`BootReceiver.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/receiver/BootReceiver.kt) líneas 22-25 describe precisamente ese escenario.

**Efecto:** una lista vacía de caché o una sincronización transitoria puede borrar alarmas locales válidas antes de que llegue el servidor. **Acción:** distinguir estado de fuente/confirmación, asociar agenda al UID y reconciliar solo con snapshot autoritativo; registrar revisiones para evitar emisiones viejas. Requiere prueba offline y de reinicio para confirmar frecuencia real.

### P1-05 — Binario release y credenciales de cliente

**Evidencia:** [`app/build.gradle.kts`](app/build.gradle.kts) líneas 24-30 empaqueta `YOUTUBE_API_KEY` en `BuildConfig`; línea 37 deja `isMinifyEnabled = false`. [`CloudinaryRepositoryImpl.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/repository/CloudinaryRepositoryImpl.kt) líneas 40-50 contiene datos de subida directa y preset en el cliente.

**Efecto:** los valores distribuidos en APK son extraíbles; el flujo de Cloudinary depende de las restricciones reales del preset. **Acción:** restringir API key por app/API/cuota, probar R8 antes de release y verificar en Cloudinary tamaños, tipos, tasa y autorización del preset. El abuso del preset **no está confirmado** sin su configuración externa.

### P2-01 — Descargas con pérdida de estado y trabajo no durable

**Evidencia:** [`DownloadCompleteReceiver.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/receiver/DownloadCompleteReceiver.kt) línea 30 llama `getAndRemove` antes de consultar `DownloadManager` (líneas 32-39). Si consulta, ruta, copia o metadatos fallan, ya no queda seguimiento para reintentar. En líneas 82-84 crea `CoroutineScope(Dispatchers.IO).launch` después del broadcast sin `goAsync()` ni trabajo persistente. [`DownloadTracker.kt`](app/src/main/java/com/ronaldcolocho/taskly/util/DownloadTracker.kt) líneas 27-31 elimina el dato inmediatamente y líneas 41/49 silencian errores de preferencias.

**Efecto:** un archivo descargado puede no aparecer en historial si el proceso termina o hay fallo transitorio. **Acción:** confirmar éxito antes de eliminar el pendiente; registrar estado persistente, usar `goAsync()` para trabajo corto o WorkManager para reconciliación; hacer la operación idempotente por ID de descarga. Probar app cerrada, proceso muerto y descarga fallida.

### P2-02 — Recordatorios duplican lógica y no cierran operaciones atómicamente

**Evidencia:** completar/posponer se implementa en [`ReminderReceiver.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/receiver/ReminderReceiver.kt) líneas 33-114, [`ReminderAlarmActivity.kt`](app/src/main/java/com/ronaldcolocho/taskly/ui/screen/reminders/ReminderAlarmActivity.kt) líneas 138-209 y [`RemindersViewModel.kt`](app/src/main/java/com/ronaldcolocho/taskly/ui/screen/reminders/RemindersViewModel.kt) líneas 191-265. Para recurrentes, los tres crean histórico y actualizan el activo en escrituras separadas; algunos `runCatching` de histórico no inspeccionan fallo (por ejemplo Activity línea 163). Receiver cancela la notificación antes de confirmar la mutación (líneas 35-38, 95-100); Activity no muestra error de red antes de cerrar.

**Efecto:** un fallo parcial puede dejar histórico y siguiente ciclo inconsistentes; una corrección debe repetirse en tres rutas. **Acción:** un único caso de uso transaccional/idempotente para completar y otro para posponer, invocados desde las tres superficies; devolver estado visible o reintento. Prueba de doble toque y fallo de red entre escrituras.

### P2-03 — Escalabilidad de lotes Firestore

**Evidencia:** [`ProfileRepositoryImpl.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/repository/ProfileRepositoryImpl.kt) líneas 173-189 actualiza todas las conversaciones en un único `WriteBatch` sin paginar; las operaciones de reclamar teléfono, borrar índice viejo y guardar perfil son separadas (líneas 103-116). [`TaskRepositoryImpl.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/repository/TaskRepositoryImpl.kt) líneas 102-107 borra todos los IDs completos en un único batch.

**Efecto:** al superar el límite de operaciones por batch, la operación falla entera. En perfil, fallos intermedios pueden desincronizar índices. **Acción:** partir lotes bajo el límite, paginar y diseñar actualización de perfil/índice recuperable. Probar con más de 500 conversaciones/tareas.

### P2-04 — Chat hace lecturas N+1 y contiene demasiadas políticas

**Evidencia:** [`ChatRepositoryImpl.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/repository/ChatRepositoryImpl.kt) líneas 930-955 consulta el índice global y después lee cada mensaje individualmente. El repositorio supera 1,100 líneas; [`ChatViewModel.kt`](app/src/main/java/com/ronaldcolocho/taskly/ui/screen/chat/ChatViewModel.kt) supera 1,100 y [`ChatScreen.kt`](app/src/main/java/com/ronaldcolocho/taskly/ui/screen/chat/ChatScreen.kt) supera 1,200 (conteo de líneas no vacías con `Measure-Object -Line`).

**Efecto:** latencia/costo de búsqueda crece con resultados; un índice desfasado genera huecos. Cambios de envío, edición, hilos, archivos y búsqueda comparten archivos enormes. **Acción:** separar repositorios de mensajes, conversaciones, recibos y búsqueda; usar índices derivados con reconciliación; dividir pantalla y ViewModel por responsabilidades, manteniendo contratos actuales.

### P2-05 — Música concentra UI/efectos y mantiene historiales locales paralelos

**Evidencia:** `MusicPlayerScreen.kt` tiene 3,786 líneas no vacías; `MusicScreens.kt` 2,057; `MareaPlaylistScreen.kt` 1,790 (medición de este checkout). [`MusicRepositoryImpl.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/repository/MusicRepositoryImpl.kt) líneas 35-68 mantiene historial de reproducciones local; [`DownloadHistoryRepositoryImpl.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/repository/DownloadHistoryRepositoryImpl.kt) líneas 27-49 mantiene otro catálogo local de descargas. Son datos distintos, pero sus identidades, disponibilidad del archivo y ciclos de vida se solapan; no hay una fuente única documentada de “canción disponible”.

**Efecto:** refactors visuales tocan archivos masivos; descargas, historial y cache pueden divergir. **Acción:** extraer renderizadores/gestos/paletas de música y definir un catálogo local con reconciliación de archivo e historial, conservando la semántica diferente de reproducción y descarga. Medir efectos en teléfono antes de cambiar política de rendimiento.

### P2-06 — Persistencia por cuenta inconsistente

**Evidencia:** [`DeletedMessagesRepositoryImpl.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/repository/DeletedMessagesRepositoryImpl.kt) líneas 22-40 guarda IDs “eliminados para mí” en una sola preferencia del dispositivo, sin UID, con expiración y tope (líneas 60-79). [`DownloadHistoryRepositoryImpl.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/repository/DownloadHistoryRepositoryImpl.kt) líneas 27-49 y 80-97 usa preferencias y silencia errores. [`ClipboardShelfViewModel.kt`](app/src/main/java/com/ronaldcolocho/taskly/ui/screen/clipboard/ClipboardShelfViewModel.kt) líneas 32-50 conserva los bloques temporales solo en memoria, mientras [`ClipboardShelfRepository.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/repository/ClipboardShelfRepository.kt) líneas 19-47 sincroniza los permanentes en Firebase.

**Efecto:** en un dispositivo compartido, un mensaje con el mismo ID puede ocultarse también a otra cuenta; algunas elecciones desaparecen al reinstalar y otras se sincronizan. **Acción:** separar datos locales por UID, limpiar estado al salir y documentar por dato si es temporal, local o sincronizado. No fusionar “Guardados” y portapapeles sin validar sus usos distintos.

### P2-07 — Errores de observación de portapapeles no llegan a UI

**Evidencia:** [`ClipboardShelfRepository.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/repository/ClipboardShelfRepository.kt) líneas 28-33 solo emite si `error == null`; si Firestore deniega o falla el listener, no envía error ni cierra el flujo. [`ClipboardShelfViewModel.kt`](app/src/main/java/com/ronaldcolocho/taskly/ui/screen/clipboard/ClipboardShelfViewModel.kt) líneas 36-49 presenta lista vacía como estado normal y captura el UID solo al construir el ViewModel (línea 32).

**Efecto:** “no hay bloques” y “falló la lectura” son indistinguibles, y un cambio de cuenta durante la vida del ViewModel requiere recreación explícita. **Acción:** estado de carga/error/reintento y observación asociada al usuario actual. Probar permiso denegado y cambio de cuenta.

### P2-08 — Archivo y enlaces de chat no coinciden con el flujo de envío

**Evidencia:** [`ChatInfoViewModel.kt`](app/src/main/java/com/ronaldcolocho/taskly/ui/screen/chatinfo/ChatInfoViewModel.kt) líneas 127-136 ignora PDF/DOC al armar el resumen; líneas 117-120 antepone `http://` a enlaces `www.`. Las líneas 139-140 contienen dos `Triple(media, lks, audio)` seguidos, código redundante.

**Efecto:** se envían documentos que luego no se encuentran en información del chat; un enlace `www.` se abre inicialmente sin HTTPS. **Acción:** incluir documentos, normalizar a HTTPS y validar esquemas; retirar la expresión duplicada.

### P3-01 — Calidad de entrega y feedback irregular

**Evidencia:** solo hay 6 archivos bajo `app/src/test` y 1 ejemplo bajo `app/src/androidTest`; no hay pruebas visibles de reglas Firestore, descargas, alarmas de sistema, Cloudinary o navegación completa. [`RemindersViewModel.kt`](app/src/main/java/com/ronaldcolocho/taskly/ui/screen/reminders/RemindersViewModel.kt) líneas 132-134 ignora error al mover fecha y líneas 255-265 ignora fallo al posponer. [`DownloadHistoryRepositoryImpl.kt`](app/src/main/java/com/ronaldcolocho/taskly/data/repository/DownloadHistoryRepositoryImpl.kt) líneas 88-97 silencia errores de persistencia. `git diff --check` marca espacios al final en `AudioPlayerController.kt` líneas 574 y 590. Archivos auxiliares `recovered*.kt`/`test.kt` y un keystore de release están en raíz, aunque ignorados por Git.

**Efecto:** acciones pueden aparentar éxito sin confirmación y el árbol no tiene una puerta de calidad verificable para cambios extensos. **Acción:** pruebas de reglas y flujos críticos, errores visibles, revisión de release y CI con formato, compilación y tests. Mantener claves/keystore fuera de cualquier commit o reporte público.

## Mapa de módulos y mantenibilidad

| Área | Estado actual | Riesgo principal | Orden de trabajo |
|---|---|---|---|
| Autenticación y perfiles | Firebase Auth + perfil e índice telefónico | Privacidad y actualización no atómica | 1 |
| Chat y búsqueda | Repositorio/ViewModel/pantalla enormes, índices derivados | Reglas, N+1, regresiones de edición/hilos | 1-2 |
| Recordatorios | Firestore + espejo local + AlarmManager + tres rutas de acción | Cuentas cruzadas, reconciliación y duplicación | 1 |
| Conversor | Servicio propio + rutas de proveedor, polling y DownloadManager | HTTP y descarga/estado frágiles | 1 |
| Música | UI/efectos masivos + historial/cache/descargas | Acoplamiento y divergencia de catálogo | 2 |
| Tareas | Repositorio sencillo, borrado en batch | Escalado del borrado masivo | 2 |
| Guardados/portapapeles | Guardados en Firebase; bloques permanentes Firebase, temporales memoria | Modelo de persistencia y errores de lectura | 2 |
| Ajustes/presencia/red | Mezcla local, Firebase y callbacks | Contrato de sincronización/errores | 3 |

## Sistemas redundantes o solapados

1. **Redundancia real:** completar y posponer recordatorios en receptor, Activity y ViewModel. Consolidar en casos de uso compartidos.
2. **Duplicación de estado con propósito válido:** Firestore y agenda local de alarmas. Mantener ambos por operación offline, pero formalizar propietario, fuente autoritativa y reconciliación.
3. **Catálogos solapados:** descargas, historial de escucha y cache musical. Mantener métricas diferentes, unificar identidad/estado de archivo y reconciliación.
4. **No son redundantes por defecto:** Guardados conserva mensajes; portapapeles conserva texto de copia rápida. Comparten parte de la UX, pero fusionarlos sin decisión de producto perdería semántica.
5. **Índices derivados:** `conversationSearch` y `globalChatSearch` facilitan consultas distintas; requieren política de creación/borrado y reconciliación, no un borrado indiscriminado.

## Secuencia recomendada

1. **Contención:** HTTPS/hosts del conversor; privacidad de perfiles/índice; retirar borrado global cliente del chat; validar reglas desplegadas en Emulator.
2. **Recordatorios:** aislar agenda por UID y limpiar al salir; bloquear reconciliación con caché vacía; extraer completar/posponer a operación idempotente; probar teléfono bloqueado, reinicio, offline y cambio de cuenta.
3. **Descargas:** estado pendiente durable, confirmación antes de consumirlo y recuperación ante proceso muerto; pruebas de DownloadManager real.
4. **Escala:** paginar batches y revisar transacciones de perfil; medir consulta global de chat y reconciliar índices.
5. **Mantenimiento:** dividir música y chat por responsabilidad sin reescritura total; añadir pruebas de contratos y estados de error.
6. **Release:** congelar commit, compilar y ejecutar tests, probar en dispositivo y revisar R8/backup/preset Cloudinary antes de publicar.

## Límites y verificaciones pendientes

- El archivo `firestore.rules` local puede no coincidir con el desplegado. Confirmar proyecto y versión antes de una corrección remota.
- No se verificó configuración efectiva de Cloudinary, permisos/restricciones de API key, backend HTTP ni posibles proxies HTTPS.
- No hubo ejecución en teléfono, validación de UI, descarga, alarma real, reproductor ni medición de rendimiento; los efectos descritos se infieren del código y deben probarse.
- El intento de Gradle terminó sin salida y se interrumpió; no usar reportes anteriores de `app/build` como prueba del árbol actual.
- No se inspeccionaron credenciales ni se copiaron valores secretos al reporte. El keystore ignorado de raíz debe permanecer fuera de Git.
