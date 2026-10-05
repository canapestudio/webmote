package io.github.krank56.webmote.core.internal

import io.github.krank56.webmote.core.ConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.time.Duration.Companion.milliseconds

/**
 * Waking the TV, and following whether it's on while the session has no link to it.
 *
 * A TV is probed with a plain TCP connect to its SSAP port, which is cheap and leaves the reported
 * state alone; only once the TV answers does the session connect for real.
 */
internal class Liveness(private val core: SessionCore, private val connector: Connector) {
    private var offPolling: Job? = null

    /**
     * The TV whose last wake failed, until it next connects. Attempts that find it off keep reporting
     * [ConnectionState.WakeFailed], so the UI keeps its hint about the TV's wake setting.
     */
    private var wakeFailedFor: String? = null

    /**
     * Set by a power off. A TV keeps answering for a moment while it shuts down, so polling connects
     * to it again only once a probe has found it gone. A wake or a new connection clears it.
     */
    private var poweredOff = false

    init {
        connector.offStateOf = { tvId -> if (tvId != null && tvId == wakeFailedFor) ConnectionState.WakeFailed else ConnectionState.Off }
    }

    /** Call when the session has just turned the TV off. */
    fun onPoweredOff() {
        poweredOff = true
    }

    /** Call when a link to the TV comes up. */
    fun onConnected() {
        poweredOff = false
        wakeFailedFor = null
    }

    /**
     * Sends magic packets to each of the active TV's MACs, reports [ConnectionState.Connecting], and
     * connects as soon as the TV answers a probe. Does nothing while the TV is evidently on.
     *
     * The wake stands in for the connection attempt: [Connector.connect] leaves it alone, while a
     * disconnect or connecting to another TV cancels it.
     */
    fun wake() {
        val tv = core.registry.activeTv ?: return
        if (core.state.value.connection in TV_ON) return
        val macs = listOfNotNull(tv.wiredMac, tv.wifiMac)
        if (macs.isEmpty()) {
            wakeFailedFor = tv.id
            connector.stop(ConnectionState.WakeFailed)
            core.update { it.copy(tvId = tv.id) }
            return
        }
        poweredOff = false
        connector.connectWhen { awaitWake(tv.id, tv.host, macs) }
        core.update { it.copy(tvId = tv.id, host = tv.host, connection = ConnectionState.Connecting, waking = true) }
    }

    /** Sends the magic packets and probes the TV until it answers: true, or false once the wake timed out. */
    private suspend fun awaitWake(tvId: String, host: String, macs: List<String>): Boolean {
        val config = core.config
        val answered = coroutineScope {
            val packets = launch {
                val addresses = withContext(config.ioDispatcher) {
                    WakeOnLan.addresses(host, config.localNetworks(), fallback = config.wakeAddress)
                }
                repeat(PACKET_REPEATS) { i ->
                    if (i > 0) delay(PACKET_INTERVAL)
                    withContext(config.ioDispatcher) { WakeOnLan.send(macs, addresses, config.wakePort) }
                }
            }
            val answered = withTimeoutOrNull(config.wakeTimeout) {
                do delay(config.pollInterval) while (!reachable(host))
            } != null
            packets.cancel()
            answered
        }
        // Reported only once the packets and probes have stopped, so a connect() straight after it
        // isn't taken for this wake still running.
        if (!answered) {
            wakeFailedFor = tvId
            core.update { it.copy(connection = ConnectionState.WakeFailed, waking = false) }
        }
        return answered
    }

    /**
     * While the TV is off (or a wake failed), probes it every poll interval and connects as soon as it
     * answers. Ends with [stopOffPolling], or by itself once the session leaves the off states for
     * anything but connecting: connected, or a state only the user can resolve.
     */
    fun startOffPolling() {
        if (offPolling?.isActive == true) return
        offPolling = core.scope.launch {
            val probing = launch {
                while (true) {
                    delay(core.config.pollInterval)
                    connectIfReachable()
                }
            }
            core.state.first { it.connection !in POLLED }
            probing.cancel()
        }
    }

    fun stopOffPolling() {
        offPolling?.cancel()
        offPolling = null
    }

    /** Connects to the active TV if it's off and answers a probe. */
    private suspend fun connectIfReachable() {
        if (core.state.value.connection !in OFF) return
        val tv = core.registry.activeTv ?: return
        if (!reachable(tv.host)) {
            poweredOff = false
            return
        }
        if (poweredOff) return
        // A wake or a connection may have started while the probe ran.
        if (core.state.value.connection in OFF) connector.connect()
    }

    /** Whether the TV at [host] answers on the SSAP port. */
    private suspend fun reachable(host: String): Boolean = core.acceptsConnection(host, core.config.port)

    private companion object {
        /** States in which the TV is known to be on: connected, or showing a pairing prompt or PIN. */
        val TV_ON = setOf(ConnectionState.Connected, ConnectionState.AwaitingPrompt, ConnectionState.AwaitingPin)

        /** States in which the TV is believed off, so it's worth probing. */
        val OFF = setOf(ConnectionState.Off, ConnectionState.WakeFailed)

        /** States off polling carries on through: off, and connecting (a wake, or a polled TV answering). */
        val POLLED = OFF + ConnectionState.Connecting

        /** Like lgtv2: a few packets in case one is lost. */
        const val PACKET_REPEATS = 3
        val PACKET_INTERVAL = 100.milliseconds
    }
}

/**
 * Whether [host] accepts a TCP connection on [port] within the connect timeout. A cheap probe that
 * sends nothing, so it doesn't disturb the TV or the reported state. Runs on the IO dispatcher.
 */
internal suspend fun SessionCore.acceptsConnection(host: String, port: Int): Boolean = withContext(config.ioDispatcher) {
    try {
        Socket().use { it.connect(InetSocketAddress(host, port), config.connectTimeout.inWholeMilliseconds.toInt()) }
        true
    } catch (e: IOException) {
        false
    }
}
