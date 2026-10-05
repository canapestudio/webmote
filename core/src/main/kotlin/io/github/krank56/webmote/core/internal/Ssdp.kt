package io.github.krank56.webmote.core.internal

import io.github.krank56.webmote.core.TvCandidate
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder

/**
 * SSDP as LG TVs speak it (research §11). A TV answers the webOS second-screen search with a
 * response that carries no name; its DLNA MediaRenderer, a separate UPnP device at the same IP,
 * answers its own search with the friendly name in `DLNADeviceName.lge.com`. Responses are
 * correlated by host.
 */
internal object Ssdp {
    const val SECOND_SCREEN = "urn:lge-com:service:webos-second-screen:1"
    const val MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"

    /** The name of a TV whose MediaRenderer didn't answer. */
    const val DEFAULT_NAME = "LG webOS TV"

    /** An M-SEARCH for [searchTarget]. The HOST header is always the multicast group, as SSDP requires. */
    fun search(searchTarget: String): ByteArray = (
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: $searchTarget\r\n" +
            "\r\n"
        ).toByteArray(Charsets.US_ASCII)
}

/** One answer to an M-SEARCH: who answered, for which search target, and the name it announced. */
internal class SsdpResponse(val host: String, val searchTarget: String?, val name: String?) {
    companion object {
        /**
         * Parses a response that arrived from [source]. Its host is the one in its `LOCATION` URL,
         * else [source]. Returns null for anything but a `200 OK` response.
         */
        fun parse(text: String, source: InetAddress): SsdpResponse? {
            val lines = text.split("\r\n", "\n")
            val status = lines.first().split(' ')
            if (!status[0].startsWith("HTTP/", ignoreCase = true) || status.getOrNull(1) != "200") return null
            val headers = lines.drop(1).mapNotNull { line ->
                val colon = line.indexOf(':').takeIf { it > 0 } ?: return@mapNotNull null
                line.substring(0, colon).trim().lowercase() to line.substring(colon + 1).trim()
            }.toMap()
            return SsdpResponse(
                host = headers["location"]?.let(::hostOf) ?: source.hostAddress,
                searchTarget = headers["st"],
                name = headers["dlnadevicename.lge.com"]?.let(::decodeName),
            )
        }

        private fun hostOf(location: String): String? =
            runCatching { URI(location).host }.getOrNull()?.removeSurrounding("[", "]")?.takeIf { it.isNotEmpty() }

        /** `%5bLG%5d%20webOS%20TV%20SM8200PLA` is `LG webOS TV SM8200PLA`. */
        private fun decodeName(encoded: String): String? {
            // Only percent-escapes are encoding here: a literal + stays a +.
            val decoded = runCatching { URLDecoder.decode(encoded.replace("+", "%2B"), Charsets.UTF_8) }.getOrDefault(encoded)
            return decoded.replace("[LG]", "LG").trim().takeIf { it.isNotEmpty() }
        }
    }
}

/**
 * Turns responses into candidates: one per host that answered the second-screen search, named
 * from any response from the same host.
 *
 * A TV whose name is already known is ready at once. One whose name hasn't come yet waits until
 * the search ends, since its MediaRenderer may answer up to MX seconds later, and is then named
 * [Ssdp.DEFAULT_NAME] if it never does. Each host is returned once, so a name learned after that
 * is ignored.
 */
internal class SsdpCandidates {
    private val tvHosts = LinkedHashSet<String>()
    private val names = HashMap<String, String>()
    private val returned = HashSet<String>()

    /** Records [response] and returns the candidates it made ready. */
    fun add(response: SsdpResponse): List<TvCandidate> {
        if (response.name != null) names.putIfAbsent(response.host, response.name)
        if (response.searchTarget.equals(Ssdp.SECOND_SCREEN, ignoreCase = true)) tvHosts += response.host
        return take { it in names }
    }

    /** The candidates still waiting for a name, once the search has ended. */
    fun finish(): List<TvCandidate> = take { true }

    private fun take(ready: (String) -> Boolean): List<TvCandidate> =
        tvHosts.filter { it !in returned && ready(it) }.map { host ->
            returned += host
            TvCandidate(names[host] ?: Ssdp.DEFAULT_NAME, host)
        }
}
