package io.github.krank56.webmote.core.internal

import io.github.krank56.webmote.core.TvInput
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException

/** The TV's inputs. */
internal class CatalogControl(private val core: SessionCore) : Feature {
    override fun onConnected(link: Link) {
        link.scope.launch { loadInputs(link) }
    }

    fun switchInput(inputId: String) = core.withLink {
        it.socket.request("ssap://tv/switchInput", buildJsonObject { put("inputId", inputId) })
    }

    private suspend fun loadInputs(link: Link) {
        val devices = fetch(link, "ssap://tv/getExternalInputList")?.objects("devices") ?: return
        val inputs = devices.mapNotNull { device ->
            val id = device.string("id") ?: return@mapNotNull null
            TvInput(id, label = device.string("label")?.takeIf { it.isNotBlank() } ?: id)
        }
        core.update { it.copy(inputs = inputs) }
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

    private companion object {
        fun JsonObject.objects(key: String): List<JsonObject> = (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    }
}
