package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.faketv.FakeTv
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

/** How long the connection lasts: what happens when it drops. */
class LifetimeTest {
    @TempDir lateinit var dir: File
    private val h by lazy { Harness(dir) }
    private val otherTvs = mutableListOf<FakeTv>()

    /** A second TV on the IPv6 loopback with the same ports, as in [SeveralTvsTest]. */
    private val second: FakeTv by lazy {
        FakeTv(host = "::1", port = h.tv.port, legacyPort = h.tv.legacyPort).also(otherTvs::add).apply { uuid = "fake-tv-uuid-2" }
    }

    @AfterEach fun tearDown() {
        h.close()
        otherTvs.forEach { it.close() }
    }

    @Test
    fun `a link the TV drops while it's still on is reconnected straight away, without reporting it off`() {
        h.pair()

        ConnectionRecorder(h.session).use { recorder ->
            h.tv.dropConnections()

            h.eventually(message = { "a second registration" }) { h.tv.registrations == 2 }
            h.awaitConnection(ConnectionState.Connected)
            assertFalse(ConnectionState.Off in recorder.states, "states: ${recorder.states}")
        }
    }

    @Test
    fun `a link that drops because the TV went off is reported off, and isn't retried`() {
        h.pair()

        h.tv.powerOff()
        h.awaitConnection(ConnectionState.Off)
        h.tv.powerOn()
        h.advance(10.seconds)
        h.settle()

        assertEquals(ConnectionState.Off, h.state.connection)
        assertEquals(1, h.tv.registrations)
    }

    @Test
    fun `disconnecting never reconnects`() {
        h.pair()

        h.session.disconnect()
        h.awaitConnection(ConnectionState.Disconnected)
        h.settle(300)

        assertEquals(ConnectionState.Disconnected, h.state.connection)
        assertEquals(1, h.tv.registrations)
    }

    @Test
    fun `switching TVs doesn't reconnect to the TV left behind`() {
        pairBoth()

        h.session.switchTo(h.tv.uuid)
        awaitConnected(h.tv.uuid)
        h.settle(300)

        assertEquals(h.tv.uuid, h.state.tvId)
        assertEquals(ConnectionState.Connected, h.state.connection)
        assertEquals(1, second.registrations)
    }

    @Test
    fun `forgetting the TV in use doesn't reconnect to it`() {
        pairBoth()

        h.session.forget(second.uuid)
        awaitConnected(h.tv.uuid)
        h.settle(300)

        assertEquals(h.tv.uuid, h.state.tvId)
        assertEquals(1, second.registrations)
    }

    @Test
    fun `re-pairing closes the old link without reconnecting over it`() {
        h.pair()

        h.session.repair()
        h.eventually(message = { "a second registration" }) { h.tv.registrations == 2 }
        h.awaitConnection(ConnectionState.Connected)
        h.settle(300)

        assertEquals(2, h.tv.registrations)
        assertEquals(ConnectionState.Connected, h.state.connection)
    }

    /** Pairs [Harness.tv], then [second], which leaves the second TV active and connected. */
    private fun pairBoth() {
        h.pair()
        second.powerOn()
        h.session.connect(second.host, "Bedroom")
        awaitConnected(second.uuid)
    }

    private fun awaitConnected(tvId: String) =
        h.eventually(message = { "connected to $tvId" }) { h.state.tvId == tvId && h.state.connection == ConnectionState.Connected }
}
