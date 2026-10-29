package com.ronaldcolocho.taskly.audio

/**
 * Representa los diferentes estilos de animación visual para el disco del reproductor de música.
 * Cada estilo reacciona de forma dinámica a la paleta de colores de la canción actual.
 */
enum class PlayerVisualEffect(
    val id: String,
    val title: String,
    val subtitle: String
) {
    RANDOM(
        id = "random",
        title = "Aleatorio",
        subtitle = "Cambia de estilo automáticamente en cada canción"
    ),
    MAREA(
        id = "marea",
        title = "Marea Líquida",
        subtitle = "Ondas armónicas fluidas y respiración marina"
    ),
    MAREA_V2(
        id = "marea_v2",
        title = "Marea v2 (Fluido Vivo)",
        subtitle = "Física de gravedad interactiva, burbujas y oleaje zen"
    ),
    GALAXY(
        id = "galaxy",
        title = "Galaxia Cósmica",
        subtitle = "Espiral estelar con brazos nebulares en rotación"
    ),
    VORTEX(
        id = "vortex",
        title = "Vórtice Cuántico",
        subtitle = "Remolino gravitacional hipnótico con distorsión centrípeta"
    ),
    BLACK_HOLE(
        id = "black_hole",
        title = "Agujero Negro",
        subtitle = "Gargantua: lente gravitacional y disco de acreción relativista"
    ),
    SUPERNOVA(
        id = "supernova",
        title = "Supernova Solar",
        subtitle = "Bucles magnéticos incandescentes y eyección coronal viva"
    ),
    PLASMA_STORM(
        id = "plasma_storm",
        title = "Tormenta de Plasma",
        subtitle = "Arcos voltaicos y rayos fractales de alta energía"
    ),
    PRISM_FRACTAL(
        id = "prism_fractal",
        title = "Prisma Fractal",
        subtitle = "Refracción cristalina, facetas angulares y luz dispersa"
    );

    companion object {
        fun fromId(id: String?): PlayerVisualEffect =
            entries.find { it.id == id } ?: MAREA
    }
}
