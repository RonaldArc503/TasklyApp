# Listas compartidas y descargas de música

## Comportamiento

- Compartir genera `https://tasksv.web.app/musica/lista/<token aleatorio>`. Reutiliza la identidad de la lista. Android admite ambos dominios de Firebase Hosting y conserva compatibilidad con `taskly://playlist/<token>`. La página web permite iniciar sesión, ver las canciones y guardar la misma referencia en la biblioteca.
- El receptor ve las canciones y puede reproducirlas, descargarlas o guardar la lista. Guardar crea una copia editable e independiente con un identificador determinista. Abrir o guardar un enlace propio no duplica la lista original.
- La biblioteca conserva solo la copia, con sus propias canciones, nombre y contador. El receptor puede agregar, quitar y reordenar canciones sin cambiar el original. Los cambios o la eliminación del original no alteran la copia ni los archivos ya descargados.
- El selector Agregar a lista muestra las listas propias editables, incluidas las copias guardadas. Una transacción evita duplicar canciones y preserva su posición y metadatos cuando ya existen.
- Los menús de listas incluyen Compartir y Descargar / Offline. La vista de lista muestra un botón de descarga sobre la portada de cada canción pendiente. Durante la descarga muestra progreso y, ante un error, un botón para reintentar; al completarse desaparece. No muestra etiquetas offline bajo las canciones ni un contador sobre Reproducir. El menú del reproductor junto a Letras contiene únicamente Agregar a lista y la acción offline con su estado.

## Organización

- `SharedMusicPlaylistRepository`: publicación del identificador compartido, observación de la lista y guardado idempotente de copias editables. Los permisos permiten leer únicamente listas explícitamente compartidas; las escrituras del receptor en el original permanecen bloqueadas.
- `MusicRepositoryImpl`: observadores existentes, resolución de referencias antiguas y transacciones de canciones. Los cambios de contador no reinician el observador de canciones.
- `MusicTrackActions` / `MusicTrackActionsViewModel`: selector y acciones reutilizados por biblioteca, lista y reproductor.
- `MusicPlaylistActions`: menú de listas y operaciones de compartir/descargar. `MusicDownloadBadge` reutiliza la cola existente para el indicador de cada portada.
- `MediaDownloadManager`: única cola y única identidad de archivo por canción. Comprueba el archivo antes de descargar y conserva las descargas terminadas aunque otras fallen. Las descargas manuales repetidas no vuelven a encolarse.
- `MusicDownloadStore`: registro persistente de solicitudes musicales pendientes. Se restaura al inicializar el gestor en la siguiente apertura de la aplicación. Un error se conserva y permite reintentar; no se borra ningún archivo completado.

Las transferencias usan la cola existente de la aplicación, sin introducir otro servicio de descargas. Si Android termina el proceso, los archivos parciales se vuelven a descargar al restaurar la cola; no hay reanudación por bytes ni un servicio nuevo que garantice ejecución con la aplicación cerrada.

## Activación

La página web y la asociación de dominios están publicadas en Firebase Hosting. Se comprobó que las reglas desplegadas ya incluyen música; no se desplegaron reglas nuevas durante la integración web. Instalar el APK actualizado y volver a compartir la lista genera enlaces HTTPS. Los enlaces enviados anteriormente no cambian automáticamente.

## Verificación reproducible

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:JAVA_TOOL_OPTIONS = '-Djava.net.preferIPv4Stack=true -Djdk.net.unixdomain.tmpdir=C:\Windows\Temp'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon
```

Pruebas nuevas:

- `MusicPlaylistLinkTest`: enlace, validación de entradas, identidad de guardado y referencias de solo lectura.
- `MusicPlaylistDownloadsTest`: dos listas con canciones repetidas, archivos existentes, error parcial, reintento y restauración de solicitudes persistidas. Usa archivos y repositorios de prueba; no accede a servicios externos.
- `MusicPlaylistRepositoryDeviceTest`: repositorios Android reales contra Auth y Firestore locales del proyecto `demo-taskly-music`; publicación concurrente, guardado concurrente, canciones duplicadas, cambios de canciones/nombre/contador y aislamiento del original.
- `MusicLyricsDeviceTest`: además de las comprobaciones previas del reproductor, comprueba el nuevo menú y que un archivo local ya disponible no permita iniciar otra descarga.

Para las reglas, instalar `@firebase/rules-unit-testing` y `firebase` en un directorio temporal y asignar esa ruta a `TASKLY_RULES_DEPS`. Con Firestore local iniciado en `127.0.0.1:8088`:

```powershell
$env:TASKLY_RULES_DEPS = '<directorio temporal con node_modules>'
$env:FIRESTORE_EMULATOR_HOST = '127.0.0.1:8088'
node scripts/test-music-playlist-rules.mjs
```

El script exige un emulador local y utiliza únicamente `demo-taskly-music`. Comprueba lectura antes/después de compartir, bloqueo de enumeración y escrituras ajenas, eliminación de la referencia y revocación del acceso.

La prueba Android del repositorio requiere Auth en `9098` y Firestore en `8088`, accesibles desde el emulador Android mediante `10.0.2.2`. Los clientes de prueba usan aplicaciones Firebase separadas; no cambian la autenticación ni los datos del proyecto real.

## Resultado de esta validación

- `testDebugUnitTest`: 107 pruebas, 102 aprobadas y 5 opcionales omitidas; sin fallos.
- `assembleDebug` y `assembleDebugAndroidTest`: correctos.
- Emulador Android API 35: las dos pruebas de descargas, la prueba del repositorio compartido y la prueba de interfaz/letras aprobaron (4 pruebas, 0 fallos). El reproductor mantuvo reproducción local, favoritos, letras, búsqueda temporal y cambio de canción.
- Reglas Firestore: script local aprobado. Los rechazos de permisos que imprime forman parte de las comprobaciones negativas.
- Android resolvió el enlace `taskly://playlist/...` a `MainActivity`. La captura del menú se inspeccionó visualmente.
- `git diff --check`: correcto. Sin commit, push ni despliegues. No se validó en teléfono físico.
- Ajuste posterior de feedback: se eliminaron las etiquetas y el contador offline de la lista. `assembleDebug` y `assembleDebugAndroidTest` aprobaron; `MusicDownloadBadgeDeviceTest` aprobó en API 35 y verificó descarga sin activar reproducción, cola, progreso, reintento y desaparición del botón al completarse o existir ya el archivo.

## Copias editables y confirmación breve

Guardar desde Android o la web copia las canciones en `users/<uid>/playlists/shared_<token>/tracks`, conserva sus identificadores y orden y guarda `copiedFromShareId` como procedencia. Al terminar se eliminan los campos de referencia al original y su `shareId`; compartir una copia genera su propio enlace. Las referencias de la versión anterior se convierten en el mismo identificador al abrirlas en Android.

La copia usa transacciones de hasta 400 canciones, un identificador de intento y confirmación final del padre. Un intento interrumpido se puede reintentar sin duplicar listas ni modificar copias ya terminadas. Los archivos offline se reutilizan por canción. La confirmación usa el aviso flotante existente (2,2 segundos), sin etiquetas permanentes de lista compartida o solo lectura.

Validado: copias concurrentes, edición independiente, conversión de referencia antigua, recuperación de un intento parcial de 503 canciones y conservación de la copia tras borrar el original.

## Validación física final: Redmi Note 9 Pro

Se actualizó la instalación debug mediante `adb install -r`, conservando los datos. Cinco pruebas instrumentadas aprobaron en el Redmi, sin fallos:

- Dos pruebas de descargas: canciones compartidas entre listas, omisión de archivos existentes, error parcial, reintento y recuperación de la cola persistida.
- Repositorios y `PlaylistDetailViewModel` reales: guardado concurrente sin duplicados, copia editable independiente, conversión automática de una referencia antigua en el mismo identificador, recuperación de 503 canciones y edición de la copia tras eliminar el original (504 canciones al agregar una nueva).
- Botón sobre la portada: no inicia reproducción al pulsarlo; muestra cola, progreso y reintento; desaparece cuando el archivo está disponible.
- Reproductor: menú con Agregar a lista y acción offline, reproducción de archivos locales, letras, búsqueda temporal, cambio de canción y favoritos de prueba.

Los clientes Firebase de la prueba usan aplicaciones nombradas independientes y los emuladores locales, accesibles en el teléfono mediante `adb reverse` y el argumento `-e musicEmulatorHost 127.0.0.1`. No se modificaron listas ni cuentas de producción. En MIUI fue necesario llevar la actividad MAIN/LAUNCHER al frente para completar las comprobaciones de interfaz.

Se corrigió la espera del metadato remoto al observar referencias antiguas: ahora se emite primero la entrada guardada, permitiendo iniciar su conversión sin esperar la actualización del original. La regresión quedó cubierta por la prueba del ViewModel.

Verificación final: 110 pruebas unitarias, 105 aprobadas y 5 opcionales omitidas; compilación debug, paquete de pruebas y release correctos. La última release no está firmada ni instalada en el Honor; la actualización física realizada corresponde al Redmi debug. La reproducción del proveedor de Internet no se volvió a probar en esta ronda.
