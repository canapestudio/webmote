package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.faketv.FakeTv
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OffStateTest {
    @TempDir lateinit var dir: File
    private val h by lazy { Harness(dir) }

    @AfterEach fun tearDown() = h.close()

    @Test
    fun `off polling connects as soon as the TV answers, without reporting connecting while it's off`() {
        h.pairThenTurnTvOff()

        ConnectionRecorder(h.session).use { recorder ->
            h.session.startOffPolling()
            pollWhileOff()
            assertEquals(ConnectionState.Off, h.state.connection)
            assertFalse(ConnectionState.Connecting in recorder.states, "states: ${recorder.states}")

            h.tv.powerOn()
            h.advance(h.pollInterval)
            h.awaitConnection(ConnectionState.Connected)
        }
    }

    @Test
    fun `stopping off polling stops probing`() {
        h.pairThenTurnTvOff()
        h.session.startOffPolling()
        pollWhileOff()

        h.session.stopOffPolling()
        h.tv.powerOn()
        pollWhileOff()

        assertEquals(ConnectionState.Off, h.state.connection)
        assertEquals(1, h.tv.registrations)
    }

    @Test
    fun `starting off polling twice still stops with one stop`() {
        h.pairThenTurnTvOff()

        h.session.startOffPolling()
        h.session.startOffPolling()
        h.settle()
        h.session.stopOffPolling()
        h.tv.powerOn()
        pollWhileOff()

        assertEquals(ConnectionState.Off, h.state.connection)
        assertEquals(1, h.tv.registrations)
    }

    @Test
    fun `off polling stops by itself once connected`() {
        h.pairThenTurnTvOff()
        h.session.startOffPolling()
        h.tv.powerOn()
        h.advance(h.pollInterval)
        h.awaitConnection(ConnectionState.Connected)

        h.tv.powerOff()
        h.awaitConnection(ConnectionState.Off)
        h.tv.powerOn()
        pollWhileOff()

        assertEquals(ConnectionState.Off, h.state.connection)
        assertEquals(2, h.tv.registrations)
    }

    @Test
    fun `off polling carries on after a failed wake`() {
        h.pairThenTurnTvOff()
        h.tv.wakesOnMagicPacket = false
        h.session.startOffPolling()

        h.session.wake()
        h.awaitConnection(ConnectionState.Connecting)
        h.advance(h.wakeTimeout)
        h.awaitConnection(ConnectionState.WakeFailed)

        h.tv.powerOn()
        h.advance(h.pollInterval)
        h.awaitConnection(ConnectionState.Connected)
    }

    @Test
    fun `after a power off, polling waits for the TV to go away before connecting to it again`() {
        h.tv.on("ssap://system/turnOff") { FakeTv.ok() } // The TV keeps answering while it shuts down.
        h.pair()
        h.session.powerOff()
        h.awaitConnection(ConnectionState.Off)

        h.session.startOffPolling()
        pollWhileOff()
        assertEquals(ConnectionState.Off, h.state.connection)
        assertEquals(1, h.tv.registrations)

        h.tv.powerOff()
        pollWhileOff(1)
        h.tv.powerOn()
        h.advance(h.pollInterval)
        h.awaitConnection(ConnectionState.Connected)
    }

    /** Lets a few polls go by, each with time to finish its probe. */
    private fun pollWhileOff(polls: Int = 3) = repeat(polls) {
        h.advance(h.pollInterval)
        h.settle()
    }
}
