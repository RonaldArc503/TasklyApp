package com.ronaldcolocho.taskly.ui.screen.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MareaV2MotionTest {
    @Test fun autonomousCurrentKeepsMovingSmoothlyWithoutSensorsForTenMinutes() {
        var previousX = MareaV2Current.x(0f)
        var previousY = MareaV2Current.y(0f)
        repeat(60) { window ->
            var distance = 0f
            repeat(600) { frame ->
                val t = (window * 600 + frame + 1) / 60f
                val x = MareaV2Current.x(t)
                val y = MareaV2Current.y(t)
                val step = abs(x - previousX) + abs(y - previousY)
                assertTrue("No visible frame jumps", step < 0.001f)
                assertTrue(abs(x) <= 0.0651f && abs(y) <= 0.0511f)
                distance += step
                previousX = x
                previousY = y
            }
            assertTrue("Current must not stall in a ten-second window", distance > 0.005f)
        }
    }

    @Test fun stationaryPhoneDoesNotGenerateArtificialShake() {
        val motion = MareaV2Motion()
        repeat(600) {
            motion.onSensor(0f, 0f, 9.81f, 1_000_000_000L + it * 16_666_667L)
            motion.advance(1f / 60f)
        }
        assertEquals(0f, motion.x, 0.00001f)
        assertEquals(0f, motion.y, 0.00001f)
        assertEquals(0f, motion.energy, 0.00001f)
    }

    @Test fun tiltMovesProgressivelyWithoutOvershoot() {
        val motion = MareaV2Motion()
        motion.onSensor(-9.81f, 0f, 0f, 1_000_000_000L)
        assertEquals(0f, motion.x, 0f)
        var previous = 0f
        repeat(300) {
            motion.advance(1f / 60f)
            assertTrue(motion.x >= previous)
            assertTrue(motion.x <= 1f)
            assertTrue(motion.x - previous < 0.02f)
            previous = motion.x
        }
        assertEquals(1f, motion.x, 0.001f)
    }

    @Test fun responseDoesNotDependOnDisplayRefreshRate() {
        fun simulate(fps: Int): MareaV2Motion {
            val motion = MareaV2Motion()
            motion.onSensor(0f, 0f, 9.81f, 1_000_000_000L)
            motion.onSensor(-15f, 4f, 9.81f, 1_060_000_000L)
            repeat(fps * 2) { motion.advance(1f / fps) }
            return motion
        }
        val reference = simulate(60)
        for (fps in listOf(30, 90, 120)) {
            val actual = simulate(fps)
            assertEquals(reference.x, actual.x, 0.0001f)
            assertEquals(reference.y, actual.y, 0.0001f)
            assertEquals(reference.energy, actual.energy, 0.0001f)
        }
    }

    @Test fun rapidMovementHasSmoothOnsetAndSettlesBackToRest() {
        val motion = MareaV2Motion()
        motion.onSensor(0f, 0f, 9.81f, 1_000_000_000L)
        motion.onSensor(18f, -12f, 4f, 1_060_000_000L)
        assertEquals(0f, motion.energy, 0f)
        motion.advance(1f / 60f)
        assertTrue(motion.energy in 0.001f..0.06f)
        var peak = motion.energy
        repeat(720) {
            motion.onSensor(0f, 0f, 9.81f, 1_080_000_000L + it * 16_666_667L)
            motion.advance(1f / 60f)
            peak = maxOf(peak, motion.energy)
            assertTrue(motion.energy in 0f..1f)
        }
        assertTrue(peak > 0.2f)
        assertTrue(motion.energy < 0.001f)
        assertTrue(abs(motion.x) < 0.001f)
        assertTrue(abs(motion.y) < 0.001f)
    }
}
