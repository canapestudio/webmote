package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.faketv.FakeTv
import io.github.krank56.webmote.core.faketv.TvError
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerTest {
    @TempDir lateinit var dir: File
    private val h by lazy { Harness(dir) }

    @AfterEach fun tearDown() = h.close()

    @Test
    fun `the session subscribes to the TV's foreground app and reports it`() {
        h.tv.foregroundApp = LIVE_TV
        h.pair()

        h.eventually { h.state.foregroundAppId == LIVE_TV }
        assertEquals("subscribe", h.awaitRequests(FakeTv.GET_FOREGROUND_APP).single().type)
    }

    @Test
    fun `the foreground app follows apps started with the physical remote`() {
        pairAndFollow()

        bringToFront(NETFLIX)
        h.eventually { h.state.foregroundAppId == NETFLIX }

        bringToFront(LIVE_TV)
        h.eventually { h.state.foregroundAppId == LIVE_TV }
    }

    @Test
    fun `a known player app in front is reported as the player, its playback state unknown`() {
        pairAndFollow()

        PLAYERS.forEach { appId ->
            bringToFront(appId)
            h.eventually(message = { "$appId as the player" }) { h.state.foregroundAppId == appId }
            assertEquals(Player(appId, PlaybackState.Unknown), h.state.player)
        }
    }

    @Test
    fun `live TV, an HDMI input and the home screen are never players, and replace the player`() {
        pairAndFollow()
        assertNull(h.state.player)

        listOf(LIVE_TV, "com.webos.app.hdmi1", FakeTv.HOME_SCREEN).forEach { appId ->
            bringToFront(NETFLIX)
            h.eventually { h.state.player?.appId == NETFLIX }

            bringToFront(appId)
            h.eventually(message = { "$appId in front" }) { h.state.foregroundAppId == appId }
            assertNull(h.state.player)
        }
    }

    @Test
    fun `an empty app ID counts as no foreground app`() {
        pairAndFollow()
        bringToFront(NETFLIX)
        h.eventually { h.state.player?.appId == NETFLIX }

        bringToFront("")

        h.eventually { h.state.player == null }
        assertNull(h.state.foregroundAppId)
    }

    @Test
    fun `seeking in a player sends the media controls' rewind and fast-forward`() {
        pairAndFollow()
        bringToFront(YOUTUBE)
        h.eventually { h.state.player?.appId == YOUTUBE }

        h.session.seekBack()
        h.session.seekForward()
        h.session.seekBack()

        h.eventually { mediaRequests().size == 3 }
        assertEquals(listOf(REWIND, FAST_FORWARD, REWIND), mediaRequests().map { it.uri })
        assertEquals(List(3) { JsonObject(emptyMap()) }, mediaRequests().map { it.payload })
    }

    @Test
    fun `seeking with no player in front falls back to rewind and fast-forward`() {
        pairAndFollow()
        assertNull(h.state.player)

        h.session.seekBack()
        h.session.seekForward()

        h.eventually { mediaRequests().size == 2 }
        assertEquals(listOf(REWIND, FAST_FORWARD), mediaRequests().map { it.uri })
    }

    @Test
    fun `a TV that won't report its foreground app has no player, and everything else still works`() {
        h.tv.on(FakeTv.GET_FOREGROUND_APP) { throw TvError("401 insufficient permissions") }
        h.pair()
        h.awaitRequests(FakeTv.GET_FOREGROUND_APP)

        h.session.seekForward()
        h.session.volumeUp()

        h.awaitRequests(FAST_FORWARD)
        h.awaitRequests("ssap://audio/volumeUp")
        assertEquals(ConnectionState.Connected, h.state.connection)
        assertNull(h.state.foregroundAppId)
        assertNull(h.state.player)
    }

    @Test
    fun `disconnecting forgets the foreground app and the player`() {
        pairAndFollow()
        bringToFront(NETFLIX)
        h.eventually { h.state.player?.appId == NETFLIX }

        h.session.disconnect()
        h.awaitConnection(ConnectionState.Disconnected)

        h.eventually { h.state.foregroundAppId == null }
        assertNull(h.state.player)
    }

    @Test
    fun `switching TVs resets the foreground app and the player`() {
        FakeTv(host = "::1", port = h.tv.port, legacyPort = h.tv.legacyPort).use { bedroom ->
            bedroom.uuid = "fake-tv-uuid-2"
            h.pair("Living room")
            bedroom.powerOn()
            h.session.connect(bedroom.host, "Bedroom")
            h.eventually { h.state.tvId == bedroom.uuid && h.state.foregroundAppId == FakeTv.HOME_SCREEN }
            bedroom.push(FakeTv.GET_FOREGROUND_APP, FakeTv.foregroundAppPayload(NETFLIX))
            h.eventually { h.state.player?.appId == NETFLIX }

            // The living room TV won't say which app is in front, so nothing it reports can stand in for the reset.
            h.tv.on(FakeTv.GET_FOREGROUND_APP) { throw TvError("401 insufficient permissions") }
            h.session.switchTo(h.tv.uuid)
            h.eventually { h.state.tvId == h.tv.uuid && h.state.connection == ConnectionState.Connected }
            h.awaitRequests(FakeTv.GET_FOREGROUND_APP, 2)
            h.settle()

            assertNull(h.state.foregroundAppId)
            assertNull(h.state.player)
        }
    }

    /** Pairs and waits until the session follows the foreground app, so pushes reach it. */
    private fun pairAndFollow() {
        h.pair()
        h.eventually { h.state.foregroundAppId == FakeTv.HOME_SCREEN }
    }

    /** Brings [appId] to the front on the TV, as the physical remote would. */
    private fun bringToFront(appId: String) = h.tv.push(FakeTv.GET_FOREGROUND_APP, FakeTv.foregroundAppPayload(appId))

    private fun mediaRequests() = h.tv.requests.filter { it.uri?.startsWith("ssap://media.controls/") == true }

    private companion object {
        const val LIVE_TV = "com.webos.app.livetv"
        const val NETFLIX = "netflix"
        const val YOUTUBE = "youtube.leanback.v4"
        const val REWIND = "ssap://media.controls/rewind"
        const val FAST_FORWARD = "ssap://media.controls/fastForward"

        /** YouTube, Netflix, Prime Video, Disney+ and Plex. */
        val PLAYERS = listOf(YOUTUBE, NETFLIX, "amazon", "com.disney.disneyplus-prod", "cdp-30")
    }
}
