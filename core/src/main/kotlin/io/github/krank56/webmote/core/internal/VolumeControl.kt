package io.github.krank56.webmote.core.internal

import io.github.krank56.webmote.core.Capability
import io.github.krank56.webmote.core.Volume
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException

/** Volume: ±, the level slider, mute, and the TV's volume subscription. */
internal class VolumeControl(private val core: SessionCore) : Feature {
    private val slider = Throttle<Int>(core.scope, core.config.throttleInterval) { level ->
        core.withLink { it.socket.request(SET_VOLUME, buildJsonObject { put("volume", level) }) }
    }

    override fun onConnected(link: Link) {
        link.scope.launch { follow(link) }
    }

    fun up() = core.withLink { it.socket.request("ssap://audio/volumeUp") }

    fun down() = core.withLink { it.socket.request("ssap://audio/volumeDown") }

    /** A slider value while dragging; throttled. */
    fun drag(level: Int) = slider.drag(level.coerceIn(0, MAX_LEVEL))

    /** The slider's value on release. */
    fun set(level: Int) = slider.release(level.coerceIn(0, MAX_LEVEL))

    fun setMute(muted: Boolean) = core.withLink {
        it.socket.request("ssap://audio/setMute", buildJsonObject { put("mute", muted) })
    }

    /** Follows `getVolume`, or `getStatus` on TVs that refuse it. If neither works, no level is reported. */
    private suspend fun follow(link: Link) {
        try {
            for (uri in listOf(GET_VOLUME, GET_STATUS)) {
                try {
                    link.socket.subscribe(uri).collect { onVolume(link, it) }
                    return
                } catch (_: SsapException) {
                    continue
                }
            }
            learn(link, Capability.Unavailable)
        } catch (_: IOException) {
            // The link is closing.
        }
    }

    /**
     * Reads both payload shapes: newer firmware nests the values in `volumeStatus`, older firmware
     * and `getStatus` send them flat.
     */
    private fun onVolume(link: Link, payload: JsonObject) {
        val status = payload.obj("volumeStatus")
        val level = (status?.int("volume") ?: payload.int("volume"))?.takeIf { it in 0..MAX_LEVEL }
        val muted = status?.bool("muteStatus") ?: payload.bool("mute") ?: payload.bool("muted")
        core.update { it.copy(volume = Volume(level, muted ?: it.volume.muted)) }
        learn(link, if (level != null) Capability.Available else Capability.Unavailable)
    }

    private fun learn(link: Link, volumeLevel: Capability) {
        core.update { it.copy(capabilities = it.capabilities.copy(volumeLevel = volumeLevel)) }
        core.registry.update(link.tvId) { it.copy(capabilities = it.capabilities.copy(volumeLevel = volumeLevel)) }
    }

    private companion object {
        const val GET_VOLUME = "ssap://audio/getVolume"
        const val GET_STATUS = "ssap://audio/getStatus"
        const val SET_VOLUME = "ssap://audio/setVolume"
        const val MAX_LEVEL = 100
    }
}
