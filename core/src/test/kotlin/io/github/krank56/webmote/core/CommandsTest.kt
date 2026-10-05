package io.github.krank56.webmote.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CommandsTest {
    @TempDir lateinit var dir: File
    private val h by lazy { Harness(dir) }

    @AfterEach fun tearDown() = h.close()

    @Test
    fun `media keys send their media controls command`() {
        h.pair()

        MediaKey.entries.forEach(h.session::media)

        val uris = listOf("play", "pause", "stop", "rewind", "fastForward").map { "ssap://media.controls/$it" }
        h.eventually { mediaRequests().size == uris.size }
        assertEquals(uris, mediaRequests().map { it.uri })
        assertEquals(List(uris.size) { JsonObject(emptyMap()) }, mediaRequests().map { it.payload })
    }

    @Test
    fun `play-pause alternates play and pause, starting with play`() {
        h.pair()

        repeat(4) { h.session.playPause() }

        h.eventually { mediaRequests().size == 4 }
        assertEquals(listOf(PLAY, PAUSE, PLAY, PAUSE), mediaRequests().map { it.uri })
    }

    @Test
    fun `play-pause starts with play again on each new connection`() {
        h.pair()
        h.session.playPause()
        h.eventually { mediaRequests().size == 1 }

        h.session.disconnect()
        h.awaitConnection(ConnectionState.Disconnected)
        h.session.connect()
        h.awaitConnection(ConnectionState.Connected)
        h.session.playPause()

        h.eventually { mediaRequests().size == 2 }
        assertEquals(listOf(PLAY, PLAY), mediaRequests().map { it.uri })
    }

    @Test
    fun `channel up and down reach the TV`() {
        h.pair()

        h.session.channelUp()
        h.session.channelDown()
        h.session.channelUp()

        h.eventually { channelRequests().size == 3 }
        assertEquals(
            listOf("ssap://tv/channelUp", "ssap://tv/channelDown", "ssap://tv/channelUp"),
            channelRequests().map { it.uri },
        )
    }

    @Test
    fun `typing, backspace and enter reach the TV's text field in order`() {
        h.pair()

        "héllo".forEach { h.session.insertText(it.toString()) }
        h.session.deleteCharacters()
        h.session.insertText("o!")
        h.session.deleteCharacters(2)
        h.session.sendEnter()

        val expected = "héllo".map { insert(it.toString()) } +
            listOf(delete(1), insert("o!"), delete(2), IME + "sendEnterKey" to JsonObject(emptyMap()))
        h.eventually { imeRequests().size == expected.size }
        assertEquals(expected, imeRequests().map { it.uri to it.payload })
    }

    @Test
    fun `text is inserted without replacing what's there`() {
        h.pair()

        h.session.insertText("a")

        h.eventually { imeRequests().isNotEmpty() }
        val replace = imeRequests().single()["replace"] as JsonPrimitive
        assertFalse(replace.isString)
        assertFalse(replace.boolean)
    }

    @Test
    fun `empty text and empty deletes are not sent`() {
        h.pair()

        h.session.insertText("")
        h.session.deleteCharacters(0)
        h.session.sendEnter()

        h.eventually { imeRequests().isNotEmpty() }
        h.settle()
        assertEquals(listOf(IME + "sendEnterKey"), imeRequests().map { it.uri })
    }

    @Test
    fun `commands sent while disconnected are dropped`() {
        h.pair()
        h.session.disconnect()
        h.awaitConnection(ConnectionState.Disconnected)

        h.session.media(MediaKey.Play)
        h.session.insertText("x")
        h.session.channelUp()
        h.settle()

        assertEquals(emptyList(), mediaRequests() + imeRequests() + channelRequests())
    }

    private fun mediaRequests() = h.tv.requests.filter { it.uri?.startsWith("ssap://media.controls/") == true }

    private fun channelRequests() = h.tv.requests.filter { it.uri?.startsWith("ssap://tv/channel") == true }

    private fun imeRequests() = h.tv.requests.filter { it.uri?.startsWith(IME) == true }

    private fun insert(text: String) = IME + "insertText" to buildJsonObject { put("text", text); put("replace", false) }

    private fun delete(count: Int) = IME + "deleteCharacters" to buildJsonObject { put("count", count) }

    private companion object {
        const val PLAY = "ssap://media.controls/play"
        const val PAUSE = "ssap://media.controls/pause"
        const val IME = "ssap://com.webos.service.ime/"
    }
}
