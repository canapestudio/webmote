package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.faketv.ScreenOffUris.PANEL
import io.github.krank56.webmote.core.faketv.ScreenOffUris.TVPOWER_OFF
import io.github.krank56.webmote.core.faketv.ScreenOffUris.TVPOWER_ON
import io.github.krank56.webmote.core.faketv.ScreenOffUris.WEBOS4_OFF
import io.github.krank56.webmote.core.faketv.ScreenOffUris.WEBOS4_ON
import io.github.krank56.webmote.core.faketv.ScreenOffUris
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

class ScreenOffTest {
    @TempDir lateinit var dir: File
    private val h by lazy { Harness(dir) }

    @AfterEach fun tearDown() = h.close()

    @Test
    fun `screen off tries each method in order until the TV takes one`() {
        h.tv.screenOffUris = setOf(TVPOWER_OFF, TVPOWER_ON)
        h.pair()

        h.session.screenOff()

        h.awaitRequests(TVPOWER_OFF)
        h.settle()
        assertEquals(listOf(PANEL, TVPOWER_OFF), screenRequests())
        assertEquals("false", h.tv.requests(PANEL).single().string("OnOff"))
        assertEquals("active", h.tv.requests(TVPOWER_OFF).single().string("standbyMode"))
    }

    @Test
    fun `webOS 4 gets tv-power turnOffScreen with standbyMode active`() {
        h.tv.screenOffUris = setOf(WEBOS4_OFF, WEBOS4_ON)
        h.pair()

        h.session.screenOff()

        h.awaitRequests(WEBOS4_OFF)
        assertEquals(listOf(PANEL, TVPOWER_OFF, WEBOS4_OFF), screenRequests())
        assertEquals("active", h.tv.requests(WEBOS4_OFF).single().string("standbyMode"))
    }

    @Test
    fun `the method that worked is remembered for the TV and tried alone next time, even after reopening the app`() {
        h.tv.screenOffUris = setOf(TVPOWER_OFF, TVPOWER_ON)
        h.pair()
        h.session.screenOff()
        h.awaitRequests(TVPOWER_OFF)
        h.eventually(message = { "the method stored" }) { h.registry[h.tv.uuid]?.screenOffMethod != null }

        h.reopen()
        h.session.connect()
        h.awaitConnection(ConnectionState.Connected)
        h.session.screenOff()

        h.awaitRequests(TVPOWER_OFF, count = 2)
        h.settle()
        assertEquals(listOf(PANEL, TVPOWER_OFF, TVPOWER_OFF), screenRequests())
    }

    @Test
    fun `a remembered method that stops working falls back to the full order, and the new one is remembered`() {
        h.tv.screenOffUris = setOf(TVPOWER_OFF, TVPOWER_ON)
        h.pair()
        h.session.screenOff()
        h.awaitRequests(TVPOWER_OFF)

        h.tv.screenOffUris = setOf(PANEL) // A firmware update changed what the TV offers.
        h.session.screenOff()
        h.awaitRequests(PANEL, count = 2)
        h.session.screenOff()
        h.awaitRequests(PANEL, count = 3)
        h.settle()

        assertEquals(listOf(PANEL, TVPOWER_OFF, TVPOWER_OFF, PANEL, PANEL), screenRequests())
    }

    @Test
    fun `screen on uses the on call of the remembered method`() {
        h.pair()
        h.session.screenOff()
        h.awaitRequests(PANEL)

        h.session.screenOn()

        h.awaitRequests(PANEL, count = 2)
        h.settle()
        assertEquals(listOf(PANEL, PANEL), screenRequests())
        assertEquals("true", h.tv.requests(PANEL).last().string("OnOff"))
    }

    @Test
    fun `screen on on webOS 4 sends turnOnScreen with standbyMode active and ignores the error it answers with`() {
        h.tv.screenOffUris = setOf(WEBOS4_OFF, WEBOS4_ON)
        h.tv.on(WEBOS4_ON) { throw io.github.krank56.webmote.core.faketv.TvError("500 Application error") }
        h.pair()
        h.session.screenOff()
        h.awaitRequests(WEBOS4_OFF)

        h.session.screenOn()
        h.awaitRequests(WEBOS4_ON)
        h.session.screenOn()
        h.awaitRequests(WEBOS4_ON, count = 2)
        h.settle()

        assertEquals(listOf(PANEL, TVPOWER_OFF, WEBOS4_OFF, WEBOS4_ON, WEBOS4_ON), screenRequests())
        assertEquals(listOf("active", "active"), h.tv.requests(WEBOS4_ON).map { it.string("standbyMode") })
        assertEquals(ConnectionState.Connected, h.state.connection)
    }

    @Test
    fun `screen on without a remembered method tries the order until one works`() {
        h.tv.screenOffUris = setOf(TVPOWER_OFF, TVPOWER_ON)
        h.pair()

        h.session.screenOn()

        h.awaitRequests(TVPOWER_ON)
        h.settle()
        assertEquals(listOf(PANEL, TVPOWER_ON), screenRequests())
        assertEquals("true", h.tv.requests(PANEL).single().string("OnOff"))
        assertEquals("active", h.tv.requests(TVPOWER_ON).single().string("standbyMode"))
    }

    /** The screen off/on requests the TV received, in order. */
    private fun screenRequests(): List<String> = h.tv.requests.mapNotNull { it.uri }.filter { it in ScreenOffUris.all }
}
