package io.github.krank56.webmote.core.internal

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The pairing manifest. It's **unsigned**: no `signatures` or `signed` block, with every permission
 * listed in the outer `permissions` array. webOS 26 (firmware 43.x) rejects the old signed
 * `com.lge.test` manifest with `403 … blacklisted certificate`.
 *
 * LG changes pairing rules between firmware generations; this file is the one place to update.
 */
internal object Manifest {

    /**
     * Ported from aiowebostv's unsigned webOS 26 manifest (`aiowebostv/handshake.py`, v0.9.2 and
     * later; Apache License 2.0, Home Assistant Team; see NOTICE). It's a
     * superset of LGTV Companion 5.7.0's. Includes CONTROL_MOUSE_AND_KEYBOARD for the pointer
     * socket, and WRITE_SETTINGS and WRITE_NOTIFICATION_ALERT for the picture alert workaround.
     */
    val permissions: List<String> = listOf(
        "APP_TO_APP",
        "CLOSE",
        "CONTROL_AUDIO",
        "CONTROL_DISPLAY",
        "CONTROL_INPUT_JOYSTICK",
        "CONTROL_INPUT_MEDIA_PLAYBACK",
        "CONTROL_INPUT_MEDIA_RECORDING",
        "CONTROL_INPUT_TEXT",
        "CONTROL_INPUT_TV",
        "CONTROL_MOUSE_AND_KEYBOARD",
        "CONTROL_POWER",
        "CONTROL_TV_SCREEN",
        "LAUNCH",
        "LAUNCH_WEBAPP",
        "READ_APP_STATUS",
        "READ_COUNTRY_INFO",
        "READ_CURRENT_CHANNEL",
        "READ_INPUT_DEVICE_LIST",
        "READ_INSTALLED_APPS",
        "READ_LGE_SDX",
        "READ_LGE_TV_INPUT_EVENTS",
        "READ_NETWORK_STATE",
        "READ_NOTIFICATIONS",
        "READ_POWER_STATE",
        "READ_RUNNING_APPS",
        "READ_SETTINGS",
        "READ_TV_CHANNEL_LIST",
        "READ_TV_CURRENT_TIME",
        "READ_UPDATE_INFO",
        "SEARCH",
        "TEST_OPEN",
        "TEST_PROTECTED",
        "TEST_SECURE",
        "UPDATE_FROM_REMOTE_APP",
        "WRITE_NOTIFICATION_ALERT",
        "WRITE_NOTIFICATION_TOAST",
        "WRITE_SETTINGS",
    )

    /** Firmware 33.20 and later doesn't answer a hello without a `payload` key, even an empty one. */
    fun helloPayload(): JsonObject = JsonObject(emptyMap())

    fun registerPayload(clientKey: String?, pairingType: String): JsonObject = buildJsonObject {
        put("forcePairing", false)
        put("pairingType", pairingType)
        if (clientKey != null) put("client-key", clientKey)
        putJsonObject("manifest") {
            put("manifestVersion", 1)
            put("appVersion", "1.1")
            putJsonArray("permissions") { permissions.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
        }
    }
}
