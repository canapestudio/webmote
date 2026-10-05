package io.github.krank56.webmote.core.internal

import io.github.krank56.webmote.core.MediaKey
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Stateless one-shot commands: media keys, channels and the keyboard.
 *
 * Each command is sent as soon as it's issued, so rapid presses reach the TV in the order they were made.
 */
internal class Commands(private val core: SessionCore) : Feature {
    private var playNext = true

    override fun onConnected(link: Link) {
        playNext = true
    }

    fun media(key: MediaKey) = request(key.uri, EMPTY)

    /** Alternates play and pause, starting with play: the TV doesn't reliably report playback state. */
    fun playPause() = core.withLink { link ->
        val key = if (playNext) MediaKey.Play else MediaKey.Pause
        playNext = !playNext
        link.socket.request(key.uri, EMPTY)
    }

    fun channelUp() = request("ssap://tv/channelUp")

    fun channelDown() = request("ssap://tv/channelDown")

    fun insertText(text: String) {
        if (text.isEmpty()) return
        request("$IME/insertText", buildJsonObject { put("text", text); put("replace", false) })
    }

    fun deleteCharacters(count: Int) {
        if (count <= 0) return
        request("$IME/deleteCharacters", buildJsonObject { put("count", count) })
    }

    fun sendEnter() = request("$IME/sendEnterKey", EMPTY)

    private fun request(uri: String, payload: JsonObject? = null) = core.withLink { it.socket.request(uri, payload) }

    private companion object {
        const val IME = "ssap://com.webos.service.ime"
        val EMPTY = JsonObject(emptyMap())
    }
}
