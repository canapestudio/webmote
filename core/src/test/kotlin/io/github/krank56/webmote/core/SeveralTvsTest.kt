package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.faketv.FakeTv
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Two fake TVs: [Harness.tv] on 127.0.0.1 and [second] on the IPv6 loopback with the same ports,
 * since a session uses one port for every TV.
 */
class SeveralTvsTest {
    @TempDir lateinit var dir: File
    private val h by lazy { Harness(dir) }
    private val otherTvs = mutableListOf<FakeTv>()

    private val second: FakeTv by lazy {
        FakeTv(host = "::1", port = h.tv.port, legacyPort = h.tv.legacyPort).also(otherTvs::add).apply {
            uuid = "fake-tv-uuid-2"
            model = "OLED65G5LA"
        }
    }

    @AfterEach fun tearDown() {
        h.close()
        otherTvs.forEach { it.close() }
    }

    /** Waits until the session is in [connection] with the TV [id]. */
    private fun awaitTv(id: String, connection: ConnectionState = ConnectionState.Connected) =
        h.eventually(message = { "$connection with $id" }) { h.state.tvId == id && h.state.connection == connection }

    /** Pairs the first TV as "Living room", then the second as "Bedroom", which leaves the second active. */
    private fun pairBoth() {
        h.pair("Living room")
        second.powerOn()
        h.session.connect(second.host, "Bedroom")
        awaitTv(second.uuid)
    }

    @Test
    fun `pairing another TV saves both, makes the new one active and closes the old connection`() {
        pairBoth()

        assertEquals(
            listOf(h.tv.uuid to "Living room", second.uuid to "Bedroom"),
            h.registry.tvs.value.map { it.id to it.name },
        )
        assertEquals(second.uuid, h.registry.activeTvId.value)
        h.eventually(message = { "the first TV's connection closed" }) { h.tv.openConnections == 0 }
    }

    @Test
    fun `switching TVs closes the old connection and connects to the new one`() {
        pairBoth()

        h.session.switchTo(h.tv.uuid)
        awaitTv(h.tv.uuid)

        assertEquals(h.tv.uuid, h.registry.activeTvId.value)
        assertEquals(TvInfo(h.tv.model, h.tv.osVersion), h.state.info)
        h.eventually(message = { "the second TV's connection closed" }) { second.openConnections == 0 }
        h.session.volumeUp()
        h.awaitRequests("ssap://audio/volumeUp")
        assertTrue(second.requests("ssap://audio/volumeUp").isEmpty())
    }

    @Test
    fun `switching to the TV in use keeps its connection`() {
        pairBoth()
        val registers = second.requests.count { it.type == "register" }

        h.session.switchTo(second.uuid)
        h.session.volumeUp()
        second.awaitVolumeUp()

        assertEquals(registers, second.requests.count { it.type == "register" })
        assertEquals(ConnectionState.Connected, h.state.connection)
    }

    @Test
    fun `reopening the app connects to the TV used last`() {
        pairBoth()
        h.session.switchTo(h.tv.uuid)
        awaitTv(h.tv.uuid)
        val secondRegisters = second.requests.count { it.type == "register" }

        h.reopen()
        h.session.connect()
        awaitTv(h.tv.uuid)

        assertEquals(secondRegisters, second.requests.count { it.type == "register" })
    }

    @Test
    fun `forgetting the active TV removes all its data and connects to the remaining one`() {
        pairBoth()
        h.registry.toggleFavourite(second.uuid, "netflix")

        h.session.forget(second.uuid)
        awaitTv(h.tv.uuid)

        assertEquals(listOf(h.tv.uuid), h.registry.tvs.value.map { it.id })
        assertEquals(h.tv.uuid, h.registry.activeTvId.value)
        h.eventually(message = { "the forgotten TV's connection closed" }) { second.openConnections == 0 }
        h.reopen()
        assertNull(h.registry[second.uuid])
    }

    @Test
    fun `forgetting another TV leaves the active TV connected`() {
        pairBoth()

        h.session.forget(h.tv.uuid)
        h.session.volumeUp()
        second.awaitVolumeUp()

        assertEquals(listOf(second.uuid), h.registry.tvs.value.map { it.id })
        assertEquals(ConnectionState.Connected, h.state.connection)
        assertEquals(second.uuid, h.state.tvId)
    }

    @Test
    fun `forgetting the last TV disconnects and leaves no TV`() {
        h.pair()

        h.session.forget(h.tv.uuid)
        h.eventually(message = { "disconnected with no TV" }) {
            h.state.connection == ConnectionState.Disconnected && h.state.tvId == null
        }

        assertEquals(TvState(), h.state)
        assertTrue(h.registry.tvs.value.isEmpty())
        assertNull(h.registry.activeTvId.value)
        h.eventually(message = { "the forgotten TV's connection closed" }) { h.tv.openConnections == 0 }
    }

    @Test
    fun `re-pairing a TV that isn't active re-pairs that TV and leaves the active one's pairing alone`() {
        pairBoth()
        val bedroom = identity(h.registry[second.uuid])

        h.session.repair(h.tv.uuid)

        awaitTv(h.tv.uuid)
        assertEquals(h.tv.uuid, h.registry.activeTvId.value)
        assertNull(h.tv.requests.last { it.type == "register" }.string("client-key"))
        assertEquals(h.tv.issuedKeys.last(), h.registry[h.tv.uuid]?.clientKey)
        assertEquals(bedroom, identity(h.registry[second.uuid]))
    }

    @Test
    fun `a renamed TV keeps its name after the app restarts`() {
        pairBoth()

        h.session.rename(h.tv.uuid, "Kitchen")
        h.eventually { h.registry[h.tv.uuid]?.name == "Kitchen" }
        h.reopen()

        assertEquals(listOf("Kitchen", "Bedroom"), h.registry.tvs.value.map { it.name })
    }

    /** Pairs the first TV, then replaces it at its address with another TV presenting the same certificate. */
    private fun pairThenReplaceAtSameAddress(): FakeTv {
        h.pair("Living room")
        h.tv.powerOff()
        return FakeTv(host = h.tv.host, port = h.tv.port, legacyPort = h.tv.legacyPort).also(otherTvs::add).apply {
            uuid = "fake-tv-uuid-3"
            certificate = h.tv.certificate
            powerOn()
        }
    }

    @Test
    fun `connecting by address to another TV than the one saved there pairs it as a new TV`() {
        val other = pairThenReplaceAtSameAddress()
        val livingRoom = identity(h.registry[h.tv.uuid])

        h.session.connect(other.host, "Office")
        awaitTv(other.uuid)

        assertNull(other.requests.single { it.type == "register" }.string("client-key"))
        assertEquals(livingRoom, identity(h.registry[h.tv.uuid]))
        assertEquals("Office", h.registry[other.uuid]?.name)
    }

    @Test
    fun `reconnecting to a saved TV whose address now belongs to another TV doesn't register with it`() {
        val other = pairThenReplaceAtSameAddress()
        val livingRoom = identity(h.registry[h.tv.uuid])

        h.reopen()
        h.session.connect()
        h.awaitConnection(ConnectionState.Off)

        assertTrue(other.requests.none { it.type == "register" })
        assertEquals(livingRoom, identity(h.registry[h.tv.uuid]))
        assertNull(h.registry[other.uuid])
    }

    /** What another TV answering at this TV's address must never overwrite. */
    private fun identity(tv: SavedTv?) = listOf(tv?.name, tv?.host, tv?.clientKey, tv?.certificatePin)

    private fun FakeTv.awaitVolumeUp() =
        h.eventually(message = { "volumeUp on $host" }) { requests("ssap://audio/volumeUp").isNotEmpty() }
}
