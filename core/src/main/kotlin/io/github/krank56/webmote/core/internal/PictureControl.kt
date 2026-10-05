package io.github.krank56.webmote.core.internal

import io.github.krank56.webmote.core.Capability
import io.github.krank56.webmote.core.EnergySaving
import io.github.krank56.webmote.core.PictureSetting
import io.github.krank56.webmote.core.PictureValues
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException

/** Picture settings: the subscription, writes through the alert workaround, and their verification. */
internal class PictureControl(private val core: SessionCore) : Feature {
    /** The picture state of the current link; each new link gets its own. */
    private var current: PictureLink? = null

    override fun onConnected(link: Link) {
        retryAfterUpdate(link)
        val picture = PictureLink(link).also { current = it }
        link.scope.launch { picture.follow() }
    }

    /** A slider value while dragging; throttled per setting. */
    fun drag(setting: PictureSetting, value: Int) {
        live()?.throttles?.getValue(setting)?.drag(value)
    }

    /** The slider's value on release: sent now. */
    fun set(setting: PictureSetting, value: Int) {
        live()?.throttles?.getValue(setting)?.release(value)
    }

    fun setEnergySaving(mode: EnergySaving) {
        live()?.write(ENERGY_SAVING, mode.key)
    }

    private fun live(): PictureLink? = current?.takeIf { it.link === core.link }

    /** Writes found not to work on another webOS version may work on this one: forget that they didn't. */
    private fun retryAfterUpdate(link: Link) {
        val learned = core.registry[link.tvId]?.capabilities ?: return
        if (learned.pictureWrites != Capability.Unavailable || learned.pictureWritesLearnedOn == link.webOsVersion) return
        core.registry.update(link.tvId) { tv ->
            tv.copy(capabilities = tv.capabilities.copy(pictureWrites = Capability.Unknown, pictureWritesLearnedOn = null))
        }
        core.update { it.copy(capabilities = it.capabilities.copy(pictureWrites = Capability.Unknown)) }
    }

    /** Picture settings on one link: the subscription, the slider throttles, and the writes being judged. */
    private inner class PictureLink(val link: Link) {
        /**
         * Numbers are written as strings ("40"). That's how the TV reports them, and the only
         * captured write on webOS 26 (LGTV Companion, research §6.1) sends strings. Ints are reported
         * to work too, but without a capture on current firmware.
         */
        val throttles: Map<PictureSetting, Throttle<Int>> = PictureSetting.entries.associateWith { setting ->
            Throttle(link.scope, core.config.throttleInterval) { value -> write(setting.key, value.toString()) }
        }

        /** Writes not yet seen in the TV's settings, by key: only the latest write to each key is judged. */
        private val checks = mutableMapOf<String, Check>()

        /** Keys with an alert sent and not yet answered. */
        private val sending = mutableSetOf<String>()

        /** The newest value for each key that waits for its unanswered alert. */
        private val waiting = mutableMapOf<String, String>()

        /** Follows the TV's picture settings until the link closes, and checks writes against them. */
        suspend fun follow() {
            try {
                link.socket.subscribe(GET_SETTINGS, QUERY).collect { payload ->
                    val settings = payload.obj("settings") ?: return@collect
                    core.update { it.copy(picture = it.picture.merge(settings)) }
                    verify(settings)
                }
            } catch (e: SsapException) {
                // The TV won't report its picture settings; the values stay unknown.
            } catch (e: IOException) {
                // The link is closing.
            }
        }

        /**
         * Writes one setting. While an earlier write to the same key is unanswered, [value] waits and
         * replaces any older value still waiting, so writes never pile up behind a slow TV.
         */
        fun write(key: String, value: String) {
            val state = core.state.value
            if (state.capabilities.pictureWrites == Capability.Unavailable) return
            if (key in sending) {
                waiting[key] = value
                return
            }
            // The TV already has it, and no earlier write that could still land would change it.
            if (key !in checks && state.picture.wire(key) == value) return
            expect(key, value)
            sending += key
            link.scope.launch {
                try {
                    sendAlert(key, value)
                } catch (e: SsapException) {
                    // The TV refused the alert; verification will notice.
                } catch (e: IOException) {
                    // The link is closing.
                } catch (e: TimeoutCancellationException) {
                    // The TV didn't answer; verification will notice.
                } finally {
                    sending -= key
                    waiting.remove(key)?.let { write(key, it) }
                }
            }
        }

        /**
         * The alert workaround, as bscpylgtv's `luna_request`: the TV runs the luna call when the
         * alert closes, so the alert is closed straight away and never really shows. The direct
         * `settings/setSystemSettings` answers 401 to an unsigned manifest, so it's never used.
         *
         * One setting per call: the TV can drop part of a combined write (a picture mode change
         * reloads that mode's backlight).
         */
        private suspend fun sendAlert(key: String, value: String) {
            val params = buildJsonObject {
                put("category", "picture")
                putJsonObject("settings") { put(key, value) }
            }
            val alert = buildJsonObject {
                put("message", " ")
                putJsonArray("buttons") {
                    addJsonObject {
                        put("label", "")
                        put("onClick", LUNA_SET_SETTINGS)
                        put("params", params)
                    }
                }
                putJsonObject("onclose") {
                    put("uri", LUNA_SET_SETTINGS)
                    put("params", params)
                }
                putJsonObject("onfail") {
                    put("uri", LUNA_SET_SETTINGS)
                    put("params", params)
                }
            }
            val alertId = link.socket.request(CREATE_ALERT, alert)["alertId"] ?: throw SsapException("$CREATE_ALERT: no alertId")
            link.socket.request(CLOSE_ALERT, buildJsonObject { put("alertId", alertId) })
        }

        /** Starts judging a write of [value] to [key], from now; it replaces the check of an earlier write. */
        private fun expect(key: String, value: String) {
            checks.remove(key)?.deadline?.cancel()
            val deadline = link.scope.launch {
                delay(core.config.pictureVerifyTimeout)
                checks.remove(key)
                learn(Capability.Unavailable)
            }
            checks[key] = Check(value, deadline)
        }

        private fun verify(settings: JsonObject) {
            val confirmed = checks.filter { (key, check) -> settings.value(key) == check.value }.keys
            if (confirmed.isEmpty()) return
            confirmed.forEach { checks.remove(it)?.deadline?.cancel() }
            learn(Capability.Available)
        }

        /** Records what writes were found to do, in the state and for this TV in the registry. */
        private fun learn(capability: Capability) {
            if (core.state.value.capabilities.pictureWrites == capability) return
            core.update { it.copy(capabilities = it.capabilities.copy(pictureWrites = capability)) }
            core.registry.update(link.tvId) { tv ->
                tv.copy(capabilities = tv.capabilities.copy(pictureWrites = capability, pictureWritesLearnedOn = link.webOsVersion))
            }
        }
    }

    private class Check(val value: String, val deadline: Job)

    private companion object {
        const val GET_SETTINGS = "ssap://settings/getSystemSettings"
        const val CREATE_ALERT = "ssap://system.notifications/createAlert"
        const val CLOSE_ALERT = "ssap://system.notifications/closeAlert"
        const val LUNA_SET_SETTINGS = "luna://com.webos.settingsservice/setSystemSettings"
        const val ENERGY_SAVING = "energySaving"

        val QUERY = buildJsonObject {
            put("category", "picture")
            putJsonArray("keys") {
                PictureSetting.entries.forEach { add(it.key) }
                add(ENERGY_SAVING)
            }
        }

        /** The value of [key] as it's written to the TV, or null if it isn't known. */
        fun PictureValues.wire(key: String): String? = when (key) {
            ENERGY_SAVING -> energySaving?.key
            else -> PictureSetting.entries.firstOrNull { it.key == key }?.let { this[it] }?.toString()
        }

        /** Applies an update, which may carry only the keys that changed. Numbers come as strings or ints. */
        fun PictureValues.merge(settings: JsonObject): PictureValues {
            fun number(setting: PictureSetting, current: Int?): Int? =
                if (setting.key in settings) settings.value(setting.key)?.toIntOrNull() else current
            return PictureValues(
                backlight = number(PictureSetting.Backlight, backlight),
                brightness = number(PictureSetting.Brightness, brightness),
                contrast = number(PictureSetting.Contrast, contrast),
                color = number(PictureSetting.Color, color),
                energySaving = if (ENERGY_SAVING in settings) EnergySaving.entries.firstOrNull { it.key == settings.value(ENERGY_SAVING) } else energySaving,
            )
        }

        /** A reported value in its string form, whether the TV sent it as a string or a number. */
        fun JsonObject.value(key: String): String? = string(key)?.trim()
    }
}
