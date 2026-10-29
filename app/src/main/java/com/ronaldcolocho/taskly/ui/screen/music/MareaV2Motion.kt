package com.ronaldcolocho.taskly.ui.screen.music

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.cos

/** Fraction of vessel radius; always active, independently of sensor availability. */
internal object MareaV2Current {
    fun x(t: Float): Float = sin(t * 0.237f + 0.55f * sin(t * 0.071f)) * 0.044f +
        cos(t * 0.113f + 1.7f) * 0.021f

    fun y(t: Float): Float = cos(t * 0.193f + 0.43f * sin(t * 0.089f)) * 0.033f +
        sin(t * 0.131f + 0.8f) * 0.018f
}

/** Sensor input is only an additive force; autonomous motion lives in the V2 geometry. */
internal class MareaV2Motion {
    var x = 0f
        private set
    var y = 0f
        private set
    var energy = 0f
        private set
    private var targetX = 0f
    private var targetY = 0f
    private var velocityX = 0f
    private var velocityY = 0f
    private var impulse = 0f
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var lastSensorNanos = 0L

    fun onSensor(ax: Float, ay: Float, az: Float, timestamp: Long) {
        if (!ax.isFinite() || !ay.isFinite() || !az.isFinite()) return
        if (lastSensorNanos == 0L) {
            gravityX = ax
            gravityY = ay
            gravityZ = az
        }
        val dt = ((timestamp - lastSensorNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
        lastSensorNanos = timestamp
        val blend = 1f - exp(-dt / 0.32f)
        gravityX += (ax - gravityX) * blend
        gravityY += (ay - gravityY) * blend
        gravityZ += (az - gravityZ) * blend
        targetX = (-gravityX / 9.81f).coerceIn(-1f, 1f)
        targetY = (gravityY / 9.81f).coerceIn(-1f, 1f)
        // Ignore sensor noise. A fast movement adds bounded energy, never a position jump.
        val acceleration = abs(ax - gravityX) + abs(ay - gravityY) + abs(az - gravityZ)
        impulse = max(impulse, ((acceleration - 1.2f) / 12f).coerceIn(0f, 1f))
    }

    fun advance(deltaSeconds: Float) {
        val dt = deltaSeconds.coerceIn(0f, 0.05f)
        // Exact critically damped spring for a constant target during this frame.
        // Preserved velocity gives weight; critical damping prevents fast bouncing.
        val rate = 2.4f
        val decay = exp(-rate * dt)
        val dx = x - targetX
        val dy = y - targetY
        val jx = velocityX + rate * dx
        val jy = velocityY + rate * dy
        x = targetX + (dx + jx * dt) * decay
        y = targetY + (dy + jy * dt) * decay
        velocityX = (velocityX - rate * jx * dt) * decay
        velocityY = (velocityY - rate * jy * dt) * decay
        // Exact cascade: quick, smooth onset followed by a several-second viscous tail.
        val impulseDecay = exp(-dt / 1.4f)
        val responseDecay = exp(-dt / 0.35f)
        energy = energy * responseDecay + impulse *
            (impulseDecay - responseDecay) / (1f - 0.35f / 1.4f)
        impulse *= impulseDecay
    }

    fun reset() {
        x = 0f
        y = 0f
        energy = 0f
        targetX = 0f
        targetY = 0f
        velocityX = 0f
        velocityY = 0f
        impulse = 0f
        lastSensorNanos = 0L
    }
}
