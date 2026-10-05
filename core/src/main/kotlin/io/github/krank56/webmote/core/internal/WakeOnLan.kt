package io.github.krank56.webmote.core.internal

import io.github.krank56.webmote.core.LocalNetwork
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress

/** Wake-on-LAN: MAC addresses and magic packets. */
internal object WakeOnLan {

    /** [raw] as upper-case, colon-separated hex (`60:75:6C:27:A9:02`), or null if it isn't a MAC address. */
    fun normaliseMac(raw: String): String? {
        val hex = raw.filterNot { it in MAC_SEPARATORS }.uppercase()
        if (hex.length != 12 || !hex.all { it in '0'..'9' || it in 'A'..'F' }) return null
        return hex.chunked(2).joinToString(":")
    }

    /** The magic packet for [mac]: six `FF` bytes, then the MAC 16 times. */
    fun magicPacket(mac: String): ByteArray {
        val bytes = normaliseMac(mac)?.split(':')?.map { it.toInt(16).toByte() }?.toByteArray()
            ?: throw IllegalArgumentException("Not a MAC address: $mac")
        return ByteArray(6) { 0xFF.toByte() } + (1..16).fold(ByteArray(0)) { packet, _ -> packet + bytes }
    }

    /**
     * Where magic packets for the TV at [host] go: the broadcast address of the network it's on, if
     * the phone is on it too, then [fallback]. Blocking.
     *
     * The network's own broadcast address is what gets through when a VPN captures the general
     * broadcast address (255.255.255.255) but leaves the local network alone.
     */
    fun addresses(host: String, networks: List<LocalNetwork>, fallback: String): List<String> {
        val tv = runCatching { InetAddress.getByName(host) }.getOrNull() as? Inet4Address
        val ownNetwork = tv?.let { ip -> networks.firstOrNull { it.contains(ip) }?.broadcast }
        return listOfNotNull(ownNetwork, fallback).distinct()
    }

    /**
     * Sends one magic packet per MAC, with broadcast enabled, to each of [addresses] on [port]. Blocking.
     * A TV only listens on the adapter it uses, so every MAC it reported gets one. Network errors are
     * ignored, address by address: the wake then times out like any other TV that doesn't answer.
     */
    fun send(macs: List<String>, addresses: List<String>, port: Int) {
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                for (address in addresses) {
                    try {
                        val target = InetAddress.getByName(address)
                        for (mac in macs) {
                            val packet = magicPacket(mac)
                            socket.send(DatagramPacket(packet, packet.size, target, port))
                        }
                    } catch (e: IOException) {
                        // This address isn't reachable from here; the others may be.
                    }
                }
            }
        } catch (e: IOException) {
            // No usable network: nothing to do but let the wake time out.
        }
    }

    private const val MAC_SEPARATORS = ":-. "
}
