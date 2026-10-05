package io.github.krank56.webmote.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tunables and network targets for a [TvSession]. The defaults are the production values; tests aim
 * the ports and Wake-on-LAN target at a fake TV on loopback.
 */
public data class SessionConfig(
    /** The encrypted SSAP port. */
    val port: Int = 3001,
    /** The plain port only pre-2018 TVs rely on; used to tell them apart from TVs that are off. */
    val legacyPort: Int = 3000,
    /** Where magic packets go: the broadcast address and the Wake-on-LAN port. */
    val wakeAddress: String = "255.255.255.255",
    val wakePort: Int = 9,
    /**
     * The phone's IPv4 networks. Magic packets also go to the broadcast address of the network the TV
     * is on: a VPN can capture [wakeAddress] while leaving the local network alone.
     */
    val localNetworks: () -> List<LocalNetwork> = LocalNetwork::current,
    /** How long a wake waits for the TV before giving up. Tune it on the test TV. */
    val wakeTimeout: Duration = 20.seconds,
    /** How often a waking or off TV is probed. */
    val pollInterval: Duration = 1.seconds,
    /** Volume slider writes are sent at most this often while dragging. */
    val throttleInterval: Duration = 150.milliseconds,
    /** How long to wait for the TV to answer an SSAP request. */
    val requestTimeout: Duration = 10.seconds,
    /** Real-time socket timeout for TCP connections and probes to the TV. */
    val connectTimeout: Duration = 3.seconds,
    /** Where blocking network I/O runs. The registry's small file writes stay on the session's dispatcher. */
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
)

/** An IPv4 network the phone is on: one of its addresses there, the prefix length, and its broadcast address. */
public data class LocalNetwork(val address: String, val prefixLength: Int, val broadcast: String) {

    /** Whether [ip] is on this network. */
    internal fun contains(ip: Inet4Address): Boolean {
        val own = runCatching { InetAddress.getByName(address) }.getOrNull() as? Inet4Address ?: return false
        val mask = if (prefixLength <= 0) 0 else -1 shl (32 - prefixLength.coerceAtMost(32))
        return (own.bits() and mask) == (ip.bits() and mask)
    }

    public companion object {
        /** The networks of this device's interfaces that are up and have a broadcast address. Blocking. */
        public fun current(): List<LocalNetwork> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { entry ->
                    val ip = entry.address as? Inet4Address ?: return@mapNotNull null
                    val broadcast = entry.broadcast ?: return@mapNotNull null
                    LocalNetwork(ip.hostAddress, entry.networkPrefixLength.toInt(), broadcast.hostAddress)
                }
        }.getOrDefault(emptyList())

        private fun Inet4Address.bits(): Int = address.fold(0) { bits, byte -> (bits shl 8) or (byte.toInt() and 0xFF) }
    }
}
