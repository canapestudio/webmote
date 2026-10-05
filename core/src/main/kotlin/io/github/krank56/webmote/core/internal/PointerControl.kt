package io.github.krank56.webmote.core.internal

import io.github.krank56.webmote.core.Capability
import io.github.krank56.webmote.core.RemoteButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToInt

/**
 * The pointer socket: remote buttons, pointer movement, clicks and scrolling.
 *
 * Messages are only sent while the socket is open; anything sent before it opens, or after the TV
 * refused it, is dropped.
 */
internal class PointerControl(private val core: SessionCore) : Feature {
    private var open: OpenSocket? = null
    private val pointer = Accumulator()
    private val wheel = Accumulator()

    private class OpenSocket(val link: Link, val socket: TextSocket)

    override fun onConnected(link: Link) {
        link.scope.launch { keepOpen(link) }
    }

    fun press(button: RemoteButton) {
        current()?.send("type:button\nname:${button.wireName}\n\n")
    }

    fun move(dx: Double, dy: Double) {
        val socket = current() ?: return
        val (x, y) = pointer.take(dx, dy) ?: return
        socket.send("type:move\ndx:$x\ndy:$y\ndown:0\n\n")
    }

    fun click() {
        current()?.send("type:click\n\n")
    }

    fun scroll(dx: Double, dy: Double) {
        val socket = current() ?: return
        val (x, y) = wheel.take(dx, dy) ?: return
        socket.send("type:scroll\ndx:$x\ndy:$y\n\n")
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
            pointer.reset()
            wheel.reset()
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

/**
 * Turns fractional deltas into the whole numbers the pointer socket is sent, carrying the remainder
 * over so that slow drags still add up to movement.
 */
private class Accumulator {
    private var x = 0.0
    private var y = 0.0

    /** Adds a delta and returns the whole part to send, or null while it's still under one unit. */
    fun take(dx: Double, dy: Double): Pair<Int, Int>? {
        if (!dx.isFinite() || !dy.isFinite()) return null
        x += dx
        y += dy
        val wholeX = x.roundToInt()
        val wholeY = y.roundToInt()
        if (wholeX == 0 && wholeY == 0) return null
        x -= wholeX
        y -= wholeY
        return wholeX to wholeY
    }

    fun reset() {
        x = 0.0
        y = 0.0
    }
}
