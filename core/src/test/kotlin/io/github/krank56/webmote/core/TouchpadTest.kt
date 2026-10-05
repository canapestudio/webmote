package io.github.krank56.webmote.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TouchpadTest {
    @TempDir lateinit var dir: File
    private val h by lazy { Harness(dir) }

    @AfterEach fun tearDown() = h.close()

    @Test
    fun `dragging moves the pointer with whole-number deltas`() {
        pairWithPointer()

        h.session.movePointer(3.0, -2.0)
        h.session.movePointer(-10.4, 7.6)

        h.eventually { h.tv.pointerMessages.size == 2 }
        assertEquals(listOf(move(3, -2), move(-10, 8)), h.tv.pointerMessages)
    }

    @Test
    fun `slow drags still move the pointer, the fractions adding up`() {
        pairWithPointer()

        repeat(10) { h.session.movePointer(0.3, -0.2) }

        h.eventually { h.tv.pointerMessages.isNotEmpty() }
        h.settle()
        val moves = h.tv.pointerMessages.map(::parse)
        assertTrue(moves.all { it["type"] == "move" && it["down"] == "0" }, "messages ${h.tv.pointerMessages}")
        assertEquals(3, moves.sumOf { it.getValue("dx").toInt() })
        assertEquals(-2, moves.sumOf { it.getValue("dy").toInt() })
    }

    @Test
    fun `movements too small to send are not sent as empty moves`() {
        pairWithPointer()

        h.session.movePointer(0.2, 0.1)
        h.session.click()

        h.eventually { h.tv.pointerMessages.isNotEmpty() }
        h.settle()
        assertEquals(listOf("type:click\n\n"), h.tv.pointerMessages)
    }

    @Test
    fun `tapping clicks`() {
        pairWithPointer()

        h.session.click()

        h.eventually { h.tv.pointerMessages.isNotEmpty() }
        assertEquals(listOf("type:click\n\n"), h.tv.pointerMessages)
    }

    @Test
    fun `two-finger drags scroll`() {
        pairWithPointer()

        h.session.scroll(0.0, 5.0)
        h.session.scroll(-1.6, -4.0)

        h.eventually { h.tv.pointerMessages.size == 2 }
        assertEquals(listOf("type:scroll\ndx:0\ndy:5\n\n", "type:scroll\ndx:-2\ndy:-4\n\n"), h.tv.pointerMessages)
    }

    @Test
    fun `moves, clicks, scrolls and buttons reach the TV in the order they were made`() {
        pairWithPointer()

        h.session.movePointer(4.0, 4.0)
        h.session.click()
        h.session.scroll(0.0, -3.0)
        h.session.press(RemoteButton.Back)
        h.session.movePointer(-1.0, 0.0)

        h.eventually { h.tv.pointerMessages.size == 5 }
        assertEquals(
            listOf(move(4, 4), "type:click\n\n", "type:scroll\ndx:0\ndy:-3\n\n", "type:button\nname:BACK\n\n", move(-1, 0)),
            h.tv.pointerMessages,
        )
    }

    @Test
    fun `nonsense deltas are ignored`() {
        pairWithPointer()

        h.session.movePointer(Double.NaN, 1.0)
        h.session.scroll(Double.POSITIVE_INFINITY, 0.0)
        h.session.movePointer(2.0, 0.0)

        h.eventually { h.tv.pointerMessages.isNotEmpty() }
        h.settle()
        assertEquals(listOf(move(2, 0)), h.tv.pointerMessages)
        assertEquals(ConnectionState.Connected, h.state.connection)
    }

    private fun pairWithPointer() {
        h.pair()
        h.eventually { h.state.capabilities.pointer == Capability.Available }
    }

    private fun move(dx: Int, dy: Int) = "type:move\ndx:$dx\ndy:$dy\ndown:0\n\n"

    /** Parses a pointer message's `key:value` lines; checks it ends with the blank line that terminates it. */
    private fun parse(message: String): Map<String, String> {
        assertTrue(message.endsWith("\n\n"), "unterminated message $message")
        return message.trimEnd('\n').lines().associate { line -> line.substringBefore(':') to line.substringAfter(':') }
    }
}
