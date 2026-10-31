package com.ronaldcolocho.taskly.audio

import kotlin.math.abs
import kotlin.random.Random

enum class PlayerColorMode { AUTOMATIC, ORIGINAL }

enum class PlayerColorFamily(val label: String) {
    RED("Rojo"), ORANGE("Naranja"), GOLD("Dorado"), GREEN("Verde"),
    CYAN("Turquesa"), BLUE("Azul"), PURPLE("Morado"), PINK("Rosa")
}

data class PlayerPalette(
    val id: String, val name: String, val primary: Int, val secondary: Int, val tertiary: Int, val glow: Int
) {
    val hue: Float = paletteHue(primary)
    val family: PlayerColorFamily = when {
        hue < 15 || hue >= 345 -> PlayerColorFamily.RED
        hue < 45 -> PlayerColorFamily.ORANGE
        hue < 75 -> PlayerColorFamily.GOLD
        hue < 165 -> PlayerColorFamily.GREEN
        hue < 195 -> PlayerColorFamily.CYAN
        hue < 255 -> PlayerColorFamily.BLUE
        hue < 315 -> PlayerColorFamily.PURPLE
        else -> PlayerColorFamily.PINK
    }
}

data class PlayerColorState(
    val mode: PlayerColorMode = PlayerColorMode.AUTOMATIC,
    val trackId: String? = null,
    val paletteId: String = "electric_cyan",
    val recentPaletteIds: List<String> = emptyList()
)

/** Pure selection rules, shared by playback and every player surface. */
class PlayerColorPolicy(
    private val palettes: List<PlayerPalette> = PLAYER_PALETTES,
    private val random: Random = Random.Default
) {
    init {
        require(palettes.isNotEmpty())
        require(palettes.map { it.id }.distinct().size == palettes.size)
    }

    private val byId = palettes.associateBy { it.id }

    fun original(trackId: String?): PlayerPalette {
        // Only the media ID defines identity. Titles, URLs and playback order may change.
        var hash = -3750763034362895579L
        for (character in trackId.orEmpty()) hash = (hash xor character.code.toLong()) * 1099511628211L
        return palettes[((hash ushr 1) % palettes.size).toInt()]
    }

    fun restore(saved: PlayerColorState): PlayerColorState {
        val track = saved.trackId?.takeIf { it.isNotBlank() }
        val history = saved.recentPaletteIds.filter { it in byId }.takeLast(HISTORY_SIZE)
        val paletteId = when {
            saved.mode == PlayerColorMode.ORIGINAL && track != null -> original(track).id
            saved.paletteId in byId -> saved.paletteId
            else -> palettes.first().id
        }
        return saved.copy(trackId = track, paletteId = paletteId, recentPaletteIds = history)
    }

    fun onTrackChanged(state: PlayerColorState, trackId: String?): PlayerColorState {
        val track = trackId?.takeIf { it.isNotBlank() }
        if (track == state.trackId) return state
        if (track == null) return state.copy(trackId = null)
        val palette = if (state.mode == PlayerColorMode.ORIGINAL) original(track) else chooseDifferent(state)
        return remember(state.copy(trackId = track), palette)
    }

    fun setMode(state: PlayerColorState, mode: PlayerColorMode): PlayerColorState {
        if (state.mode == mode) return state
        val changed = state.copy(mode = mode)
        if (state.trackId == null) return changed
        return remember(changed, if (mode == PlayerColorMode.ORIGINAL) original(state.trackId) else chooseDifferent(state))
    }

    fun changeColor(state: PlayerColorState): PlayerColorState {
        if (state.trackId == null) return state
        // Manual changes resume automatic variety for subsequent songs.
        return remember(state.copy(mode = PlayerColorMode.AUTOMATIC), chooseDifferent(state))
    }

    private fun remember(state: PlayerColorState, palette: PlayerPalette): PlayerColorState {
        val history = if (state.recentPaletteIds.lastOrNull() == palette.id) state.recentPaletteIds
            else (state.recentPaletteIds + palette.id).takeLast(HISTORY_SIZE)
        return state.copy(paletteId = palette.id, recentPaletteIds = history)
    }

    private fun chooseDifferent(state: PlayerColorState): PlayerPalette {
        val current = byId[state.paletteId]?.takeIf { state.trackId != null || state.recentPaletteIds.isNotEmpty() }
        val recentFamilies = state.recentPaletteIds.asReversed().mapNotNull { byId[it]?.family }
            .distinct().take(2).toSet() + listOfNotNull(current?.family)
        val recentIds = state.recentPaletteIds.toSet()
        fun distant(palette: PlayerPalette) = current == null || hueDistance(current.hue, palette.hue) >= MIN_HUE_DISTANCE
        // Relax history first for small catalogs; never reject every available palette.
        val candidates = palettes.filter { it.family !in recentFamilies && it.id !in recentIds && distant(it) }
            .ifEmpty { palettes.filter { it.family !in recentFamilies && distant(it) } }
            .ifEmpty { palettes.filter { it.family != current?.family && distant(it) } }
            .ifEmpty { palettes.filter { it.family != current?.family } }
            .ifEmpty { palettes.filter { it.id != current?.id } }
            .ifEmpty { palettes }
        // Choose a family first: having more shades must not make a family more frequent.
        val families = candidates.groupBy { it.family }.values.toList()
        val family = families[random.nextInt(families.size)]
        return family[random.nextInt(family.size)]
    }

    companion object {
        const val HISTORY_SIZE = 10
        const val MIN_HUE_DISTANCE = 45f
    }
}

internal fun hueDistance(first: Float, second: Float): Float {
    val difference = abs(first - second) % 360f
    return minOf(difference, 360f - difference)
}

private fun paletteHue(rgb: Int): Float {
    val red = ((rgb shr 16) and 255) / 255f
    val green = ((rgb shr 8) and 255) / 255f
    val blue = (rgb and 255) / 255f
    val max = maxOf(red, green, blue)
    val delta = max - minOf(red, green, blue)
    if (delta == 0f) return 0f
    val hue = when (max) {
        red -> 60f * ((green - blue) / delta)
        green -> 60f * ((blue - red) / delta + 2)
        else -> 60f * ((red - green) / delta + 4)
    }
    return (hue + 360f) % 360f
}
