# Chat Android y web: permisos y sincronización

Fecha: 2026-11-04. Proyecto Firebase compartido: `tasksv`.

## Cambios

- Los dos proyectos mantienen exactamente el mismo `firestore.rules`. El script de reglas comprueba su igualdad antes de ejecutar casos positivos y negativos. La configuración Android sigue siendo la referencia para los índices; los diez índices declarados ya estaban desplegados, por lo que no se eliminaron ni reconstruyeron índices.
- Se habilitan índices de conversaciones y mensajes, recibos propios y desarchivado limitado de otro participante. Un usuario ajeno al chat continúa sin acceso a mensajes; un participante no puede editar el texto de otro ni escribir sus recibos, fijados o datos privados. Los teléfonos permiten repetir la reserva del propietario y bloquean la apropiación o eliminación por otro usuario.
- Las reglas admiten los mensajes web antiguos sin `conversationId`, las reacciones a mensajes sin `text`, el formato del editor web y hasta 30 adjuntos. Se preservan las operaciones web de grupos y se impiden cambios de participantes en chats directos.
- Editar un mensaje actualiza texto, índices y texto fijado en una transacción. Los mensajes antiguos sin índice también se pueden editar. Una edición desde el editor de texto Android elimina rangos de formato anteriores que ya no corresponden al texto.
- Eliminar mensajes borra índices, fijados y contador del hilo de forma transaccional e idempotente, incluidos índices inexistentes. Dos eliminaciones concurrentes no descuentan dos veces una respuesta. La reparación de la vista previa comprueba el tiempo del mensaje eliminado y relee el candidato para no sobrescribir un envío concurrente.
- En conversaciones de hasta 200 participantes, los envíos, índices y desarchivado se escriben en la misma transacción. Los participantes adicionales de grupos grandes conservan las escrituras por lotes existentes. Los tiempos de mensajes nuevos son crecientes en ambas versiones.
- La web reutiliza chats antiguos y crea los nuevos con el mismo identificador SHA-256 que Android. Se evita crear otro chat al comenzar una conversación desde la otra versión. No se fusionaron ni eliminaron conversaciones antiguas.
- La web conserva `threadRootId`, actualiza el contador de respuestas y escribe los recibos del último mensaje entrante cuando la conversación está visible. No marca como leído un mensaje nuevo que todavía no está en la vista.
- Android añade listeners por grupos de diez identificadores para el historial cargado y el hilo abierto; se cierran con el ViewModel. Las ediciones y eliminaciones fuera de los últimos 30 mensajes también llegan sin recargar. Una caché local vacía no se interpreta como una eliminación del servidor. La lectura inicial de caché no sustituye un snapshot reciente que ya haya llegado.

## Archivos principales

Android: `ChatRepositoryImpl`, `ChatMessageDeletion`, `ChatMessageUpdates`, `IChatRepository`, `ChatUseCases`, `ChatViewModel`.

Web: `chatConversations.ts`, `chatSearchIndex.ts`, `chatMessageMutations.ts`, `chatMessageWrites.ts`, `chatThreads.ts`, `ConversationWindow.tsx`.

No se añadieron dependencias ni otro sistema de sincronización: ambas versiones usan las mismas colecciones y listeners Firestore.

## Verificación local

Con Auth en 9098 y Firestore en 8088, usando exclusivamente `demo-taskly-music`:

```powershell
$env:TASKLY_RULES_DEPS = '<directorio con firebase y @firebase/rules-unit-testing>'
$env:FIRESTORE_EMULATOR_HOST = '127.0.0.1:8088'
node scripts/test-chat-sync-rules.mjs
node scripts/test-music-playlist-rules.mjs
```

Desde el proyecto web:

```powershell
$env:FIRESTORE_EMULATOR_HOST = '127.0.0.1:8088'
npm run test:chat-sync
npm run test:chat-burst
npm run check
```

Resultados:

- 51 comprobaciones de reglas, incluida la denegación de accesos ajenos, aprobaron. La regresión de reglas de listas musicales aprobó.
- 36 pruebas web de escritura, hilos, formato, portapapeles, contenido y listas aprobaron, más la integración de dos clientes de `chatSync.emulator.test.mjs`.
- Ráfaga: 130 envíos concurrentes de dos usuarios, sin pérdidas ni duplicados; orden, lectura, hilos, formato, adjuntos y contadores correctos.
- Android: 110 pruebas unitarias, 105 aprobadas y 5 opcionales omitidas. Compilaciones debug, paquete de pruebas y release correctas.
- Android API 35: `ChatSyncRepositoryDeviceTest` y `MusicPlaylistRepositoryDeviceTest` aprobaron, dos pruebas instrumentadas sin fallos. Se comprobaron repositorios nativos reales, edición de mensajes antiguos sin índices, historial fuera de los 30 recientes, eliminación concurrente, recibos, treinta adjuntos y ausencia de regresión en copias musicales.
- `astro check`: cero errores, cuatro avisos informativos previos. Build web correcto.

## Publicación

Se publicaron Hosting y reglas con `firebase deploy --only 'hosting,firestore:rules' --project tasksv --non-interactive`. Firebase confirmó la compilación y publicación de las reglas y la nueva versión Hosting. Los módulos JavaScript de la portada pública coinciden por SHA-256 con el build local.

Reglas idénticas Android/web, SHA-256: `f538fcd865145c1748629af1195a5423ca01af73076fff3a1927091611ff8329`.

La web está en https://tasksv.web.app. Una pestaña abierta anteriormente necesita cargar la nueva versión una vez. Después los cambios de mensajes llegan mediante listeners, sujetos a la conexión de cada cliente. Las pruebas con datos de conversación se realizaron en Firebase local; no se enviaron, editaron ni eliminaron mensajes de producción.

APK debug: `app/build/outputs/apk/debug/app-debug.apk`. La release compilada no está firmada: `app/build/outputs/apk/release/app-release-unsigned.apk`. En esta sesión no había un teléfono físico conectado; las pruebas Android se realizaron en el emulador. No hubo commit ni push.
