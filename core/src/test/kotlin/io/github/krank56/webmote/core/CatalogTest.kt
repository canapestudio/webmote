package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.faketv.FakeTv
import io.github.krank56.webmote.core.faketv.FakeTv.Companion.ok
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogTest {
    @TempDir lateinit var dir: File
    private val h by lazy { Harness(dir) }

    @AfterEach fun tearDown() = h.close()

    // Inputs

    @Test
    fun `the TV's inputs are listed`() {
        h.pair()

        h.eventually { h.state.inputs.isNotEmpty() }
        assertEquals(
            listOf(TvInput("HDMI_1", "HDMI 1"), TvInput("HDMI_2", "HDMI 2"), TvInput("HDMI_3", "HDMI 3")),
            h.state.inputs,
        )
    }

    @Test
    fun `inputs without an id are skipped, and an input without a label is shown by its id`() {
        h.tv.on(INPUT_LIST) {
            ok {
                putJsonArray("devices") {
                    addJsonObject { put("label", "Mystery") }
                    addJsonObject { put("id", "AV_1") }
                    addJsonObject { put("id", "HDMI_2"); put("label", "THE-POWERHOUSE"); put("appId", "com.webos.app.hdmi2") }
                }
            }
        }
        h.pair()

        h.eventually { h.state.inputs.isNotEmpty() }
        assertEquals(listOf(TvInput("AV_1", "AV_1"), TvInput("HDMI_2", "THE-POWERHOUSE")), h.state.inputs)
    }

    @Test
    fun `switching input sends the input's id`() {
        h.pair()

        h.session.switchInput("HDMI_2")

        assertEquals("HDMI_2", h.awaitRequests("ssap://tv/switchInput").single().string("inputId"))
    }

    // Apps

    @Test
    fun `the TV's apps are listed in its order, with their icons`() {
        h.pair()

        h.eventually { h.state.apps.isNotEmpty() }
        assertEquals(
            listOf(
                TvApp("netflix", "Netflix", iconUrl("netflix"), pinned = false),
                TvApp("youtube.leanback.v4", "YouTube", iconUrl("youtube.leanback.v4"), pinned = false),
                TvApp("com.webos.app.livetv", "Live TV", iconUrl("com.webos.app.livetv"), pinned = false),
            ),
            h.state.apps,
        )
    }

    @Test
    fun `an app's large icon is used when it's a full URL, and an app listed twice appears once`() {
        val base = "https://${h.tv.host}:${h.tv.port}/resources/abc"
        h.tv.on(LAUNCH_POINTS) {
            ok {
                putJsonArray("launchPoints") {
                    addJsonObject { put("id", "large"); put("title", "Large"); put("largeIcon", "$base/l.png"); put("icon", "$base/s.png") }
                    addJsonObject { put("id", "relative"); put("title", "Relative"); put("largeIcon", "largeIcon.png"); put("icon", "$base/r.png") }
                    addJsonObject { put("id", "none"); put("title", "None"); put("icon", "/usr/palm/applications/none/icon.png") }
                    addJsonObject { put("title", "No id") }
                    addJsonObject { put("id", "large"); put("title", "Large again") }
                }
            }
        }
        h.pair()

        h.eventually { h.state.apps.isNotEmpty() }
        assertEquals(
            listOf(
                TvApp("large", "Large", "$base/l.png", pinned = false),
                TvApp("relative", "Relative", "$base/r.png", pinned = false),
                TvApp("none", "None", null, pinned = false),
            ),
            h.state.apps,
        )
    }

    @Test
    fun `launching an app sends its id`() {
        h.pair()

        h.session.launchApp("youtube.leanback.v4")

        assertEquals("youtube.leanback.v4", h.awaitRequests("ssap://system.launcher/launch").single().string("id"))
    }

    // Favourites

    @Test
    fun `pinned apps come first, in the order they were pinned`() {
        pairAndList()

        h.session.toggleFavourite("com.webos.app.livetv")
        h.session.toggleFavourite("youtube.leanback.v4")

        h.eventually { h.state.apps.count { it.pinned } == 2 }
        assertEquals(listOf("com.webos.app.livetv", "youtube.leanback.v4", "netflix"), h.state.apps.map { it.id })
        assertEquals(listOf(true, true, false), h.state.apps.map { it.pinned })
        assertEquals(listOf("com.webos.app.livetv", "youtube.leanback.v4"), h.registry[h.tv.uuid]!!.favourites)
    }

    @Test
    fun `unpinning an app puts it back in the TV's order`() {
        pairAndList()
        h.session.toggleFavourite("com.webos.app.livetv")
        h.eventually { h.state.apps.first().pinned }

        h.session.toggleFavourite("com.webos.app.livetv")

        h.eventually { h.state.apps.none { it.pinned } }
        assertEquals(listOf("netflix", "youtube.leanback.v4", "com.webos.app.livetv"), h.state.apps.map { it.id })
        assertEquals(emptyList(), h.registry[h.tv.uuid]!!.favourites)
    }

    @Test
    fun `favourites are kept across app restarts`() {
        pairAndList()
        h.session.toggleFavourite("youtube.leanback.v4")
        h.eventually { h.state.apps.first().pinned }

        h.reopen()
        h.session.connect()
        h.awaitConnection(ConnectionState.Connected)

        h.eventually { h.state.apps.isNotEmpty() }
        assertEquals(listOf("youtube.leanback.v4", "netflix", "com.webos.app.livetv"), h.state.apps.map { it.id })
        assertTrue(h.state.apps.first().pinned)
    }

    @Test
    fun `favourites are kept separately for each TV`() {
        pairAndList()
        val first = h.tv.uuid
        h.session.toggleFavourite("youtube.leanback.v4")
        h.eventually { h.state.apps.first().pinned }

        // A second TV: the first one moves to another address, and a new one answers at the fake TV's.
        h.session.disconnect()
        h.awaitConnection(ConnectionState.Disconnected)
        h.registry.update(first) { it.copy(host = "192.0.2.10") }
        h.tv.powerOff()
        h.tv.uuid = "fake-tv-uuid-2"
        h.tv.certificate = FakeTv.newCertificate()
        h.pair(name = "Bedroom")
        h.eventually { h.state.apps.isNotEmpty() }

        assertEquals("fake-tv-uuid-2", h.state.tvId)
        assertTrue(h.state.apps.none { it.pinned })

        h.session.toggleFavourite("netflix")
        h.eventually { h.state.apps.first().pinned }
        assertEquals(listOf("netflix"), h.registry["fake-tv-uuid-2"]!!.favourites)
        assertEquals(listOf("youtube.leanback.v4"), h.registry[first]!!.favourites)
    }

    @Test
    fun `favourites the TV no longer lists are left out`() {
        h.pair()
        h.registry.update(h.tv.uuid) { it.copy(favourites = listOf("uninstalled.app", "netflix")) }
        h.session.disconnect()
        h.awaitConnection(ConnectionState.Disconnected)

        h.session.connect()
        h.awaitConnection(ConnectionState.Connected)

        h.eventually { h.state.apps.isNotEmpty() }
        assertEquals(listOf("netflix", "youtube.leanback.v4", "com.webos.app.livetv"), h.state.apps.map { it.id })
        assertEquals(listOf(true, false, false), h.state.apps.map { it.pinned })
    }

    // Icons

    @Test
    fun `icons load from the TV, trusting its pinned certificate`() {
        pairAndList()

        val icon = loadIcon(h.state.apps.first().iconUrl!!)

        assertContentEquals(h.tv.icon("netflix"), icon)
    }

    @Test
    fun `an icon the TV doesn't have loads as nothing`() {
        pairAndList()

        assertNull(loadIcon(iconUrl("not.installed")))
    }

    @Test
    fun `icons listed on the plain port are fetched from the encrypted port when the plain one fails`() {
        val plain = "http://${h.tv.host}:${h.tv.legacyPort}/resources/icons/netflix.png"
        h.tv.on(LAUNCH_POINTS) {
            ok { putJsonArray("launchPoints") { addJsonObject { put("id", "netflix"); put("title", "Netflix"); put("icon", plain) } } }
        }
        pairAndList()
        assertEquals(plain, h.state.apps.single().iconUrl)

        val icon = loadIcon(plain)

        assertContentEquals(h.tv.icon("netflix"), icon)
        assertEquals(listOf("/resources/icons/netflix.png"), h.tv.httpRequests)
    }

    @Test
    fun `icons are only fetched from the TV itself`() {
        pairAndList()

        assertNull(loadIcon("https://localhost:${h.tv.port}/resources/icons/netflix.png"))
        assertNull(loadIcon("ftp://${h.tv.host}/resources/icons/netflix.png"))
        assertNull(loadIcon("not a url"))
        assertTrue(h.tv.httpRequests.isEmpty())
    }

    @Test
    fun `an icon server presenting another certificate is refused`() {
        FakeTv().use { impostor ->
            impostor.powerOn()
            pairAndList()

            assertNull(loadIcon("https://${impostor.host}:${impostor.port}/resources/icons/netflix.png"))
            assertTrue(impostor.httpRequests.isEmpty())
        }
    }

    @Test
    fun `no icons load while disconnected`() {
        pairAndList()
        val url = h.state.apps.first().iconUrl!!
        h.session.disconnect()
        h.awaitConnection(ConnectionState.Disconnected)

        assertNull(loadIcon(url))
    }

    private fun pairAndList() {
        h.pair()
        h.eventually { h.state.apps.isNotEmpty() }
    }

    private fun iconUrl(appId: String) = "https://${h.tv.host}:${h.tv.port}/resources/icons/$appId.png"

    private fun loadIcon(url: String): ByteArray? {
        val icon = CoroutineScope(Dispatchers.Default).async { h.session.loadIcon(url) }
        h.eventually(message = { "icon $url" }) { icon.isCompleted }
        return runBlocking { icon.await() }
    }

    private companion object {
        const val INPUT_LIST = "ssap://tv/getExternalInputList"
        const val LAUNCH_POINTS = "ssap://com.webos.applicationManager/listLaunchPoints"
    }
}
