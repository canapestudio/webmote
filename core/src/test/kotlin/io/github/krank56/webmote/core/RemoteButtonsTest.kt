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
    fun `digits and colour keys are sent as button messages, in the order they were pressed`() {
        pairWithPointer()

        val buttons = listOf(
            RemoteButton.Num1, RemoteButton.Num2, RemoteButton.Num0, RemoteButton.Num9, RemoteButton.Num3,
            RemoteButton.Num4, RemoteButton.Num5, RemoteButton.Num6, RemoteButton.Num7, RemoteButton.Num8,
            RemoteButton.Red, RemoteButton.Green, RemoteButton.Yellow, RemoteButton.Blue,
        )
        buttons.forEach(h.session::press)

        val names = listOf("1", "2", "0", "9", "3", "4", "5", "6", "7", "8", "RED", "GREEN", "YELLOW", "BLUE")
        h.eventually { h.tv.pointerMessages.size == names.size }
        assertEquals(names.map(::button), h.tv.pointerMessages)
    }

    @Test
    fun `every button is sent as a button message, by the name the reference projects list`() {
        pairWithPointer()
        assertEquals(RemoteButton.entries.toSet(), wireNames.keys, "buttons without an expected name")

        wireNames.keys.forEach(h.session::press)

        h.eventually { h.tv.pointerMessages.size == wireNames.size }
        assertEquals(wireNames.values.map(::button), h.tv.pointerMessages)
    }

    @Test
    fun `the buttons that open a service menu are InStart, EzAdjust and Advanced setting`() {
        pairWithPointer()

        RemoteButton.entries.filter { it.opensServiceMenu }.forEach(h.session::press)

        h.eventually { h.tv.pointerMessages.size >= 3 }
        h.settle()
        assertEquals(setOf("IN_START", "EZ_ADJUST", "ADVANCE_SETTING"), h.tv.pointerMessages.map(::nameOf).toSet())
        assertEquals(3, h.tv.pointerMessages.size)
    }

    @Test
    fun `the buttons confirmed on webOS 26 are the ones seen working on a webOS 26 TV`() {
        pairWithPointer()

        val confirmed = RemoteButton.entries.filter { it.confirmedOnWebOs26 }
        confirmed.forEach(h.session::press)

        h.eventually { h.tv.pointerMessages.size == confirmed.size }
        // The research's capture on a C2 running webOS 26, plus the keys tried on the test TV
        // (docs/research/webos-protocol.md §4.3).
        val verified = (
            "LEFT RIGHT DOWN UP HOME MENU BACK ENTER DASH INFO EXIT MUTE RED GREEN BLUE YELLOW VOLUMEUP VOLUMEDOWN " +
                "CHANNELUP CHANNELDOWN PLAY PAUSE NETFLIX GUIDE AMAZON IN_START"
            ).split(' ') + (0..9).map { "$it" }
        assertEquals(verified.toSet(), h.tv.pointerMessages.map(::nameOf).toSet())
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
        h.session.movePointer(5.0, 5.0)
        h.session.click()
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

    /** The button name in a `type:button` message; fails on any other message. */
    private fun nameOf(message: String): String {
        assertTrue(message.startsWith("type:button\nname:") && message.endsWith("\n\n"), "not a button message: $message")
        return message.removePrefix("type:button\nname:").removeSuffix("\n\n")
    }

    private companion object {
        const val GET_POINTER_SOCKET = "ssap://com.webos.service.networkinput/getPointerInputSocket"

        /** Every button and the name it's sent by (docs/research/webos-protocol.md §4.3). */
        val wireNames = mapOf(
            RemoteButton.Up to "UP", RemoteButton.Down to "DOWN", RemoteButton.Left to "LEFT",
            RemoteButton.Right to "RIGHT", RemoteButton.Ok to "ENTER", RemoteButton.Back to "BACK",
            RemoteButton.Home to "HOME", RemoteButton.Settings to "QMENU", RemoteButton.Info to "INFO",
            RemoteButton.Num0 to "0", RemoteButton.Num1 to "1", RemoteButton.Num2 to "2", RemoteButton.Num3 to "3",
            RemoteButton.Num4 to "4", RemoteButton.Num5 to "5", RemoteButton.Num6 to "6", RemoteButton.Num7 to "7",
            RemoteButton.Num8 to "8", RemoteButton.Num9 to "9",
            RemoteButton.Red to "RED", RemoteButton.Green to "GREEN", RemoteButton.Yellow to "YELLOW",
            RemoteButton.Blue to "BLUE", RemoteButton.Exit to "EXIT", RemoteButton.Asterisk to "ASTERISK",
            RemoteButton.Power to "POWER", RemoteButton.InStart to "IN_START", RemoteButton.EzAdjust to "EZ_ADJUST",
            RemoteButton.AdvancedSetting to "ADVANCE_SETTING",
            // TV and guide
            RemoteButton.Guide to "GUIDE", RemoteButton.Program to "PROGRAM", RemoteButton.ChannelList to "LIST",
            RemoteButton.LiveTv to "DASH", RemoteButton.Tv to "TV", RemoteButton.ChannelUp to "CHANNELUP",
            RemoteButton.ChannelDown to "CHANNELDOWN", RemoteButton.Flashback to "FLASHBACK",
            RemoteButton.Favourites to "FAVORITES", RemoteButton.Teletext to "TELETEXT",
            RemoteButton.TextOption to "TEXTOPTION", RemoteButton.Record to "RECORD", RemoteButton.Recordings to "RECLIST",
            // Sound and picture
            RemoteButton.Subtitles to "CC", RemoteButton.AudioDescription to "AD", RemoteButton.MultiAudio to "SAP",
            RemoteButton.VolumeUp to "VOLUMEUP", RemoteButton.VolumeDown to "VOLUMEDOWN", RemoteButton.Mute to "MUTE",
            RemoteButton.AspectRatio to "ASPECT_RATIO", RemoteButton.PictureMode to "EZPIC",
            RemoteButton.EnergySaving to "EYE_Q", RemoteButton.LiveZoom to "LIVE_ZOOM",
            RemoteButton.FocusZoom to "MAGNIFIER_ZOOM", RemoteButton.ThreeD to "3D_MODE",
            // Menus and apps
            RemoteButton.Menu to "MENU", RemoteButton.MyApps to "MYAPPS", RemoteButton.Recent to "RECENT",
            RemoteButton.InputHub to "INPUT_HUB", RemoteButton.Search to "SEARCH",
            RemoteButton.ScreenRemote to "SCREEN_REMOTE", RemoteButton.EManual to "EMANUAL",
            RemoteButton.SleepTimer to "TIMER", RemoteButton.AlwaysReady to "UPDOWN", RemoteButton.Simplink to "HCEC",
            // Playback and streaming
            RemoteButton.Play to "PLAY", RemoteButton.Pause to "PAUSE", RemoteButton.Stop to "STOP",
            RemoteButton.Rewind to "REWIND", RemoteButton.FastForward to "FASTFORWARD", RemoteButton.Previous to "GOTOPREV",
            RemoteButton.Next to "GOTONEXT", RemoteButton.Netflix to "NETFLIX", RemoteButton.Amazon to "AMAZON",
            RemoteButton.Alexa to "ALEXA", RemoteButton.Yandex to "YANDEX", RemoteButton.Ivi to "IVI",
            RemoteButton.Soccer to "SOCCER", RemoteButton.Twin to "TWIN", RemoteButton.Usp to "USP",
            RemoteButton.Bendable to "BENDABLE",
        )
    }
}
