package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.internal.Ssdp
import io.github.krank56.webmote.core.internal.SsdpCandidates
import io.github.krank56.webmote.core.internal.SsdpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** A TV found on the local network, ready to pass to [TvSession.connect]. */
public data class TvCandidate(val name: String, val host: String)

/** Where SSDP searches go. Tests aim them at a fake TV's responder on loopback. */
public data class DiscoveryConfig(
    val address: String = "239.255.255.250",
    val port: Int = 1900,
    /** How long a search listens for answers. */
    val timeout: Duration = 3.seconds,
)

/**
 * Finds LG TVs on the local network with an SSDP search for the webOS second-screen service.
 *
 * On Android the caller must hold a Wi-Fi multicast lock while searching.
 */
public class Discovery(private val config: DiscoveryConfig = DiscoveryConfig()) {

    /**
     * Emits each TV that answers, once per host, and completes after [DiscoveryConfig.timeout].
     *
     * Each TV is named from its DLNA MediaRenderer's answer, which the search also asks for. A TV
     * is emitted as soon as both answers are in; one whose MediaRenderer doesn't answer is emitted
     * when the search ends, named "LG webOS TV". The search runs on [Dispatchers.IO].
     */
    public fun search(): Flow<TvCandidate> = flow {
        DatagramSocket().use { socket ->
            val target = InetSocketAddress(config.address, config.port)
            val searches = listOf(Ssdp.search(Ssdp.SECOND_SCREEN), Ssdp.search(Ssdp.MEDIA_RENDERER))
            // Leave time for answers to the last search, which a TV may delay by up to MX seconds.
            val resendInterval = config.timeout / (SEARCHES + 1)
            val candidates = SsdpCandidates()
            val buffer = ByteArray(BUFFER_SIZE)
            val started = TimeSource.Monotonic.markNow()
            var sent = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val elapsed = started.elapsedNow()
                if (elapsed >= config.timeout) break
                if (sent < SEARCHES && elapsed >= resendInterval * sent) {
                    // UDP is lossy, so each search goes out more than once; the TV answers each.
                    searches.forEach { bytes ->
                        try {
                            socket.send(DatagramPacket(bytes, bytes.size, target))
                        } catch (_: IOException) {
                            // No usable network right now; keep listening until the timeout.
                        }
                    }
                    sent++
                }
                val nextSearch = if (sent < SEARCHES) resendInterval * sent - elapsed else Duration.INFINITE
                socket.soTimeout = minOf(config.timeout - elapsed, nextSearch, POLL).inWholeMilliseconds.coerceAtLeast(1).toInt()
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                val response = SsdpResponse.parse(text, packet.address) ?: continue
                candidates.add(response).forEach { emit(it) }
            }
            candidates.finish().forEach { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        /** How many times each M-SEARCH is sent, spread over the first part of the search. */
        const val SEARCHES = 2
        const val BUFFER_SIZE = 8 * 1024

        /** How often a blocked receive wakes up to notice cancellation. */
        val POLL = 100.milliseconds
    }
}
