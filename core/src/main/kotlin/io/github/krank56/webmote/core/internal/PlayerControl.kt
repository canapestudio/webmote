package io.github.krank56.webmote.core.internal

import io.github.krank56.webmote.core.MediaKey
import io.github.krank56.webmote.core.Player
import kotlinx.coroutines.launch
import java.io.IOException

/** The TV's foreground app, the player it may be, and seeking in it. */
internal class PlayerControl(private val core: SessionCore, private val commands: Commands) : Feature {
    override fun onConnected(link: Link) {
        link.scope.launch { follow(link) }
    }

    fun seekBack() = seek(back = true)

    fun seekForward() = seek(back = false)

    /** Seeks the way the foreground player does; any other app gets the media controls. */
    private fun seek(back: Boolean) {
        when (KnownPlayers.find(core.state.value.foregroundAppId)?.seek ?: SeekMethod.MediaControls) {
            SeekMethod.MediaControls -> commands.media(if (back) MediaKey.Rewind else MediaKey.FastForward)
        }
    }

    /** Follows the app in front on the TV until the link closes, then forgets it. */
    private suspend fun follow(link: Link) {
        try {
            // An empty ID says no app is known to be in front.
            link.socket.subscribe(GET_FOREGROUND_APP).collect { show(it.string("appId")?.takeIf(String::isNotBlank)) }
        } catch (_: SsapException) {
            // The TV won't say which app is in front; it stays unknown.
        } catch (_: IOException) {
            // The link is closing.
        } finally {
            // Without a link the app in front is unknown. A newer link reports its own.
            if (core.link == null) show(null)
        }
    }

    /** Reports [appId] as the foreground app, and as the player if it's a known one. */
    private fun show(appId: String?) {
        val player = KnownPlayers.find(appId)?.let { Player(it.appId) }
        core.update { it.copy(foregroundAppId = appId, player = player) }
    }

    private companion object {
        const val GET_FOREGROUND_APP = "ssap://com.webos.applicationManager/getForegroundAppInfo"
    }
}
