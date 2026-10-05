package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.faketv.FakeTv
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class DiscoveryTest {
    private val tv = FakeTv()

    @AfterEach fun tearDown() = tv.close()

    /** Discovery aimed at the fake TV's SSDP responder. */
    private fun discovery(timeout: Duration) = Discovery(DiscoveryConfig(address = tv.host, port = tv.ssdpPort, timeout = timeout))

    /** Searches with [discovery] and returns everything it found. */
    private fun search(timeout: Duration = 400.milliseconds): List<TvCandidate> = runBlocking { discovery(timeout).search().toList() }

    @Test
    fun `a TV is listed with the name its MediaRenderer announces`() {
        tv.friendlyName = "[LG] webOS TV SM8200PLA"

        assertEquals(listOf(TvCandidate("LG webOS TV SM8200PLA", "127.0.0.1")), search())
    }

    @Test
    fun `the search asks for the second-screen service and the MediaRenderer, more than once`() {
        search()

        val searches = tv.ssdpSearches.toList()
        fun searchFor(target: String) =
            "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: $target\r\n" +
                "\r\n"
        assertTrue(searches.count { it == searchFor(FakeTv.SECOND_SCREEN) } >= 2, "second-screen searches in $searches")
        assertTrue(searches.count { it == searchFor(FakeTv.MEDIA_RENDERER) } >= 2, "MediaRenderer searches in $searches")
    }

    @Test
    fun `a TV whose MediaRenderer doesn't answer is listed as LG webOS TV`() {
        tv.ssdpResponses = { target -> if (target == FakeTv.SECOND_SCREEN) listOf(tv.secondScreenResponse()) else emptyList() }

        assertEquals(listOf(TvCandidate("LG webOS TV", "127.0.0.1")), search())
    }

    @Test
    fun `a TV that answers several times is listed once`() {
        tv.ssdpResponses = { target -> List(3) { tv.defaultSsdpResponses(target) }.flatten() }

        assertEquals(listOf(TvCandidate("LG webOS TV OLED55C6LA", "127.0.0.1")), search())
    }

    @Test
    fun `several TVs are each listed with their own name, in whatever order their answers come`() {
        val living = "http://192.168.1.20"
        val bedroom = "http://192.168.1.21"
        tv.ssdpResponses = { target ->
            if (target != FakeTv.SECOND_SCREEN) emptyList() else listOf(
                tv.mediaRendererResponse("[LG] Salon été", "$living:1792/"),
                tv.secondScreenResponse("$living:1048/"),
                tv.secondScreenResponse("$bedroom:1048/"),
                tv.mediaRendererResponse("Bedroom 50+", "$bedroom:1792/"),
            )
        }

        assertEquals(
            setOf(TvCandidate("LG Salon été", "192.168.1.20"), TvCandidate("Bedroom 50+", "192.168.1.21")),
            search().toSet(),
        )
    }

    @Test
    fun `devices that don't offer the webOS second-screen service are ignored`() {
        val lgSoundbar = "HTTP/1.1 200 OK\r\nLocation: http://192.168.1.30:1792/\r\n" +
            "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\nDLNADeviceName.lge.com: %5bLG%5d%20Soundbar\r\n\r\n"
        val chromecast = "HTTP/1.1 200 OK\r\nLOCATION: http://192.168.1.31:8008/ssdp/device-desc.xml\r\n" +
            "ST: urn:dial-multiscreen-org:service:dial:1\r\n\r\n"
        val notFound = "HTTP/1.1 404 Not Found\r\nLocation: http://192.168.1.32:1048/\r\n" +
            "ST: urn:lge-com:service:webos-second-screen:1\r\n\r\n"
        val announcement = "NOTIFY * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nLocation: http://192.168.1.33:1048/\r\n" +
            "NT: urn:lge-com:service:webos-second-screen:1\r\nNTS: ssdp:alive\r\n\r\n"
        tv.ssdpResponses = { target ->
            if (target != FakeTv.SECOND_SCREEN) emptyList()
            else listOf(lgSoundbar, chromecast, notFound, announcement, "garbage", "") + tv.secondScreenResponse()
        }

        assertEquals(listOf(TvCandidate("LG webOS TV", "127.0.0.1")), search())
    }

    @Test
    fun `header names are case-insensitive`() {
        tv.ssdpResponses = { target ->
            if (target != FakeTv.SECOND_SCREEN) emptyList() else listOf(
                "HTTP/1.1 200 OK\r\nlocation: http://192.168.1.20:1048/\r\nst: urn:lge-com:service:webos-second-screen:1\r\n" +
                    "dlnadevicename.LGE.COM: Kitchen\r\n\r\n",
            )
        }

        assertEquals(listOf(TvCandidate("Kitchen", "192.168.1.20")), search())
    }

    @Test
    fun `a TV whose answer has no LOCATION is found at the address it answered from`() {
        tv.ssdpResponses = { target -> if (target == FakeTv.SECOND_SCREEN) listOf(tv.secondScreenResponse(location = null)) else emptyList() }

        assertEquals(listOf(TvCandidate("LG webOS TV", "127.0.0.1")), search())
    }

    @Test
    fun `a TV whose name is known is listed straight away, and stopping the search early is prompt`() {
        val elapsed = measureTime {
            val first = runBlocking { discovery(timeout = 10.seconds).search().first() }
            assertEquals(TvCandidate("LG webOS TV OLED55C6LA", "127.0.0.1"), first)
        }

        assertTrue(elapsed < 2.seconds, "took $elapsed")
    }

    @Test
    fun `the search completes after its timeout without blocking the collecting thread`() = runBlocking {
        var ticks = 0
        val ticker = launch {
            while (true) {
                delay(20)
                ticks++
            }
        }

        val elapsed = measureTime { discovery(timeout = 600.milliseconds).search().toList() }
        ticker.cancel()

        assertTrue(elapsed >= 600.milliseconds && elapsed < 2.seconds, "took $elapsed")
        assertTrue(ticks >= 10, "the collecting thread ran only $ticks ticks")
    }

    @Test
    fun `a discovered TV is paired under the name it announced`(@TempDir dir: File) {
        Harness(dir, tv).use { h ->
            val candidate = search().single()
            h.tv.powerOn()

            h.session.connect(candidate.host, candidate.name)
            h.awaitConnection(ConnectionState.Connected)

            assertEquals("LG webOS TV OLED55C6LA", h.registry[tv.uuid]?.name)
        }
    }
}
