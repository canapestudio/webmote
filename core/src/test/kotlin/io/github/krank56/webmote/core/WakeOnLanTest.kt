package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.faketv.FakeTv
import io.github.krank56.webmote.core.faketv.TvError
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class WakeOnLanTest {
    @TempDir lateinit var dir: File
    private val h by lazy { Harness(dir) }

    @AfterEach fun tearDown() = h.close()

    @Test
    fun `wake sends a well-formed magic packet to each of the TV's MACs`() {
        h.pairThenTurnTvOff()
        h.tv.wakesOnMagicPacket = false

        h.session.wake()

        h.eventually(message = { "a magic packet for each MAC" }) {
            h.tv.magicPacketsFor(h.tv.wiredMac!!).isNotEmpty() && h.tv.magicPacketsFor(h.tv.wifiMac!!).isNotEmpty()
        }
        assertTrue(h.tv.magicPackets.all { it.size == 102 })
        assertEquals(
            h.tv.magicPackets.size,
            h.tv.magicPacketsFor(h.tv.wiredMac!!).size + h.tv.magicPacketsFor(h.tv.wifiMac!!).size,
        )
    }

    @Test
    fun `wake reaches the TV through its own network's broadcast address when the general broadcast is lost`() {
        // As on a phone whose VPN captures 255.255.255.255: the general broadcast address never reaches
        // the TV (TEST-NET-1 here), but the broadcast address of the network the TV is on does. The
        // fake TV listens on loopback, so loopback stands in for that network and its broadcast address.
        val tv = FakeTv()
        val vpn = Harness(
            dir,
            tv,
            tv.sessionConfig().copy(
                wakeAddress = "192.0.2.1",
                localNetworks = { listOf(LocalNetwork(address = "127.0.0.1", prefixLength = 8, broadcast = tv.host)) },
            ),
        )
        try {
            vpn.pairThenTurnTvOff()

            vpn.session.wake()

            vpn.eventually(message = { "a magic packet for each MAC" }) {
                tv.magicPacketsFor(tv.wiredMac!!).isNotEmpty() && tv.magicPacketsFor(tv.wifiMac!!).isNotEmpty()
            }
            vpn.advance(vpn.pollInterval)
            vpn.awaitConnection(ConnectionState.Connected)
        } finally {
            vpn.close()
        }
    }

    @Test
    fun `wake leaves out networks the TV isn't on`() {
        val tv = FakeTv()
        val elsewhere = Harness(
            dir,
            tv,
            tv.sessionConfig().copy(
                wakeAddress = "192.0.2.1",
                localNetworks = { listOf(LocalNetwork(address = "10.5.0.2", prefixLength = 16, broadcast = tv.host)) },
            ),
        )
        try {
            elsewhere.pairThenTurnTvOff()
            tv.wakesOnMagicPacket = false

            elsewhere.session.wake()
            elsewhere.settle(300)

            assertTrue(tv.magicPackets.isEmpty())
        } finally {
            elsewhere.close()
        }
    }

    @Test
    fun `after a wake the session reports connecting, polls, and connects once the TV starts listening`() {
        h.pairThenTurnTvOff()
        h.tv.wakesOnMagicPacket = false

        h.session.wake()
        h.awaitConnection(ConnectionState.Connecting)
        repeat(3) {
            h.advance(h.pollInterval)
            h.settle()
        }
        assertEquals(ConnectionState.Connecting, h.state.connection)
        assertEquals(1, h.tv.registrations)

        h.tv.powerOn()
        h.advance(h.pollInterval)
        h.awaitConnection(ConnectionState.Connected)
        assertEquals(2, h.tv.registrations)
    }

    @Test
    fun `the session reports waking until the TV answers`() {
        h.pairThenTurnTvOff()
        h.tv.wakesOnMagicPacket = false
        assertEquals(false, h.state.waking)

        h.session.wake()
        h.eventually(message = { "waking" }) { h.state.waking }
        assertEquals(ConnectionState.Connecting, h.state.connection)

        h.tv.powerOn()
        h.advance(h.pollInterval)
        h.awaitConnection(ConnectionState.Connected)
        assertEquals(false, h.state.waking)
    }

    @Test
    fun `a TV turned off from the app can be woken from the app`() {
        h.pairAndLearnMacs()
        h.session.powerOff()
        h.awaitConnection(ConnectionState.Off)
        h.eventually(message = { "the TV to turn off" }) { !h.tv.isOn }

        h.session.wake()

        h.eventually(message = { "waking" }) { h.state.waking }
        h.eventually(message = { "the TV to wake" }) { h.tv.isOn }
        h.advance(h.pollInterval)
        h.awaitConnection(ConnectionState.Connected)
    }

    @Test
    fun `a failed wake stops reporting waking`() {
        h.pairThenTurnTvOff()
        h.tv.wakesOnMagicPacket = false

        h.session.wake()
        h.eventually(message = { "waking" }) { h.state.waking }
        h.advance(h.wakeTimeout + h.pollInterval)

        h.awaitConnection(ConnectionState.WakeFailed)
        assertEquals(false, h.state.waking)
    }

    @Test
    fun `disconnecting during a wake stops reporting waking`() {
        h.pairThenTurnTvOff()
        h.tv.wakesOnMagicPacket = false

        h.session.wake()
        h.eventually(message = { "waking" }) { h.state.waking }
        h.session.disconnect()

        h.awaitConnection(ConnectionState.Disconnected)
        assertEquals(false, h.state.waking)
    }

    @Test
    fun `a TV woken by the magic packet is connected to`() {
        h.pairThenTurnTvOff()

        h.session.wake()
        h.eventually(message = { "the TV to wake" }) { h.tv.isOn }
        h.advance(h.pollInterval)

        h.awaitConnection(ConnectionState.Connected)
    }

    @Test
    fun `a TV that doesn't answer within the wake timeout is reported as wake failed`() {
        h.pairThenTurnTvOff()
        h.tv.wakesOnMagicPacket = false

        h.session.wake()
        h.awaitConnection(ConnectionState.Connecting)
        h.advance(h.wakeTimeout - 1.seconds)
        h.settle()
        assertEquals(ConnectionState.Connecting, h.state.connection)

        h.advance(1.seconds)
        h.awaitConnection(ConnectionState.WakeFailed)

        // A TV that comes up later isn't connected to by the failed wake.
        h.tv.powerOn()
        h.advance(h.pollInterval * 3)
        h.settle()
        assertEquals(ConnectionState.WakeFailed, h.state.connection)
    }

    @Test
    fun `waking a TV with no stored MAC fails straight away`() {
        h.tv.on(GET_INFO) { throw TvError("404 no such service or method") }
        h.pair()
        h.awaitRequests(GET_INFO)
        h.tv.powerOff()
        h.awaitConnection(ConnectionState.Off)

        h.session.wake()

        h.awaitConnection(ConnectionState.WakeFailed)
        h.settle()
        assertTrue(h.tv.magicPackets.isEmpty())
    }

    @Test
    fun `waking a connected TV does nothing`() {
        h.pairAndLearnMacs()

        h.session.wake()
        h.settle()

        assertEquals(ConnectionState.Connected, h.state.connection)
        assertTrue(h.tv.magicPackets.isEmpty())
        assertEquals(1, h.tv.registrations)
    }

    @Test
    fun `disconnecting during a wake ends it`() {
        h.pairThenTurnTvOff()
        h.tv.wakesOnMagicPacket = false
        h.session.wake()
        h.awaitConnection(ConnectionState.Connecting)

        h.session.disconnect()
        h.awaitConnection(ConnectionState.Disconnected)
        h.tv.powerOn()
        h.advance(h.pollInterval * 3)
        h.settle()
        h.advance(h.wakeTimeout)
        h.settle()

        assertEquals(ConnectionState.Disconnected, h.state.connection)
        assertEquals(1, h.tv.registrations)
    }

    @Test
    fun `connect during a wake leaves the wake to carry on`() {
        h.pairThenTurnTvOff()
        h.tv.wakesOnMagicPacket = false
        h.session.wake()
        h.awaitConnection(ConnectionState.Connecting)

        ConnectionRecorder(h.session).use { recorder ->
            h.session.connect() // The app starting up.
            h.settle(300)
            repeat(2) {
                h.advance(h.pollInterval)
                h.settle()
            }
            assertEquals(listOf(ConnectionState.Connecting), recorder.states)
        }

        h.tv.powerOn()
        h.advance(h.pollInterval)
        h.awaitConnection(ConnectionState.Connected)
    }

    @Test
    fun `connect after a failed wake keeps reporting the failed wake while the TV doesn't answer`() {
        h.pairThenTurnTvOff()
        h.tv.wakesOnMagicPacket = false
        h.session.wake()
        h.advance(h.wakeTimeout)
        h.awaitConnection(ConnectionState.WakeFailed)

        ConnectionRecorder(h.session).use { recorder ->
            h.session.connect()
            h.eventually(message = { "a retry that fails" }) { ConnectionState.Connecting in recorder.states && !h.state.connection.isConnecting }
            assertEquals(listOf(ConnectionState.WakeFailed, ConnectionState.Connecting, ConnectionState.WakeFailed), recorder.states)
        }

        // Once the TV has connected again, a TV that's gone is plainly off.
        h.tv.powerOn()
        h.session.connect()
        h.awaitConnection(ConnectionState.Connected)
        h.tv.powerOff()
        h.awaitConnection(ConnectionState.Off)
    }

    private val ConnectionState.isConnecting get() = this == ConnectionState.Connecting

    private companion object {
        const val GET_INFO = "ssap://com.webos.service.connectionmanager/getinfo"
    }
}
