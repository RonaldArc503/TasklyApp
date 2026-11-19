package com.ronaldcolocho.taskly.audio

/**
 * Modos de animación visual para las letras sincronizadas en el reproductor de música.
 */
enum class LyricsAnimationMode(
    val id: String,
    val title: String,
    val subtitle: String
) {
    STATIC(
        id = "static",
        title = "Predeterminada",
        subtitle = "Estática e instantánea, sin animaciones de texto"
    ),
    TYPEWRITER(
        id = "typewriter",
        title = "A máquina",
        subtitle = "Aparece letra por letra con cursor parpadeante"
    ),
    BLUR_FOCUS(
        id = "blur_focus",
        title = "Enfoque cinemático",
        subtitle = "Línea activa nítida y el resto desenfocadas"
    ),
    KARAOKE_GLOW(
        id = "karaoke_glow",
        title = "Karaoke fluido",
        subtitle = "Barrido continuo de luz horizontal estilo Apple Music"
    );

    companion object {
        fun fromId(id: String?): LyricsAnimationMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: STATIC
    }
}
