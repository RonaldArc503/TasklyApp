# Colores del reproductor — 29 de octubre de 2026

## Comportamiento

- **Automático variado**, predeterminado: elige primero una familia de color y después una de sus paletas. Evita las dos familias distintas más recientes, las diez paletas recientes y tonos consecutivos separados por menos de 45° en el círculo de matiz. El catálogo actual tiene 60 paletas y ocho familias; si en el futuro se reduce el catálogo, relaja las restricciones gradualmente para seguir funcionando.
- **Original**: asignación determinista a partir del ID de la canción. El nombre, URL, posición en la cola y canción anterior no participan. La misma canción recupera la misma paleta al reiniciar, con el catálogo actual. Dos originales pueden compartir familia; la variedad entre canciones corresponde al modo automático.
- **Cambiar color**: elige otra familia para la canción actual y activa el modo automático para las siguientes. Tocar el disco usa la misma acción. El modo seleccionado se muestra en Efectos del reproductor y hay una explicación junto a los controles.
- Color y animación son controles independientes. Elegir Galaxia, Marea u otra animación conserva la política de color.
- La elección y el historial pertenecen al controlador de reproducción. Siguen avanzando cuando el reproductor completo está cerrado. Reabrir la pantalla, pausar, buscar una posición o repetir la misma canción no elige otra paleta.
- El reproductor completo, la barra de audio del chat y el minirreproductor de música consultan la misma selección. El color de las tarjetas estáticas usa el original de su ID.

## Persistencia y cambios

- Preferencias locales `player_colors_v2`: modo, ID de canción activa, ID de paleta e historial acotado a diez selecciones. Se conserva la selección de una sesión restaurada y el historial para la siguiente canción.
- Las preferencias antiguas `palette_offset_*` dejan de aplicarse. Los desplazamientos anteriores recorrían paletas contiguas de una misma familia y no se migran a este comportamiento. Se conserva la selección de efecto visual.
- El catálogo se extrajo de `MusicPlayerScreen.kt` a `audio/PlayerPaletteCatalog.kt`. Se compararon las 60 identidades/nombres y los 240 valores RGB con el archivo anterior: coinciden.
- `PlayerColorPolicy` contiene las reglas puras y comprobables; `PlayerColorsController` mantiene la selección y sus preferencias. El composable ya no escribe un índice anterior mientras se compone.

## Validación

- Compilación Kotlin completada.
- `:app:assembleDebug` generó `app/build/outputs/apk/debug/app-debug.apk`.
- **55 pruebas aprobadas**, incluidas **16 nuevas**: 5.000 cambios de canción sin repetir las familias recientes ni las paletas del historial; saltos manuales con distancia mínima de matiz; original estable; reinicio; historial acotado; datos inválidos; selección equilibrada por familia y catálogos pequeños.
- Se conserva la exclusión temporal de `ReminderRepeatTest.kt` por su error de compilación previo, mediante un init script externo; el archivo de pruebas no se modifica.
- Lint global mantiene los cinco errores previos de recordatorios/manifiesto (tres `MissingPermission`, un `MissingSuperCall` y un `PermissionImpliesUnsupportedChromeOsHardware`). No se encontraron errores de Lint en los cambios de colores.
- Sin dispositivo ADB conectado. La apariencia en pantalla, los controles táctiles y la persistencia real de Android requieren validación en dispositivo.

## Revisión manual

1. Activar Automático variado y pasar por varias canciones, incluyendo cambios con la pantalla del reproductor cerrada.
2. Abrir el reproductor de nuevo: debe conservar la paleta vigente y coincidir con las barras de reproducción.
3. Activar Original, anotar una canción y su paleta, cambiar de canción y volver; repetir después de reiniciar la app.
4. En Original, pulsar Cambiar color: debe cambiar de familia y activar Automático variado. Volver a Original debe recuperar la paleta base.
5. Cambiar únicamente la animación: debe conservarse el color elegido.
