package io.github.krank56.webmote.core.internal

import io.github.krank56.webmote.core.Capability
import io.github.krank56.webmote.core.RemoteButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * The pointer socket: remote buttons.
 *
 * Messages are only sent while the socket is open; anything sent before it opens, or after the TV
 * refused it, is dropped.
 */
internal class PointerControl(private val core: SessionCore) : Feature {
    private var open: OpenSocket? = null

    private class OpenSocket(val link: Link, val socket: TextSocket)

    override fun onConnected(link: Link) {
        link.scope.launch { keepOpen(link) }
    }

    fun press(button: RemoteButton) {
        current()?.send("type:button\nname:${button.wireName}\n\n")
    }

    /** The open pointer socket, if it belongs to the live link. */
    private fun current(): TextSocket? = open?.takeIf { it.link === core.link }?.socket

    /**
     * Keeps a pointer socket open for as long as [link] lives. Each time the TV closes it, it's
     * reopened once; if that fails, the pointer is unavailable until the next connection.
     */
    private suspend fun keepOpen(link: Link) {
        while (true) {
            val socket = open(link) ?: return
            open = OpenSocket(link, socket)
            learn(link, Capability.Available)
            try {
                socket.closed.await()
            } finally {
                if (open?.socket === socket) open = null
                socket.close()
            }
        }
    }

    /**
     * Asks the TV for its pointer socket and opens it with the TV's pinned certificate. Returns null
     * if the TV refuses, after recording the pointer as unavailable unless the link itself is closing.
     */
    private suspend fun open(link: Link): TextSocket? {
        val socket = try {
            val path = link.socket.request(GET_POINTER_SOCKET).string("socketPath")
                ?: throw SsapException("$GET_POINTER_SOCKET: no socketPath")
            withTimeout(core.config.requestTimeout) { TextSocket.open(link.http, path) }
        } catch (_: TimeoutCancellationException) {
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (socket == null && !link.socket.closed.isCompleted) learn(link, Capability.Unavailable)
        return socket
    }

    private fun learn(link: Link, pointer: Capability) {
        core.update { it.copy(capabilities = it.capabilities.copy(pointer = pointer)) }
        core.registry.update(link.tvId) { it.copy(capabilities = it.capabilities.copy(pointer = pointer)) }
    }

    private companion object {
        const val GET_POINTER_SOCKET = "ssap://com.webos.service.networkinput/getPointerInputSocket"
    }
}
