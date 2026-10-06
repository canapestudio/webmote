package io.github.krank56.webmote.ui.touchpad

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Turns the phone's rotation into pointer moves, like a Magic Remote: turning the phone left or right
 * about the room's vertical moves the pointer left or right, and tipping its top up or down moves it
 * up or down, however steeply the phone is held.
 *
 * Between [start] and [stop], feed it the gyroscope's and the gravity sensor's readings, in the
 * phone's axes: x to the right of the screen, y to its top, z out of its face. It calls [move] with
 * fractional deltas in the TV pointer's units, which the TV session adds up into whole moves: a
 * positive dx moves right and a positive dy moves down.
 *
 * A hand's tremor is ignored and jitter lightly smoothed out; slow turns move the pointer little for
 * precise aiming and quick ones move it far, like mouse acceleration. The constants that set this
 * feel are starting points, to be tuned by hand on a real phone and TV.
 */
class AimTracker(private val move: (dx: Double, dy: Double) -> Unit) {
    /** The user's Pointing speed: multiplies how far the pointer moves for a given turn. */
    var speed = 1.0

    private var pointing = false

    /** Which way is up in the phone's axes, as a unit vector; null until this grip's first gravity reading. */
    private var up: Axes? = null
    private var lastNanos: Long? = null

    /** The smoothed turning speeds, in rad/s: about the vertical (left positive) and tipping (up positive). */
    private var smoothYaw = 0.0
    private var smoothPitch = 0.0

    /** Starts pointing afresh: nothing from an earlier pointing carries over. */
    fun start() {
        reset()
        pointing = true
    }

    /** Stops pointing; readings that still arrive are ignored. */
    fun stop() {
        reset()
        pointing = false
    }

    /** The gravity sensor's reading, which points up: a phone lying face up reads (0, 0, 9.81). */
    fun onGravity(x: Float, y: Float, z: Float) {
        val length = sqrt(x.toDouble() * x + y.toDouble() * y + z.toDouble() * z)
        // A broken reading, or none at all, keeps the last one.
        if (!pointing || !length.isFinite() || length == 0.0) return
        up = Axes(x / length, y / length, z / length)
    }

    /**
     * The gyroscope's reading: turning speeds in rad/s about the phone's x, y and z axes,
     * counter-clockwise positive, taken at [timestampNanos] (the sensor event's timestamp).
     */
    fun onGyroscope(x: Float, y: Float, z: Float, timestampNanos: Long) {
        if (!pointing || !x.isFinite() || !y.isFinite() || !z.isFinite()) return
        val previous = lastNanos
        // A reading no newer than the last one says nothing about how far the phone turned since.
        if (previous != null && timestampNanos <= previous) return
        lastNanos = timestampNanos
        val up = up ?: return
        // The first reading only starts the clock: it doesn't say for how long the phone turned. Nor
        // does one after a long gap, which starts afresh.
        if (previous == null || timestampNanos - previous > MAX_GAP_NANOS) {
            smoothYaw = 0.0
            smoothPitch = 0.0
            return
        }
        val seconds = (timestampNanos - previous) / 1e9

        // Turning about the room's vertical: counter-clockwise seen from above (to the left) is positive.
        // Lying flat, up is z, and turning right is a negative turn about z: a negative yaw, so dx
        // (-yaw) is positive, to the right. Held upright, up is y and the same holds about y.
        val yaw = x * up.x + y * up.y + z * up.z
        // Tipping about the phone's left-right axis made horizontal (x minus its part along up): a
        // positive turn raises the phone's top, or its back when it's held upright. Lying flat,
        // tipping the top up is a positive turn about x (carrying y towards z): a positive pitch, so
        // dy (-pitch) is negative, up on the TV. As the phone turns onto its side that axis shrinks
        // to nothing, and tipping fades out with it.
        val side = sqrt(1 - up.x * up.x)
        val pitch = (x * (1 - up.x * up.x) - y * up.x * up.y - z * up.x * up.z) / max(side, MIN_SIDE)

        // Light exponential smoothing, by elapsed time so that it's the same at any sensor rate.
        val keep = exp(-seconds / SMOOTHING)
        smoothYaw = yaw + (smoothYaw - yaw) * keep
        smoothPitch = pitch + (smoothPitch - pitch) * keep

        // The dead zone is taken off rather than cut out, so movement grows from nothing past it.
        val turning = hypot(smoothYaw, smoothPitch)
        val beyond = turning - DEAD_ZONE
        if (beyond <= 0) return
        val scale = beyond / turning * gainAt(beyond) * speed * seconds
        move(-smoothYaw * scale, -smoothPitch * scale)
    }

    /**
     * Pointer units per radian when turning [beyond] rad/s past the dead zone, like mouse
     * acceleration: [SLOW_GAIN] up to [SLOW_TURN], rising evenly to [FAST_GAIN] at [FAST_TURN] and beyond.
     */
    private fun gainAt(beyond: Double): Double {
        val fastness = ((beyond - SLOW_TURN) / (FAST_TURN - SLOW_TURN)).coerceIn(0.0, 1.0)
        return SLOW_GAIN + (FAST_GAIN - SLOW_GAIN) * fastness
    }

    private fun reset() {
        up = null
        lastNanos = null
        smoothYaw = 0.0
        smoothPitch = 0.0
    }

    private class Axes(val x: Double, val y: Double, val z: Double)

    private companion object {
        /** A longer gap between gyroscope readings than this (100 ms, five at the game rate) starts afresh. */
        const val MAX_GAP_NANOS = 100_000_000L

        /**
         * The smoothing's time constant, in seconds: a change in turning speed is two-thirds through
         * after 25 ms. Longer steadies the pointer more but makes it lag.
         */
        const val SMOOTHING = 0.025

        /**
         * Turning speeds below this, in rad/s (2°/s), are a steady hand's tremor and move nothing;
         * faster ones have it taken off.
         */
        const val DEAD_ZONE = 0.035

        /** Pointer units per radian for slow, precise aiming: 600 is about 10 per degree. */
        const val SLOW_GAIN = 600.0

        /**
         * Pointer units per radian for quick turns: at 3500 (about 61 per degree), a 35° flick in a
         * third of a second moves about 1850, across a 1920-wide screen if the TV counts in pixels.
         */
        const val FAST_GAIN = 3500.0

        /** Up to this turning speed past the dead zone, in rad/s (about 6°/s), the gain stays at [SLOW_GAIN]. */
        const val SLOW_TURN = 0.1

        /** From this turning speed past the dead zone, in rad/s (about 86°/s), the gain is [FAST_GAIN]. */
        const val FAST_TURN = 1.5

        /**
         * The left-right axis's horizontal length below which tipping fades out: rolled more than
         * 60°, the phone is nearly on its side and its tip no longer says where it aims.
         */
        const val MIN_SIDE = 0.5
    }
}
