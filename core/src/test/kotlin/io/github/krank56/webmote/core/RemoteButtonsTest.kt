package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.faketv.FakeTv
import io.github.krank56.webmote.core.faketv.FakeTv.Companion.ok
import io.github.krank56.webmote.core.faketv.TvError
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteButtonsTest {
    @TempDir lateinit var dir: File
    private val h by lazy { Harness(dir) }

    @AfterEach fun tearDown() = h.close()

    @Test
    fun `the session opens the pointer socket on connect and remembers that it works`() {
        h.pair()

        h.eventually { h.state.capabilities.pointer == Capability.Available }
        h.awaitRequests(GET_POINTER_SOCKET)
        assertEquals(Capability.Available, h.registry[h.tv.uuid]!!.capabilities.pointer)
    }

    @Test
    fun `D-pad, OK, Back, Home, Settings and Info are sent as button messages on the pointer socket`() {
        pairWithPointer()

        listOf(
            RemoteButton.Up, RemoteButton.Down, RemoteButton.Left, RemoteButton.Right, RemoteButton.Ok,
            RemoteButton.Back, RemoteButton.Home, RemoteButton.Settings, RemoteButton.Info,
        ).forEach(h.session::press)

        val names = listOf("UP", "DOWN", "LEFT", "RIGHT", "ENTER", "BACK", "HOME", "QMENU", "INFO")
        h.eventually { h.tv.pointerMessages.size == names.size }
        assertEquals(names.map(::button), h.tv.pointerMessages)
    }

    @Test
    fun `a TV that refuses the pointer socket has no pointer capability, and the session remembers it`() {
        h.tv.pointerSocketAvailable = false
        h.pair()

        h.eventually { h.state.capabilities.pointer == Capability.Unavailable }
        assertEquals(Capability.Unavailable, h.registry[h.tv.uuid]!!.capabilities.pointer)
    }

    @Test
    fun `a TV that refuses getPointerInputSocket has no pointer capability`() {
        h.tv.on(GET_POINTER_SOCKET) { throw TvError("401 insufficient permissions") }
        h.pair()

        h.eventually { h.state.capabilities.pointer == Capability.Unavailable }
        assertEquals(Capability.Unavailable, h.registry[h.tv.uuid]!!.capabilities.pointer)
    }

    @Test
    fun `buttons pressed without a pointer socket are dropped and the session stays connected`() {
        h.tv.pointerSocketAvailable = false
        h.pair()
        h.eventually { h.state.capabilities.pointer == Capability.Unavailable }

        h.session.press(RemoteButton.Ok)
        h.session.volumeUp()

        h.awaitRequests("ssap://audio/volumeUp")
        assertTrue(h.tv.pointerMessages.isEmpty())
        assertEquals(ConnectionState.Connected, h.state.connection)
    }

    @Test
    fun `the pointer socket must present the TV's pinned certificate`() {
        FakeTv().use { impostor ->
            impostor.powerOn()
            h.tv.on(GET_POINTER_SOCKET) {
                ok { put("socketPath", "wss://${impostor.host}:${impostor.port}/resources/0/netinput.pointer.sock") }
            }
            h.pair()

            h.eventually { h.state.capabilities.pointer == Capability.Unavailable }
            assertTrue(impostor.requests.isEmpty())
        }
    }

    @Test
    fun `a pointer socket that closes while connected is reopened`() {
        pairWithPointer()

        h.tv.dropPointerSocket()
        h.awaitRequests(GET_POINTER_SOCKET, 2)
        h.eventually { pressUntilReceived(RemoteButton.Home) }

        assertEquals(Capability.Available, h.state.capabilities.pointer)
        assertEquals(button("HOME"), h.tv.pointerMessages.last())
    }

    @Test
    fun `a pointer socket that can't be reopened is reported unavailable`() {
        pairWithPointer()

        h.tv.pointerSocketAvailable = false
        h.tv.dropPointerSocket()

        h.eventually { h.state.capabilities.pointer == Capability.Unavailable }
        assertEquals(Capability.Unavailable, h.registry[h.tv.uuid]!!.capabilities.pointer)
        assertEquals(ConnectionState.Connected, h.state.connection)
    }

    @Test
    fun `the pointer is tried again on the next connection`() {
        h.tv.pointerSocketAvailable = false
        h.pair()
        h.eventually { h.state.capabilities.pointer == Capability.Unavailable }

        h.tv.pointerSocketAvailable = true
        h.reopen()
        h.session.connect()

        h.eventually { h.state.capabilities.pointer == Capability.Available }
        assertEquals(Capability.Available, h.registry[h.tv.uuid]!!.capabilities.pointer)
    }

    private fun pairWithPointer() {
        h.pair()
        h.eventually { h.state.capabilities.pointer == Capability.Available }
    }

    /** Presses [button] and reports whether the TV has received it; the reopened socket may not be up yet. */
    private fun pressUntilReceived(button: RemoteButton): Boolean {
        h.session.press(button)
        h.settle(20)
        return h.tv.pointerMessages.isNotEmpty()
    }

    private fun button(name: String) = "type:button\nname:$name\n\n"

    private companion object {
        const val GET_POINTER_SOCKET = "ssap://com.webos.service.networkinput/getPointerInputSocket"
    }
}
