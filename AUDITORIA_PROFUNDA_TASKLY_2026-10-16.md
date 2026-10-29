# Auditoría profunda de Taskly

**Fecha:** 16 de octubre de 2026  
**Alcance:** revisión estática de la app Android Compose/Firebase, configuración de compilación, reglas de Firestore, estado Git y pruebas existentes. No se modificó código de producto.  
**Estado de verificación:** los hallazgos marcados como **confirmados** proceden directamente del código/configuración actual. Los marcados como **requiere prueba en dispositivo/producción** son riesgos plausibles que no se pueden afirmar sin ejecutar el flujo real.

## Resumen ejecutivo

La app tiene una base funcional amplia y buenas intenciones de arquitectura (Hilt, repositorios, `StateFlow`, Compose, paginación en varios flujos). Sin embargo, ahora mismo hay cuatro riesgos que deberían resolverse antes de ampliar funciones:

1. **P0 — Tráfico HTTP en el conversor:** la URL del servicio de conversión usa HTTP con una IP fija y la app permite tráfico sin cifrar. La descarga y el envío de solicitudes pueden ser interceptados o modificados en una red no confiable.
2. **P1 — Integridad de conversaciones Firestore:** cualquier participante puede borrar el documento raíz de una conversación. Firestore no borra subcolecciones en cascada, por lo que pueden quedar mensajes e índices huérfanos y los demás participantes pierden acceso al chat.
3. **P1 — Privacidad de perfiles:** cualquier usuario autenticado puede leer perfiles completos, que incluyen correo, correo normalizado y teléfono; además permite buscar por teléfono y correo. Es un directorio de PII mucho más expuesto de lo necesario.
4. **P1 — Mantenibilidad:** varias piezas críticas son “god files”. El reproductor de música tiene 4,020 líneas; el chat suma 1,353 de pantalla, 1,233 de ViewModel y 1,219 de repositorio. Cada ajuste de producto exige tocar bloques enormes y mezclados.

El árbol de trabajo no está limpio: hay 38 archivos modificados y archivos nuevos, con aproximadamente **4,433 inserciones y 891 eliminaciones** sin confirmar. Por tanto, este informe audita el estado presente, que incluye trabajo en curso, y no debe confundirse con una release estabilizada.

## Severidad y orden recomendado

| Prioridad | Hallazgos | Acción inicial |
|---|---|---|
| P0 | HTTP/cleartext del conversor | Mover servicio y descargas a HTTPS con dominio estable; bloquear cleartext en release. |
| P1 | Borrado de conversación y subcolecciones huérfanas | Eliminar el permiso cliente de borrar conversaciones; hacer borrado administrativo/Cloud Function con limpieza atómica o soft-delete. |
| P1 | Exposición de correo/teléfono | Separar perfil público de perfil privado e índice de búsqueda de acceso mínimo. |
| P1 | Release sin minificación y API key empaquetada | Activar/hardening de release después de pruebas; restringir/rotar la key y no tratarla como secreto de cliente. |
| P1 | Archivos y responsabilidades gigantes | Extraer módulos por capacidad antes de sumar más UI musical/chat. |
| P2 | Sincronización de perfil no escalable/atómica | Paginar lotes y rediseñar el dato denormalizado de miembros. |
| P2 | Estados locales contradictorios con una app sincronizada | Declarar qué es local y qué se sincroniza; persistir datos de usuario importantes en backend. |
| P2 | Pruebas insuficientes | Añadir pruebas de reglas, repositorios, flujos de alarmas y UI crítica. |
| P3 | Pulido UX, a11y y limpieza | Unificar feedback, retirar archivos muertos, lint/format y telemetría de fallos. |

---

## Hallazgos confirmados

### P0-01 — Conversor por HTTP y cleartext habilitado

**Evidencia**

- `ConverterViewModel.kt:690` define el backend como `http://<IP>:8000`.
- `ConverterViewModel.kt:342,359` crea y consulta conversiones por esa URL; `:310` usa la misma URL como `Referer` en la descarga.
- `AndroidManifest.xml:29` contiene `android:usesCleartextTraffic="true"` para toda la aplicación.

**Impacto**

En una red Wi‑Fi hostil un atacante puede observar o alterar solicitudes de conversión y respuestas de descarga. La IP fija añade fragilidad operacional: un cambio de host obliga a publicar una nueva app y no hay control de dominio/certificado ni rotación de endpoint.

**Recomendación**

- Publicar el API exclusivamente bajo HTTPS con un dominio propio y certificado válido.
- Definir endpoint por `BuildConfig`/flavor, no como constante en el ViewModel.
- Eliminar `usesCleartextTraffic=true` para release; si existe una necesidad de desarrollo, limitarla a debug mediante `networkSecurityConfig`.
- Aplicar validación de URL de descarga: host permitido, HTTPS obligatorio y error visible si el contrato devuelve algo distinto.

### P1-01 — Un participante puede borrar la conversación raíz y dejar datos huérfanos

**Evidencia**

- `firestore.rules:133-143`: `allow delete: if participant(conversationId);` sobre `/conversations/{conversationId}`.
- Los mensajes viven en `/conversations/{conversationId}/messages/{messageId}` (`firestore.rules:145-162`) y los recibos en otra subcolección (`:164-168`). Firestore no realiza borrado cascada de subcolecciones.
- Índices globales quedan en colecciones externas (`globalChatSearch`, `conversationSearch`, `firestore.rules:182-219`).

**Impacto**

Un participante puede hacer desaparecer el chat para todos, dejar mensajes/recibos/búsquedas sin padre y generar almacenamiento facturable o resultados inconsistentes. También contradice la política de producto de “cualquiera puede borrar mensajes para todos”; esa política no implica necesariamente que cualquiera pueda borrar una conversación completa.

**Recomendación**

- Denegar `delete` del documento de conversación desde el cliente.
- Para “eliminar para mí”, usar únicamente `users/{uid}/chatStates`.
- Para una eliminación global real, usar una función de servidor con autorización explícita, soft-delete/auditoría y borrado paginado de subcolecciones e índices.

### P1-02 — Perfiles, correo y teléfono quedan legibles para todo usuario autenticado

**Evidencia**

- `UserProfile.kt:3-10` reúne `email`, `emailLower`, `phone`, foto y nombre en el mismo modelo.
- `firestore.rules:114-117` permite leer cualquier `/profiles/{uid}` a cualquier usuario autenticado.
- `firestore.rules:119-126` permite leer cualquier documento de `phoneIndex` a cualquier usuario autenticado.
- `ProfileRepositoryImpl.kt:127-153` busca perfiles por prefijo de teléfono y por correo exacto.

**Impacto**

Un usuario autenticado puede enumerar o consultar datos personales de otros usuarios fuera de una conversación compartida. Guardar `emailLower` también amplifica la exposición: es un índice de búsqueda de correo en texto claro.

**Recomendación**

- Separar `profilesPublic` (uid, nombre, foto, quizá alias) de `users/{uid}/privateProfile` (correo/teléfono).
- Cambiar la búsqueda a un servicio autenticado que devuelva solo campos mínimos y con rate limiting, o requerir invitación/contacto previo.
- Evitar que los documentos de índice sean legibles directamente; comprobar disponibilidad/identidad desde backend o Cloud Function.
- Revisar y migrar reglas con Emulator antes de desplegarlas.

### P1-03 — Release no endurecida y clave de YouTube incorporada al binario

**Evidencia**

- `app/build.gradle.kts:29-30` toma `YOUTUBE_API_KEY` y la expone con `buildConfigField`.
- `app/build.gradle.kts:37` deja `isMinifyEnabled = false` para release.
- `app/proguard-rules.pro:1` indica que R8 “remains disabled for now”.
- `local.properties` contiene una clave configurada; aunque está ignorado por Git, BuildConfig la incorpora a los APK generados.

**Impacto**

Una API key en un cliente móvil se puede extraer del APK. Que no esté en Git evita un tipo de filtración, pero no la convierte en secreto. Sin minificación se facilita ingeniería inversa, aumenta tamaño y se publica más información de implementación.

**Recomendación**

- Restringir la clave por package name + SHA-1/SHA-256, APIs y cuota; rotarla si ya se distribuyó sin restricciones adecuadas.
- Mover operaciones privilegiadas a backend cuando aplique.
- Crear un flavor release endurecido con R8, `shrinkResources`, reglas mínimas y una matriz de smoke tests de Firebase/media/adjuntos antes de activarlo en producción.

### P1-04 — Cloudinary permite subida directa desde el APK

**Evidencia**

- `CloudinaryRepositoryImpl.kt:40-50` define en el cliente el preset de subida, la API key pública, carpeta por usuario y endpoint.
- La subida es directa y no hay una firma emitida por servidor en este flujo.

**Impacto**

Si el preset del proveedor admite subida sin firma de forma amplia, puede ser abusado desde clientes modificados para consumir cuota o depositar contenido. El código no confirma la configuración efectiva del preset, por lo que el abuso concreto requiere revisión del panel Cloudinary, pero la superficie existe.

**Recomendación**

- Revisar el preset: tipos permitidos, tamaño, formatos, origen/carpeta y rate limiting.
- Preferir firmas de corta duración desde backend/Cloud Function y validar el usuario autenticado/metadata.
- Mantener la eliminación de assets únicamente del lado de servidor; nunca incorporar secretos Cloudinary al APK.

### P1-05 — Estado de trabajo masivo sin confirmar dificulta saber qué fue validado

**Evidencia**

- `git status --short` muestra 38 archivos modificados y archivos Kotlin nuevos sin seguimiento.
- `git diff --stat` registra 4,433 inserciones y 891 eliminaciones.
- `git diff --check` detectó espacios al final en `AudioPlayerController.kt:574` y `:590`.

**Impacto**

Cambios de música, chat, alarmas, reglas y navegación se mezclan en una sola área de trabajo. Hace difícil revertir, probar por funcionalidad, revisar seguridad o identificar la causa de un regresión.

**Recomendación**

- Separar en commits pequeños por capacidad (alarmas, chat, música, reglas).
- Añadir pre-commit/CI con `git diff --check`, formato Kotlin y pruebas.
- No desplegar reglas ni generar release hasta congelar una línea base compilada y revisada.

### P2-01 — Sincronización de perfil rompe al crecer y no es transaccional

**Evidencia**

- `ProfileRepositoryImpl.kt:103-116` reclama el teléfono nuevo, borra el anterior y actualiza perfil en operaciones separadas.
- `ProfileRepositoryImpl.kt:173-189` consulta todas las conversaciones del usuario y las actualiza en un único `WriteBatch`.
- Firestore limita un batch a 500 operaciones; el código no pagina ni parte el batch.

**Impacto**

Una interrupción entre operaciones puede dejar el índice telefónico y el perfil inconsistentes. Un usuario en más de 500 conversaciones no podrá actualizar su foto/nombre/teléfono porque fallará el batch de sincronización. El error se propaga como fallo general de actualización.

**Recomendación**

- Resolver el cambio de índice + perfil mediante transacción o un proceso recuperable de dos fases.
- Paginar `syncProfileInConversations` en lotes de menos de 500, registrar progreso y reintentar de forma idempotente.
- A más largo plazo, no denormalizar teléfono en cada miembro de conversación; para mostrarlo, usar un snapshot público mínimo o datos de perfil controlados.

### P2-02 — El chat mezcla demasiadas responsabilidades y realiza N lecturas para búsqueda global

**Evidencia**

- `ChatRepositoryImpl.kt` tiene 1,219 líneas y concentra conversaciones, historial, envíos, índices, recibos, reacciones, edición y borrado.
- `ChatViewModel.kt` tiene 1,233 líneas y además gestiona adjuntos, reintentos, grabación, hilos, búsqueda, reproducción y estado local.
- `ChatScreen.kt` tiene 1,353 líneas y contiene permisos, cámara, grabación, scroll, búsqueda, composer y renderizado.
- `ChatRepositoryImpl.kt:936-955` hace una consulta de índice y luego una lectura individual por resultado para recuperar cada mensaje.

**Impacto**

El acoplamiento eleva el riesgo de regresión. La búsqueda global escala como 1 consulta + N lecturas, puede devolver huecos si el índice y el mensaje están desincronizados, y aumenta coste/latencia con el límite de resultados.

**Recomendación**

- Separar `ConversationRepository`, `MessageRepository`, `ReceiptRepository`, `SearchIndexRepository` y un coordinador de envío.
- Dividir `ChatScreen` en contenedor, timeline, composer, permisos/captura y overlays.
- Guardar en el índice solo el snippet seguro necesario para mostrar resultados o usar una búsqueda de servidor; tratar el índice como derivado, con una tarea de reconciliación.

### P2-03 — El reproductor reúne UI, física visual, paletas y controles en un solo archivo

**Evidencia**

- `MusicPlayerScreen.kt` tiene 4,020 líneas.
- Contiene la pantalla y estado principal (`:160-280`), la barra de seek (`:2993-3115`), controles, múltiples renderizadores y un catálogo de 60 paletas (`:3880-3978`) junto con hash de selección (`:3992-4020`).
- `MusicScreens.kt` (2,146 líneas) y `MareaPlaylistScreen.kt` (1,883 líneas) duplican la concentración en el mismo dominio.

**Impacto**

Un cambio visual puede afectar reproducción, gestos, accesibilidad y rendimiento. Los conflictos de merge y el tiempo de recompilación/revisión crecerán rápido. Es muy difícil probar piezas aisladas o reutilizar el sistema visual.

**Recomendación**

- Extraer `player/ui`, `player/controls`, `player/visuals/*`, `player/palette`, `player/queue` y `player/model`.
- Mover las paletas a datos inmutables en archivo dedicado o recurso, con test de selección determinista.
- Medir en dispositivo por efecto y desactivar automáticamente efectos costosos para batería baja/animaciones reducidas.

### P2-04 — Estados locales y sincronizados no están definidos de forma uniforme

**Evidencia**

- `DeletedMessagesRepositoryImpl.kt:22-79` conserva “eliminar para mí” localmente, con máximo 2,000 entradas y expiración de 90 días.
- `DownloadHistoryRepositoryImpl.kt:27-103` guarda historial de descargas local en `SharedPreferences` y silencia fallos de guardado (`:93-97`).
- `SettingsRepositoryImpl.kt:23-55` guarda tema y descargas automáticas solo en `SharedPreferences`.
- En cambio, tareas, recordatorios, perfiles y chat dependen de Firebase.

**Impacto**

Al cambiar de teléfono, reinstalar o borrar datos, algunas decisiones del usuario desaparecen mientras otras reaparecen. La app se presenta como autenticada/sincronizada pero no declara al usuario qué datos son solo del dispositivo. Los fallos de persistencia de descargas pueden pasar desapercibidos.

**Recomendación**

- Publicar un contrato de persistencia: “local”, “sincronizado” o “caché recuperable” por cada dato.
- Migrar a DataStore los ajustes puramente locales; sincronizar las preferencias que deban acompañar a la cuenta.
- Mostrar feedback y logging estructurado cuando una escritura local importante falle.
- Confirmar de producto si “eliminar para mí” debe sobrevivir a otros dispositivos; si sí, llevarlo a `users/{uid}`.

### P2-05 — Acceso a enlaces convierte `www.` en HTTP y descarta documentos de la vista de información

**Evidencia**

- `ChatInfoViewModel.kt:103-121` analiza enlaces y en `:117-120` antepone `http://` a `www.`.
- El manifest permite tráfico sin cifrar (`AndroidManifest.xml:29`).
- `ChatInfoViewModel.kt:127-136` ignora explícitamente documentos y PDFs al construir el resumen de adjuntos.

**Impacto**

El detalle de chat puede abrir una variante no cifrada cuando exista equivalente HTTPS. Además, un usuario envía PDFs/documentos en chat pero no puede encontrarlos desde “información del chat”: contradicción funcional entre capacidades de envío y descubrimiento.

**Recomendación**

- Normalizar `www.` a `https://`, permitir solamente esquemas esperados y mostrar host antes de abrir enlaces externos.
- Añadir pestaña “Documentos” y búsqueda/filtro por tipo en información de chat.
- Corregir la expresión duplicada `Triple(media, lks, audio)` en `ChatInfoViewModel.kt:139-140`; no es un fallo funcional confirmado, pero es una señal de código sin limpiar.

### P2-06 — Alarmas: lógica de completar/snooze duplicada en receptor y Activity

**Evidencia**

- `ReminderReceiver.kt:33-114` implementa completar y posponer desde acciones de notificación.
- `ReminderAlarmActivity.kt:138-209` repite el mismo flujo para la interfaz full-screen: buscar aviso, marcar/arhivar recurrente, calcular siguiente fecha, reprogramar, cancelar notificación.

**Impacto**

Dos rutas de negocio deben mantenerse idénticas. Cualquier corrección de recurrencia, recibos, auditoría o error puede aplicarse solo a una y causar comportamiento distinto según cómo el usuario complete el mismo aviso.

**Recomendación**

- Extraer `CompleteReminderUseCase` y `SnoozeReminderUseCase` que devuelvan un resultado tipado.
- Usar los mismos casos desde Activity, receiver y pantalla; dejar cada superficie solo con permisos, navegación y feedback.
- Añadir pruebas para completar desde notificación y desde pantalla bloqueada, incluido offline/reinicio.

### P2-07 — Receptores y procesos en background sin una capa uniforme de trabajo durable

**Evidencia**

- `DownloadCompleteReceiver.kt:82-84` abre un `CoroutineScope(Dispatchers.IO)` propio para guardar la descarga después del broadcast.
- `DownloadHistoryRepositoryImpl.kt:30-37` y `SettingsRepositoryImpl.kt:24-35` crean scopes singleton propios sin ciclo de vida explícito.
- Hay varios `catch (_: Exception) {}` que silencian problemas, por ejemplo `DownloadHistoryRepositoryImpl.kt:88-97`, `DownloadTracker.kt:41-49` y `ConverterViewModel.kt:629-640` al borrar archivo.

**Impacto**

Después de un broadcast el proceso puede morir antes de finalizar el trabajo que no está ligado a un mecanismo durable. Los errores silenciosos hacen que el usuario vea un historial incompleto sin explicación y dificultan diagnosticar producción.

**Recomendación**

- Para operaciones que deben sobrevivir a reinicio/proceso muerto, usar WorkManager o persistir primero un estado recuperable.
- Centralizar scopes de aplicación y su cancelación donde corresponda.
- Sustituir capturas vacías por un resultado explícito, log con contexto y feedback cuando tenga impacto de usuario.

### P3-01 — Feedback y estados de error no son homogéneos

**Evidencia**

- Conversor usa snackbar y Toast (`ConverterScreen.kt:77-98`, `ConverterViewModel.kt:382-384`).
- Música usa `feedbackMessage` local que desaparece tras 2.2 segundos (`MusicPlayerScreen.kt:205-210`; `MareaPlaylistScreen.kt:240-244`).
- Varias pantallas usan estados de error de página completa; otras almacenan mensajes efímeros.
- La Activity de alarma ejecuta operaciones de red en `complete`/`snooze` (`ReminderAlarmActivity.kt:138-209`) sin estado visible de procesando, éxito o error antes de cerrar.

**Impacto**

Una acción idéntica puede sentirse fiable en una pantalla y “no pasó nada” en otra. Cerrar la alarma antes de saber que la mutación remota llegó puede confundir al usuario si la operación falla.

**Recomendación**

- Definir un contrato de eventos UI: operación en curso, éxito, fallo recuperable, reintento y detalle de soporte.
- Usar una sola capa de `UiEvent`/snackbar accesible para acciones no bloqueantes; reservar Toast para casos de sistema.
- Para alarmas, confirmar primero la operación local/durable y mostrar un estado de sincronización, en vez de depender exclusivamente de la red.

---

## Contradicciones de producto y UX

| Tema | Contradicción observada | Decisión recomendada |
|---|---|---|
| Chat | Se pueden enviar PDF/DOC, pero información de chat los ignora. | Añadir documentos y filtros en la vista de información. |
| Privacidad | La app tiene chats participantes, pero perfil/correo/teléfono son leíbles por cualquier autenticado. | Hacer público solo lo imprescindible y privado lo sensible. |
| Sincronización | Cuenta/Firebase para datos principales, pero ajustes, historial de descargas y “eliminar para mí” quedan locales sin declararlo. | Etiquetar y decidir el alcance de sincronización. |
| Borrado | “Cualquiera elimina mensajes para todos” se extiende por reglas a “cualquiera borra el chat entero”. | Separar borrar mensaje, ocultar para mí y borrar conversación global. |
| Seguridad | Conversor propio promete feedback y control, pero opera por HTTP/IP fija; además la vista de links baja a HTTP. | HTTPS end-to-end y hosts permitidos. |
| Música | El control se llama waveform, pero muestra barras uniformes, no forma de onda real (`MusicPlayerScreen.kt:3022-3092`). | Renombrar a barra de progreso estilizada o calcular waveform real. |
| Metadata | El reproductor muestra siempre artista “Taskly” (`MusicPlayerScreen.kt:2978-2984`) aunque el track tenga autor/canal en otros flujos. | Extender `AudioTrack` con artista y usar un fallback honesto. |
| Navegación | Música, conversor, guardados, ajustes y portapapeles están escondidos tras Perfil, aunque varias son áreas principales de uso. | Validar IA de navegación con usuarios; mantener atajos desde Inicio y medir descubribilidad. |

## Archivos sobredimensionados

| Archivo | Líneas | Problema |
|---|---:|---|
| `ui/screen/music/MusicPlayerScreen.kt` | 4,020 | UI, efectos, gestos, paletas, formatos y lógica visual en un archivo. |
| `ui/screen/music/MusicScreens.kt` | 2,146 | Inicio musical y detalle concentrados. |
| `ui/screen/music/MareaPlaylistScreen.kt` | 1,883 | UI de playlist, feedback, temporizador y acciones. |
| `ui/screen/chat/ChatScreen.kt` | 1,353 | Permisos, cámara, voz, scroll, composer, búsqueda y presentación. |
| `ui/screen/chat/ChatViewModel.kt` | 1,233 | Orquestación de chat, adjuntos, hilos, búsqueda, estados y reintentos. |
| `data/repository/ChatRepositoryImpl.kt` | 1,219 | Acceso Firestore y varias políticas de negocio mezcladas. |
| `ui/screen/converter/ConverterScreen.kt` | 1,000 | UI y decisiones de flujo extensas. |
| `ui/screen/chat/MessageBubble.kt` | 895 | Renderización de muchos tipos y acciones de mensaje. |
| `ui/screen/home/HomeScreen.kt` | 819 | Dashboard y varias tarjetas/estados. |
| `audio/AudioPlayerController.kt` | 739 | Servicio/controlador de reproducción, cola, timer y estado. |

**Regla práctica sugerida:** cuando un archivo UI supere 400–600 líneas o mezcle más de dos responsabilidades (por ejemplo render, permisos, almacenamiento, navegación), extraer componentes/casos de uso antes de agregar otra capacidad.

## Pruebas y calidad

### Cobertura actual visible

- Se encontraron **6 archivos de pruebas unitarias** y **1 de instrumentación**.
- Cubren parser de URL compartida, utilidades de chat, normalizador de búsqueda, repetición de avisos y movimiento de Marea V2.
- No hay evidencia en el árbol de pruebas de Emulator/Rules de Firestore, repositorios Firebase, Cloudinary, descargas, permisos, navegación completa, alarmas en dispositivo o casos de UI Compose críticos.

### Ejecución en esta auditoría

Se lanzó `:app:compileDebugKotlin :app:testDebugUnitTest --offline` sobre el árbol actual. El proceso no emitió salida durante aproximadamente dos minutos y fue cancelado para no bloquear la auditoría. Esto **no prueba** un error de compilación ni un éxito.

Hay reportes de pruebas generados anteriormente en `app/build/reports`, pero son artefactos de una ejecución anterior y no validan el conjunto actual de cambios sin confirmar.

### Plan mínimo de pruebas antes de release

1. Ejecutar compilación limpia y unit tests del commit congelado.
2. Probar reglas Firestore con Emulator: crear chat, enviar/editar/reaccionar, hilo, borrado permitido/no permitido, índices, perfiles y privacidad.
3. Pruebas instrumentadas de navegación, modo offline, permisos de micrófono/cámara/notificación/alarma y share sheet.
4. Dispositivo real: conversión HTTPS, descarga completa/fallida, reproducción background, reanudación, upload de archivos y alarmas con app cerrada/bloqueada/reinicio.
5. Benchmark o Macrobenchmark de chat largo, lista de música y efectos visuales en un dispositivo de gama media.

## Mejoras de arquitectura por fases

### Fase 0 — Contención (P0/P1)

- HTTPS, dominio y configuración release segura para conversor.
- Corregir reglas de borrado de conversación y exposición de PII.
- Revisar preset Cloudinary y restringir/rotar credenciales públicas según proveedor.
- Congelar un commit limpio y ejecutar suite verificable.

### Fase 1 — Contratos de dominio

- Crear casos de uso para completar/posponer recordatorio, enviar mensaje y administrar conversación.
- Definir modelos de perfil público/privado y contrato de persistencia (local/sync/caché).
- Centralizar configuración por flavor: endpoints, feature flags y límites.

### Fase 2 — Extracción de módulos de alto riesgo

- Partir dominio de música y chat en archivos/módulos pequeños y testeables.
- Extraer componentes Compose sin dependencias de Firebase/Android framework.
- Reemplazar lógica de background frágil por trabajos durables cuando aplique.

### Fase 3 — Experiencia y observabilidad

- Unificar `UiEvent`, errores recuperables y reintentos.
- Añadir logs estructurados sin PII, Crashlytics/telemetría con consentimiento y diagnóstico de red.
- Mejorar accesibilidad de controles custom, progreso y estados de carga.

## Quick wins concretos

1. Cambiar el endpoint del conversor a HTTPS y desactivar cleartext en release.
2. Cambiar `allow delete` de conversaciones a `false` mientras se diseña la operación global segura.
3. No exponer email/teléfono en `profiles` de lectura global.
4. Corregir `http://` implícito de los links y añadir pestaña Documentos en chat info.
5. Extraer el flujo común de completar/posponer avisos.
6. Eliminar la expresión duplicada en `ChatInfoViewModel` y whitespace detectado por Git.
7. Añadir pruebas de reglas Firestore y al menos un test de cada flujo de P0/P1.

## Límites de esta auditoría

- No hubo despliegue ni modificación de Firebase, Cloudinary, backend ni APK.
- No se inspeccionó el panel Cloudinary ni la infraestructura del conversor; sus configuraciones efectivas deben verificarse fuera del repo.
- No se realizó prueba física de alarmas, cámara, grabación, media, descargas o rendimiento. Las observaciones UX/rendimiento estáticas requieren esa validación.
- Las reglas evaluadas son las del archivo local actual; antes de corregirlas hay que comparar contra la versión efectivamente desplegada y probar en Emulator.
