package io.github.krank56.webmote.core.internal

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Screen off/on: the picture goes off while the sound keeps playing.
 *
 * Firmware generations implement it differently, so screen off tries each [ScreenMethod] in order and
 * remembers in the registry the one that worked for the TV; next time only that one is tried, unless
 * it stops working. The TV pushes no screen state, so none is reported.
 */
internal class ScreenControl(private val core: SessionCore) {

    fun off() = core.withLink { link ->
        val remembered = ScreenMethod.withKey(core.registry[link.tvId]?.screenOffMethod)
        val order = listOfNotNull(remembered) + ScreenMethod.entries.filter { it != remembered }
        val worked = order.firstOrNull { link.succeeds(it.offUri, it.offPayload) } ?: return@withLink
        if (worked != remembered) core.registry.update(link.tvId) { it.copy(screenOffMethod = worked.key) }
    }

    /** Uses the on call of the method that turned the screen off, or tries them in order if none is known. */
    fun on() = core.withLink { link ->
        val remembered = ScreenMethod.withKey(core.registry[link.tvId]?.screenOffMethod)
        if (remembered != null) {
            link.turnOn(remembered)
        } else {
            ScreenMethod.entries.firstOrNull { link.turnOn(it) }
        }
    }

    /** Whether the screen came on. webOS 4's call answers with an error even when it works (research §7). */
    private suspend fun Link.turnOn(method: ScreenMethod): Boolean = succeeds(method.onUri, method.onPayload) || method.onErrorExpected

    /** Whether the TV took the request; an error means it doesn't offer this method. */
    private suspend fun Link.succeeds(uri: String, payload: JsonObject): Boolean = try {
        socket.request(uri, payload)
        true
    } catch (e: SsapException) {
        false
    }
}

/**
 * The screen off/on calls, in the order they're tried. `standbyMode` is always `active`: `passive`
 * can't turn the screen back on without unplugging the TV, and webOS 4 rejects a call without it.
 */
private enum class ScreenMethod(
    /** How the registry records this method in [io.github.krank56.webmote.core.SavedTv.screenOffMethod]. */
    val key: String,
    val offUri: String,
    val offPayload: JsonObject,
    val onUri: String,
    val onPayload: JsonObject,
    val onErrorExpected: Boolean = false,
) {
    PanelController(
        key = "panelcontroller",
        offUri = "ssap://com.webos.service.panelcontroller/setScreenOnOff",
        offPayload = buildJsonObject { put("OnOff", false) },
        onUri = "ssap://com.webos.service.panelcontroller/setScreenOnOff",
        onPayload = buildJsonObject { put("OnOff", true) },
    ),

    /** webOS 5 and later (some 4.5 sets too). */
    TvPower(
        key = "tvpower",
        offUri = "ssap://com.webos.service.tvpower/power/turnOffScreen",
        offPayload = activeStandby(),
        onUri = "ssap://com.webos.service.tvpower/power/turnOnScreen",
        onPayload = activeStandby(),
    ),

    /** webOS 4.x. */
    WebOs4(
        key = "tv.power",
        offUri = "ssap://com.webos.service.tv.power/turnOffScreen",
        offPayload = activeStandby(),
        onUri = "ssap://com.webos.service.tv.power/turnOnScreen",
        onPayload = activeStandby(),
        onErrorExpected = true,
    ),
    ;

    companion object {
        fun withKey(key: String?): ScreenMethod? = entries.firstOrNull { it.key == key }
    }
}

private fun activeStandby(): JsonObject = buildJsonObject { put("standbyMode", "active") }
