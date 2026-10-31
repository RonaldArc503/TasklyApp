package com.ronaldcolocho.taskly.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PlayerColorPolicyTest {
    private fun policy(seed: Int = 53) = PlayerColorPolicy(random = Random(seed))
    private fun palette(id: String) = PLAYER_PALETTES.first { it.id == id }

    @Test fun newInstallStartsWithAutomaticVariety() {
        val state = policy().restore(PlayerColorState(paletteId = "", recentPaletteIds = emptyList()))
        assertEquals(PlayerColorMode.AUTOMATIC, state.mode)
        assertTrue(PLAYER_PALETTES.any { it.id == state.paletteId })
    }

    @Test fun originalOnlyDependsOnMediaIdAcrossRestartsAndPlaybackOrder() {
        val first = policy(1)
        val restarted = policy(999)
        repeat(1_000) { index ->
            val id = "taskly/user/áudio-$index"
            assertEquals(first.original(id), restarted.original(id))
        }
        val original = first.original("favorite")
        var state = first.setMode(first.onTrackChanged(PlayerColorState(), "favorite"), PlayerColorMode.ORIGINAL)
        repeat(30) { state = first.onTrackChanged(state, "other-$it") }
        state = first.onTrackChanged(state, "favorite")
        assertEquals(original.id, state.paletteId)
    }

    @Test fun automaticAvoidsLastTwoDistinctFamiliesAndNearHuesForThousandsOfSongs() {
        val policy = policy()
        var state = PlayerColorState()
        repeat(5_000) { index ->
            val previous = state
            state = policy.onTrackChanged(state, "song-$index")
            val chosen = palette(state.paletteId)
            val families = previous.recentPaletteIds.asReversed().map { palette(it).family }.distinct().take(2)
            assertFalse("Repeated recent family at song $index", chosen.family in families)
            assertFalse("Repeated a palette in recent history", state.paletteId in previous.recentPaletteIds)
            if (previous.trackId != null) assertTrue("Visually similar consecutive hues",
                hueDistance(palette(previous.paletteId).hue, chosen.hue) >= PlayerColorPolicy.MIN_HUE_DISTANCE)
        }
    }

    @Test fun changeColorJumpsFamiliesInsteadOfWalkingAdjacentPurpleShades() {
        val policy = policy()
        var state = PlayerColorState(trackId = "purple-song", paletteId = "mystic_amethyst")
        // Use an actual purple palette, independent of catalog ordering.
        val purple = PLAYER_PALETTES.first { it.family == PlayerColorFamily.PURPLE }
        state = state.copy(paletteId = purple.id, recentPaletteIds = listOf(purple.id))
        repeat(250) {
            val before = state
            state = policy.changeColor(state)
            assertNotEquals(palette(before.paletteId).family, palette(state.paletteId).family)
            assertTrue(hueDistance(palette(before.paletteId).hue, palette(state.paletteId).hue) >= 45f)
            assertEquals("purple-song", state.trackId)
        }
    }

    @Test fun changingColorFromOriginalEnablesAutomaticForFollowingSongs() {
        val policy = policy()
        val original = policy.onTrackChanged(PlayerColorState(mode = PlayerColorMode.ORIGINAL), "song")
        val manual = policy.changeColor(original)
        assertEquals(PlayerColorMode.AUTOMATIC, manual.mode)
        assertNotEquals(palette(original.paletteId).family, palette(manual.paletteId).family)
        val next = policy.onTrackChanged(manual, "next-song")
        assertNotEquals(palette(manual.paletteId).family, palette(next.paletteId).family)
    }

    @Test fun originalRestoresTheSameSongAfterSeveralManualChanges() {
        val policy = policy()
        var state = policy.onTrackChanged(PlayerColorState(), "favorite")
        repeat(12) { state = policy.changeColor(state) }
        state = policy.setMode(state, PlayerColorMode.ORIGINAL)
        assertEquals(policy.original("favorite").id, state.paletteId)
        assertEquals(PlayerColorMode.ORIGINAL, state.mode)
        val restarted = policy(123)
        assertEquals(state, restarted.onTrackChanged(restarted.restore(state), "favorite"))
    }

    @Test fun reopeningOrSeekingTheSameSongDoesNotChooseAnotherColorOrGrowHistory() {
        val policy = policy()
        val saved = policy.onTrackChanged(PlayerColorState(), "playing")
        repeat(50) { assertEquals(saved, policy.onTrackChanged(saved, "playing")) }
        val restored = policy(12).restore(saved)
        assertEquals(saved, policy(12).onTrackChanged(restored, "playing"))
    }

    @Test fun restartingKeepsRecentFamiliesForNextSong() {
        val policy = policy()
        var saved = PlayerColorState()
        repeat(10) { saved = policy.onTrackChanged(saved, "song-$it") }
        val restarted = policy(434)
        val next = restarted.onTrackChanged(restarted.restore(saved), "new-song")
        val excluded = saved.recentPaletteIds.asReversed().map { palette(it).family }.distinct().take(2)
        assertFalse(palette(next.paletteId).family in excluded)
    }

    @Test fun originalModeCanRepeatAFamilyWithoutChangingItsSongsIdentity() {
        val policy = policy()
        var state = PlayerColorState(mode = PlayerColorMode.ORIGINAL)
        repeat(100) {
            val id = "song-$it"
            state = policy.onTrackChanged(state, id)
            assertEquals(policy.original(id).id, state.paletteId)
        }
    }

    @Test fun modeAndColorChangesWithoutAnActiveTrackAreSafe() {
        val policy = policy()
        val original = policy.setMode(PlayerColorState(), PlayerColorMode.ORIGINAL)
        assertEquals(PlayerColorMode.ORIGINAL, original.mode)
        assertEquals(original, policy.changeColor(original))
        assertEquals(original, policy.onTrackChanged(original, "   "))
        val selected = policy.onTrackChanged(original, "song")
        assertEquals(policy.original("song").id, selected.paletteId)
        assertNull(policy.onTrackChanged(selected, null).trackId)
    }

    @Test fun invalidSavedPaletteAndHistoryAreRecovered() {
        val policy = policy()
        val bad = PlayerColorState(trackId = "song", paletteId = "removed-theme",
            recentPaletteIds = listOf("removed-theme") + List(30) { PLAYER_PALETTES[it].id })
        val restored = policy.restore(bad)
        assertTrue(PLAYER_PALETTES.any { it.id == restored.paletteId })
        assertEquals(10, restored.recentPaletteIds.size)
        assertFalse("removed-theme" in restored.recentPaletteIds)
        val original = policy.restore(bad.copy(mode = PlayerColorMode.ORIGINAL))
        assertEquals(policy.original("song").id, original.paletteId)
    }

    @Test fun familiesHaveEqualFirstChoiceChanceEvenWithManyShadesInOneFamily() {
        fun swatch(id: String, rgb: Int) = PlayerPalette(id, id, rgb, rgb, rgb, rgb)
        val palettes = listOf(swatch("red", 0xFF0000), swatch("blue", 0x0000FF)) +
            (1..20).map { swatch("green-$it", 0x00FF00) }
        val policy = PlayerColorPolicy(palettes, Random(38))
        val counts = mutableMapOf<PlayerColorFamily, Int>()
        repeat(6_000) {
            val next = policy.onTrackChanged(PlayerColorState(), "song-$it")
            val family = palettes.first { it.id == next.paletteId }.family
            counts[family] = counts.getOrDefault(family, 0) + 1
        }
        assertEquals(3, counts.size)
        counts.values.forEach { assertTrue("A shade-heavy family dominated selection: $counts", it in 1_700..2_300) }
    }

    @Test fun historyIsBoundedAndRetainsTheVisibleSelection() {
        val policy = policy()
        var state = PlayerColorState()
        repeat(300) { state = policy.onTrackChanged(state, "song-$it") }
        assertEquals(PlayerColorPolicy.HISTORY_SIZE, state.recentPaletteIds.size)
        assertEquals(state.paletteId, state.recentPaletteIds.last())
    }

    @Test fun reducedCatalogsStillSelectSafelyWhenExclusionsCannotAllBeMet() {
        val singleton = PlayerColorPolicy(listOf(PLAYER_PALETTES.first()), Random(1))
        var state = PlayerColorState()
        repeat(20) { state = singleton.onTrackChanged(state, "song-$it") }
        assertEquals(PLAYER_PALETTES.first().id, state.paletteId)
        val small = PlayerColorPolicy(PLAYER_PALETTES.take(2), Random(1))
        state = PlayerColorState()
        repeat(20) { state = small.changeColor(small.onTrackChanged(state, "song-$it")) }
        assertTrue(state.paletteId in PLAYER_PALETTES.take(2).map { it.id })
    }

    @Test fun catalogKeepsSixtyUniquePalettesAndAllEightFamilies() {
        assertEquals(60, PLAYER_PALETTES.size)
        assertEquals(60, PLAYER_PALETTES.map { it.id }.distinct().size)
        assertEquals(PlayerColorFamily.entries.toSet(), PLAYER_PALETTES.map { it.family }.toSet())
        PLAYER_PALETTES.forEach { palette ->
            assertTrue(palette.hue in 0f..<360f)
            listOf(palette.primary, palette.secondary, palette.tertiary, palette.glow).forEach {
                assertTrue(it in 0..0xFFFFFF)
            }
        }
    }

    @Test fun warmRedShadesAreNotMisclassifiedAsOrangeBecauseOfTheirOldPosition() {
        val peach = PLAYER_PALETTES.first { it.name == "Durazno Tropical" }
        assertEquals(PlayerColorFamily.RED, peach.family)
        assertEquals(10f, hueDistance(355f, 5f), 0.001f)
        assertEquals(180f, hueDistance(0f, 180f), 0.001f)
    }
}
