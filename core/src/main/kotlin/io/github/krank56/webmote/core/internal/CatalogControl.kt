package io.github.krank56.webmote.core.internal

import io.github.krank56.webmote.core.TvApp
import io.github.krank56.webmote.core.TvInput
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The TV's inputs and apps, and per-TV favourite apps. */
internal class CatalogControl(private val core: SessionCore) : Feature {
    /** The apps in the order the TV listed them, and the TV they came from. */
    private var listed: ListedApps? = null

    private class ListedApps(val tvId: String, val apps: List<TvApp>)

    override fun onConnected(link: Link) {
        link.scope.launch { loadInputs(link) }
        link.scope.launch { loadApps(link) }
    }

    fun switchInput(inputId: String) = core.withLink {
        it.socket.request("ssap://tv/switchInput", buildJsonObject { put("inputId", inputId) })
    }

    fun launchApp(appId: String) = core.withLink {
        it.socket.request("ssap://system.launcher/launch", buildJsonObject { put("id", appId) })
    }

    /** Pins or unpins [appId] for the TV whose apps are shown, and re-sorts them straight away. */
    fun toggleFavourite(appId: String) {
        val tvId = core.state.value.tvId ?: return
        core.registry.toggleFavourite(tvId, appId)
        showApps(tvId)
    }

    /**
     * Fetches an app icon with the TV's pinned trust. Returns null if it can't be loaded.
     *
     * Only URLs on the TV's own host are fetched. TVs list icons on the plain port, but newer ones
     * only serve the encrypted one, so a failed plain fetch is retried there.
     */
    suspend fun loadIcon(url: String): ByteArray? {
        val link = core.link ?: return null
        val icon = url.toHttpUrlOrNull()?.takeIf { it.host.equals(link.host.removeSurrounding("[", "]"), ignoreCase = true) }
            ?: return null
        val client = link.http.newBuilder()
            .callTimeout(core.config.requestTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .build()
        return download(client, icon)
            ?: icon.takeIf { !it.isHttps && it.port == core.config.legacyPort }
                ?.let { download(client, it.newBuilder().scheme("https").port(core.config.port).build()) }
    }

    private suspend fun loadInputs(link: Link) {
        val devices = fetch(link, "ssap://tv/getExternalInputList")?.objects("devices") ?: return
        val inputs = devices.mapNotNull { device ->
            val id = device.string("id") ?: return@mapNotNull null
            TvInput(id, label = device.string("label")?.takeIf { it.isNotBlank() } ?: id)
        }
        core.update { it.copy(inputs = inputs) }
    }

    private suspend fun loadApps(link: Link) {
        val launchPoints = fetch(link, "ssap://com.webos.applicationManager/listLaunchPoints")?.objects("launchPoints") ?: return
        val apps = launchPoints.mapNotNull { point ->
            val id = point.string("id") ?: return@mapNotNull null
            TvApp(id, title = point.string("title") ?: id, iconUrl = iconUrl(point), pinned = false)
        }
        listed = ListedApps(link.tvId, apps.distinctBy { it.id })
        showApps(link.tvId)
    }

    /** Publishes [tvId]'s apps with its favourites first, in the order they were pinned. */
    private fun showApps(tvId: String) {
        val apps = listed?.takeIf { it.tvId == tvId }?.apps ?: return
        val favourites = core.registry[tvId]?.favourites.orEmpty()
        val byId = apps.associateBy { it.id }
        val pinned = favourites.mapNotNull { byId[it]?.copy(pinned = true) }
        val pinnedIds = pinned.map { it.id }.toSet()
        val rest = apps.filter { it.id !in pinnedIds }
        core.update { if (it.tvId == tvId) it.copy(apps = pinned + rest) else it }
    }

    private suspend fun fetch(link: Link, uri: String): JsonObject? = try {
        link.socket.request(uri)
    } catch (_: SsapException) {
        null
    } catch (_: IOException) {
        null
    } catch (_: TimeoutCancellationException) {
        null
    }

    private suspend fun download(client: OkHttpClient, url: HttpUrl): ByteArray? = withContext(core.config.ioDispatcher) {
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) response.body.bytes() else null
            }
        } catch (_: IOException) {
            null
        }
    }

    private companion object {
        /** `largeIcon` when it's a full URL (it can be a bare file name), else `icon`. */
        fun iconUrl(launchPoint: JsonObject): String? =
            listOf("largeIcon", "icon").firstNotNullOfOrNull { key -> launchPoint.string(key)?.takeIf { it.toHttpUrlOrNull() != null } }

        fun JsonObject.objects(key: String): List<JsonObject> = (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    }
}
