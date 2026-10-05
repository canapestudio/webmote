package io.github.krank56.webmote.core.internal

import io.github.krank56.webmote.core.ConnectionState
import kotlinx.coroutines.launch
import java.io.IOException

/** Power off, screen off/on, Wake-on-LAN, and following whether the TV is on ([Liveness]). */
internal class PowerControl(private val core: SessionCore, private val connector: Connector) : Feature {
    private val liveness = Liveness(core, connector)
    private val screen = ScreenControl(core)

    override fun onConnected(link: Link) {
        liveness.onConnected()
        val saved = core.registry[link.tvId] ?: return
        if (saved.wiredMac != null && saved.wifiMac != null) return
        link.scope.launch { learnMacs(link) }
    }

    /**
     * Sends `system/turnOff` and reports the TV off straight away. The TV's reply is unreliable while
     * it shuts down, so it isn't awaited. The request goes out before the close frame.
     */
    fun powerOff() {
        val link = core.link ?: return
        link.socket.send("request", POWER_OFF)
        liveness.onPoweredOff()
        connector.stop(ConnectionState.Off)
    }

    fun wake() = liveness.wake()

    fun startOffPolling() = liveness.startOffPolling()

    fun stopOffPolling() = liveness.stopOffPolling()

    fun screenOff() = screen.off()

    fun screenOn() = screen.on()

    /** Stores the MACs Wake-on-LAN needs. Some TVs don't implement getinfo; they just can't be woken. */
    private suspend fun learnMacs(link: Link) {
        val info = try {
            link.socket.request(GET_NETWORK_INFO)
        } catch (e: SsapException) {
            return
        } catch (e: IOException) {
            return
        }
        val wired = info.obj("wiredInfo")?.string("macAddress")?.let(WakeOnLan::normaliseMac)
        val wifi = info.obj("wifiInfo")?.string("macAddress")?.let(WakeOnLan::normaliseMac)
        core.registry.update(link.tvId) { it.copy(wiredMac = wired ?: it.wiredMac, wifiMac = wifi ?: it.wifiMac) }
    }

    private companion object {
        const val GET_NETWORK_INFO = "ssap://com.webos.service.connectionmanager/getinfo"
        const val POWER_OFF = "ssap://system/turnOff"
    }
}
