package io.github.krank56.webmote.ui.touchpad

import org.junit.jupiter.api.Test
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The phone's axes are Android's: x to the right of the screen, y to its top, z out of its face. The
 * gravity sensor reports the vector pointing up, so a phone lying face up reads (0, 0, 9.81). The
 * gyroscope reports turning speeds about those axes, counter-clockwise positive (right-hand rule).
 * The TV's pointer moves right with a positive dx and down with a positive dy.
 */
class AimTrackerTest {
    private val moves = mutableListOf<Pair<Double, Double>>()
    private val tracker = AimTracker(move = { dx, dy -> moves += dx to dy })
    private var nanos = 0L

    @Test
    fun `holding the phone still moves nothing`() {
        tracker.start()
        point(gravity = FLAT, turn = STILL)

        assertEquals(emptyList(), moves)
    }

    @Test
    fun `turning right moves the pointer right`() {
        tracker.start()
        // Seen from above, turning right is clockwise: a negative turn about the up-pointing z axis.
        point(gravity = FLAT, turn = Axes(0f, 0f, -1f))

        assertTrue(moves.dx > 0, "dx ${moves.dx}")
        assertEquals(0.0, moves.dy, 1e-9)
    }

    @Test
    fun `turning left moves the pointer left`() {
        tracker.start()
        point(gravity = FLAT, turn = Axes(0f, 0f, 1f))

        assertTrue(moves.dx < 0, "dx ${moves.dx}")
        assertEquals(0.0, moves.dy, 1e-9)
    }

    @Test
    fun `tipping the top up moves the pointer up`() {
        tracker.start()
        // A positive turn about x carries y (the top) towards z, which points up when lying flat.
        point(gravity = FLAT, turn = Axes(1f, 0f, 0f))

        assertTrue(moves.dy < 0, "dy ${moves.dy}")
        assertEquals(0.0, moves.dx, 1e-9)
    }

    @Test
    fun `tipping the top down moves the pointer down`() {
        tracker.start()
        point(gravity = FLAT, turn = Axes(-1f, 0f, 0f))

        assertTrue(moves.dy > 0, "dy ${moves.dy}")
        assertEquals(0.0, moves.dx, 1e-9)
    }

    @Test
    fun `turning right moves the pointer as far whether the phone is flat, tilted or upright`() {
        // The same turn right about the room's vertical, which each grip sees about different axes.
        val flat = gesture { point(gravity = FLAT, turn = Axes(0f, 0f, -1f)) }
        val tilted = gesture { point(gravity = TILTED, turn = Axes(0f, -SIN_45, -SIN_45)) }
        val upright = gesture { point(gravity = UPRIGHT, turn = Axes(0f, -1f, 0f)) }

        assertTrue(flat.dx > 0, "dx ${flat.dx}")
        for (other in listOf(tilted, upright)) {
            assertEquals(flat.dx, other.dx, flat.dx * 0.01)
            assertEquals(0.0, other.dy, 1e-3)
        }
    }

    @Test
    fun `tipping the top up moves the pointer as far whether the phone is flat, rolled or upright`() {
        // Tipping up turns about the room's horizontal left-right axis. Rolled 30° to the right, the
        // phone's x axis dips below it: that axis is cos 30° along x and sin 30° along z.
        val flat = gesture { point(gravity = FLAT, turn = Axes(1f, 0f, 0f)) }
        val rolled = gesture { point(gravity = ROLLED, turn = Axes(COS_30, 0f, 0.5f)) }
        // Upright, its back faces the TV: tipping the top towards me raises its aim, the same turn about x.
        val upright = gesture { point(gravity = UPRIGHT, turn = Axes(1f, 0f, 0f)) }

        assertTrue(flat.dy < 0, "dy ${flat.dy}")
        for (other in listOf(rolled, upright)) {
            assertEquals(flat.dy, other.dy, -flat.dy * 0.01)
            assertEquals(0.0, other.dx, 1e-3)
        }
    }

    @Test
    fun `held on its side, turning right still moves the pointer right`() {
        tracker.start()
        // Its right edge up: up is x, and turning right is a negative turn about x.
        point(gravity = Axes(G, 0f, 0f), turn = Axes(-1f, 0f, 0f))

        assertTrue(moves.dx > 0, "dx ${moves.dx}")
        assertEquals(0.0, moves.dy, 1e-9)
    }

    @Test
    fun `a small tremor while holding still barely moves the pointer`() {
        // Two seconds of a steady hand's jitter: up to 3°/s either way about each axis, at random.
        val random = Random(56)
        val jitter = Math.toRadians(3.0).toFloat()
        fun wobble() = (random.nextFloat() * 2 - 1) * jitter
        val tremor = gesture {
            tracker.onGravity(FLAT.x, FLAT.y, FLAT.z)
            repeat(100) { tracker.onGyroscope(wobble(), wobble(), wobble(), next()) }
        }

        // How far the pointer travels back and forth, not just where it ends up.
        val travel = tremor.sumOf { (dx, dy) -> hypot(dx, dy) }
        assertTrue(travel < 2, "travel $travel")
    }

    @Test
    fun `the pointer stops soon after the phone does`() {
        val afterStopping = gesture {
            point(gravity = FLAT, turn = Axes(0f, 0f, -2f), millis = 300)
            turn(STILL, millis = 150)
            moves.clear()
            turn(STILL, millis = 500)
        }

        assertEquals(emptyList(), afterStopping)
    }

    @Test
    fun `turning slower than a degree per second moves nothing`() {
        // Half a degree per second right and as much up: a drift well within a steady hand's tremor.
        val slow = Math.toRadians(0.5).toFloat()
        tracker.start()
        point(gravity = FLAT, turn = Axes(slow, 0f, -slow), millis = 2000)

        assertEquals(emptyList(), moves)
    }

    @Test
    fun `a quick turn moves the pointer farther than a slow one through the same angle`() {
        // 20° to the right, over a second and over a fifth of a second.
        val angle = Math.toRadians(20.0)
        val slow = gesture {
            point(gravity = FLAT, turn = Axes(0f, 0f, -angle.toFloat()), millis = 1000)
            turn(STILL, millis = 500)
        }
        val quick = gesture {
            point(gravity = FLAT, turn = Axes(0f, 0f, -(angle * 5).toFloat()), millis = 200)
            turn(STILL, millis = 500)
        }

        assertTrue(slow.dx > 0, "slow dx ${slow.dx}")
        assertTrue(quick.dx > 1.5 * slow.dx, "quick dx ${quick.dx}, slow dx ${slow.dx}")
    }

    @Test
    fun `doubling the pointing speed doubles how far the pointer moves`() {
        val rightAndUp = {
            point(gravity = FLAT, turn = Axes(0.3f, 0f, -0.6f))
            turn(STILL, millis = 500)
        }
        val normal = gesture(rightAndUp)
        tracker.speed = 2.0
        val doubled = gesture(rightAndUp)

        assertTrue(normal.dx > 0 && normal.dy < 0, "normal ${normal.dx}, ${normal.dy}")
        assertEquals(2 * normal.dx, doubled.dx, normal.dx * 0.01)
        assertEquals(2 * normal.dy, doubled.dy, -normal.dy * 0.01)
    }

    @Test
    fun `the first sample moves nothing, however fast the turn`() {
        // Sensor timestamps count from boot, so the first one is no measure of how long it turned.
        nanos = 3_600_000_000_000
        tracker.start()
        point(gravity = FLAT, turn = Axes(3f, 0f, -3f), millis = STEP_MS)

        assertEquals(emptyList(), moves)
    }

    @Test
    fun `a reading no newer than the last one is skipped without a jump`() {
        val steady = gesture { point(gravity = FLAT, turn = RIGHT, millis = 400) }
        val withStale = gesture {
            point(gravity = FLAT, turn = RIGHT, millis = 200)
            // A late reading and a repeated one, turning hard the other way.
            tracker.onGyroscope(0f, 0f, 5f, nanos - 50_000_000)
            tracker.onGyroscope(0f, 0f, 5f, nanos)
            point(gravity = FLAT, turn = RIGHT, millis = 200)
        }

        assertEquals(steady, withStale)
    }

    @Test
    fun `after a long gap between readings it carries on as if pointing had just started`() {
        val beforeGap = gesture { point(gravity = FLAT, turn = LEFT, millis = 300) }
        val withGap = gesture {
            point(gravity = FLAT, turn = LEFT, millis = 300)
            // Half a second without a reading, then the phone turns the other way.
            nanos += 500_000_000
            point(gravity = FLAT, turn = RIGHT, millis = 300)
        }
        val fresh = gesture { point(gravity = FLAT, turn = RIGHT, millis = 300) }

        assertEquals(fresh, withGap.drop(beforeGap.size))
    }

    @Test
    fun `broken readings are ignored`() {
        val clean = gesture {
            point(gravity = FLAT, turn = RIGHT, millis = 200)
            next()
            turn(RIGHT, millis = 200)
        }
        val broken = gesture {
            point(gravity = FLAT, turn = RIGHT, millis = 200)
            tracker.onGyroscope(Float.NaN, 0f, -1f, next())
            tracker.onGravity(0f, Float.POSITIVE_INFINITY, G)
            tracker.onGravity(0f, 0f, 0f)
            turn(RIGHT, millis = 200)
        }

        assertEquals(clean, broken)
    }

    @Test
    fun `after stopping and starting again it moves exactly as a new one would`() {
        tracker.start()
        point(gravity = UPRIGHT, turn = Axes(1f, -1f, 0f))
        tracker.stop()
        moves.clear()

        val newMoves = mutableListOf<Pair<Double, Double>>()
        val newTracker = AimTracker(move = { dx, dy -> newMoves += dx to dy })
        val start = nanos
        for (each in listOf(tracker, newTracker)) {
            each.start()
            nanos = start
            // Lying flat, turning right and up while rolling about its top (y): first before this
            // grip's gravity reading arrives, when there's nothing to aim by yet, then after it.
            repeat(3) { each.onGyroscope(1f, -1f, -1f, next()) }
            each.onGravity(FLAT.x, FLAT.y, FLAT.z)
            repeat(10) { each.onGyroscope(1f, -1f, -1f, next()) }
        }

        assertTrue(newMoves.isNotEmpty())
        assertEquals(newMoves, moves)
    }

    @Test
    fun `samples arriving after stopping move nothing`() {
        tracker.start()
        point(gravity = FLAT, turn = Axes(0f, 0f, -1f))
        tracker.stop()
        moves.clear()

        point(gravity = FLAT, turn = Axes(0f, 0f, -1f))

        assertEquals(emptyList(), moves)
    }

    private val List<Pair<Double, Double>>.dx get() = sumOf { it.first }
    private val List<Pair<Double, Double>>.dy get() = sumOf { it.second }

    /** The moves of one pointing gesture from a fresh start, while [feed] feeds the tracker. */
    private fun gesture(feed: () -> Unit): List<Pair<Double, Double>> {
        moves.clear()
        tracker.start()
        feed()
        tracker.stop()
        return moves.toList()
    }

    /** Holds the phone as [gravity] says and turns it at [turn] (rad/s about x, y, z) for [millis], at the game rate. */
    private fun point(gravity: Axes, turn: Axes, millis: Int = 500) {
        tracker.onGravity(gravity.x, gravity.y, gravity.z)
        turn(turn, millis)
    }

    /** Turns the phone at [turn] for [millis] without a new gravity reading. */
    private fun turn(turn: Axes, millis: Int) {
        repeat(millis / STEP_MS) { tracker.onGyroscope(turn.x, turn.y, turn.z, next()) }
    }

    /** The next sample's timestamp, one game-rate step on. */
    private fun next(): Long {
        nanos += STEP_MS * 1_000_000L
        return nanos
    }

    private data class Axes(val x: Float, val y: Float, val z: Float)

    private companion object {
        const val STEP_MS = 20
        const val G = 9.81f
        const val SIN_45 = 0.70710677f
        const val COS_30 = 0.8660254f

        val STILL = Axes(0f, 0f, 0f)

        /** Turning right at 1 rad/s while lying flat. */
        val RIGHT = Axes(0f, 0f, -1f)

        /** Turning left at 1 rad/s while lying flat. */
        val LEFT = Axes(0f, 0f, 1f)

        /** Lying face up, its top towards the TV. */
        val FLAT = Axes(0f, 0f, G)

        /** Its top raised 45° from flat: up is halfway between its top (y) and its face (z). */
        val TILTED = Axes(0f, G * SIN_45, G * SIN_45)

        /** Flat, then rolled 30° so its right edge dips: up leans towards its left edge (−x). */
        val ROLLED = Axes(-G * 0.5f, 0f, G * COS_30)

        /** Standing up, its face towards me and its back towards the TV. */
        val UPRIGHT = Axes(0f, G, 0f)
    }
}
