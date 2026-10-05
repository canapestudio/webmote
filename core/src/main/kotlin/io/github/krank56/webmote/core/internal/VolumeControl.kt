package io.github.krank56.webmote.core.internal

/** Volume: ±. */
internal class VolumeControl(private val core: SessionCore) {
    fun up() = core.withLink { it.socket.request("ssap://audio/volumeUp") }

    fun down() = core.withLink { it.socket.request("ssap://audio/volumeDown") }
}
