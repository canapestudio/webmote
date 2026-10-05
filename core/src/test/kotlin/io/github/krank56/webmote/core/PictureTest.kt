package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.faketv.FakeTv
import io.github.krank56.webmote.core.faketv.TvError
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PictureTest {
    @TempDir lateinit var dir: File
    private val h by lazy { Harness(dir) }

    @AfterEach fun tearDown() = h.close()

    @Test
    fun `on connect the session subscribes to the picture settings and reports the current values`() {
        h.pair()

        val subscription = h.awaitRequests(FakeTv.GET_SETTINGS).single()
        assertEquals("subscribe", subscription.type)
        assertEquals("picture", subscription.string("category"))
        assertEquals(
            listOf("backlight", "brightness", "contrast", "color", "energySaving"),
            subscription["keys"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        h.eventually { h.state.picture == INITIAL }
    }

    @Test
    fun `a change made on the TV updates only the values it reports`() {
        h.pair()
        h.eventually { h.state.picture == INITIAL }

        changeOnTv("backlight" to "30")

        h.eventually { h.state.picture == INITIAL.copy(backlight = 30) }
        changeOnTv("energySaving" to "max")
        h.eventually { h.state.picture == INITIAL.copy(backlight = 30, energySaving = EnergySaving.Max) }
    }

    @Test
    fun `values the TV reports as numbers are read, and confirm writes, like strings`() {
        h.tv.reportsPictureAsInts = true
        h.pair()

        h.eventually { h.state.picture == INITIAL }
        changeOnTv("contrast" to "26")
        h.eventually { h.state.picture == INITIAL.copy(contrast = 26) }

        h.session.setPicture(PictureSetting.Color, 60)
        assertEquals(alertPayload("color", "60"), h.awaitRequests(FakeTv.CREATE_ALERT).single().payload)
        h.eventually { h.state.picture == INITIAL.copy(contrast = 26, color = 60) }
        h.eventually { h.state.capabilities.pictureWrites == Capability.Available }
    }

    @Test
    fun `a write goes through a hidden alert, and once the TV applies it the capability is available`() {
        h.pair()
        h.eventually { h.state.picture == INITIAL }

        h.session.setPicture(PictureSetting.Backlight, 40)

        val create = h.awaitRequests(FakeTv.CREATE_ALERT).single()
        assertEquals(alertPayload("backlight", "40"), create.payload)
        val close = h.awaitRequests(FakeTv.CLOSE_ALERT).single()
        assertEquals(setOf("alertId"), close.payload.keys)
        assertTrue(h.tv.requests.indexOf(create) < h.tv.requests.indexOf(close))
        assertTrue(h.tv.requests(SET_SETTINGS_SSAP).isEmpty())
        h.eventually { h.state.picture == INITIAL.copy(backlight = 40) }
        h.eventually { h.state.capabilities.pictureWrites == Capability.Available }
        val learned = h.registry[h.tv.uuid]!!.capabilities
        assertEquals(Capability.Available, learned.pictureWrites)
        assertEquals(h.tv.osVersion, learned.pictureWritesLearnedOn)
    }

    @Test
    fun `the alert is closed with the id the TV returned, unchanged`() {
        val alertId = "com.webos.service.apiadapter.pub-1786895965205"
        h.tv.on(FakeTv.CREATE_ALERT) { FakeTv.ok { put("alertId", alertId) } }
        h.tv.on(FakeTv.CLOSE_ALERT) { FakeTv.ok() }
        h.pair()
        h.eventually { h.state.picture == INITIAL }

        h.session.setPicture(PictureSetting.Contrast, 70)

        val close = h.awaitRequests(FakeTv.CLOSE_ALERT).single()
        assertEquals(buildJsonObject { put("alertId", alertId) }, close.payload)
    }

    @Test
    fun `energy saving is written once, with the TV's name for the mode`() {
        h.pair()
        h.eventually { h.state.picture == INITIAL }

        h.session.setEnergySaving(EnergySaving.Med)

        assertEquals(alertPayload("energySaving", "med"), h.awaitRequests(FakeTv.CREATE_ALERT).single().payload)
        h.eventually { h.state.picture == INITIAL.copy(energySaving = EnergySaving.Med) }
        h.eventually { h.state.capabilities.pictureWrites == Capability.Available }
        h.advance(1000.milliseconds)
        h.settle()
        assertEquals(1, h.tv.requests(FakeTv.CREATE_ALERT).size)
    }

    @Test
    fun `writing a value the TV already has sends nothing and judges nothing`() {
        h.tv.appliesPictureWrites = false
        h.pair()
        h.eventually { h.state.picture == INITIAL }

        h.session.setPicture(PictureSetting.Backlight, 80)
        h.session.setEnergySaving(EnergySaving.Off)
        h.settle()
        h.advance(3.seconds)

        assertTrue(h.tv.requests(FakeTv.CREATE_ALERT).isEmpty())
        assertEquals(Capability.Unknown, h.state.capabilities.pictureWrites)
    }

    @Test
    fun `going back to the TV's value is still sent while an earlier write is unconfirmed`() {
        h.tv.appliesPictureWrites = false
        h.pair()
        h.eventually { h.state.picture == INITIAL }

        h.session.setPicture(PictureSetting.Backlight, 70)
        h.awaitRequests(FakeTv.CLOSE_ALERT, 1)
        h.session.setPicture(PictureSetting.Backlight, 80)
        h.awaitRequests(FakeTv.CLOSE_ALERT, 2)

        assertEquals(listOf("backlight" to "70", "backlight" to "80"), written())
    }

    @Test
    fun `values written while an alert is unanswered don't pile up, only the newest is sent next`() {
        h.pair()
        h.eventually { h.state.picture == INITIAL }

        h.session.setPicture(PictureSetting.Backlight, 70)
        h.session.setPicture(PictureSetting.Backlight, 65)
        h.session.setPicture(PictureSetting.Backlight, 60)
        h.session.setPicture(PictureSetting.Backlight, 55)
        h.awaitRequests(FakeTv.CLOSE_ALERT, 2)
        h.settle()

        assertEquals(listOf("backlight" to "70", "backlight" to "55"), written())
        h.eventually { h.state.picture == INITIAL.copy(backlight = 55) }
    }

    @Test
    fun `a drag sends its first value at once, then the latest one per throttle interval, then the value on release`() {
        h.pair()
        h.eventually { h.state.picture == INITIAL }

        h.session.dragPicture(PictureSetting.Backlight, 70)
        h.session.dragPicture(PictureSetting.Backlight, 65)
        h.session.dragPicture(PictureSetting.Backlight, 60)
        h.awaitRequests(FakeTv.CLOSE_ALERT, 1)
        h.settle()
        assertEquals(listOf("backlight" to "70"), written())

        h.advance(100.milliseconds)
        h.settle()
        assertEquals(listOf("backlight" to "70"), written())
        h.advance(50.milliseconds)
        h.awaitRequests(FakeTv.CREATE_ALERT, 2)
        assertEquals(listOf("backlight" to "70", "backlight" to "60"), written())

        h.session.dragPicture(PictureSetting.Backlight, 55)
        h.session.setPicture(PictureSetting.Backlight, 50)
        h.awaitRequests(FakeTv.CREATE_ALERT, 3)
        h.advance(200.milliseconds)
        h.settle()
        assertEquals(listOf("backlight" to "70", "backlight" to "60", "backlight" to "50"), written())
        h.eventually { h.state.picture == INITIAL.copy(backlight = 50) }
    }

    @Test
    fun `setting a value again after the TV moved away from it is sent`() {
        h.pair()
        h.eventually { h.state.picture == INITIAL }
        h.session.setPicture(PictureSetting.Backlight, 30)
        h.eventually { h.state.picture.backlight == 30 }
        h.advance(1.seconds)

        changeOnTv("backlight" to "20")
        h.eventually { h.state.picture.backlight == 20 }
        h.session.setPicture(PictureSetting.Backlight, 30)

        h.awaitRequests(FakeTv.CREATE_ALERT, 2)
        assertEquals(listOf("backlight" to "30", "backlight" to "30"), written())
    }

    @Test
    fun `each setting is throttled on its own`() {
        h.pair()
        h.eventually { h.state.picture == INITIAL }

        h.session.dragPicture(PictureSetting.Backlight, 70)
        h.session.dragPicture(PictureSetting.Contrast, 20)
        h.awaitRequests(FakeTv.CREATE_ALERT, 2)

        assertEquals(setOf("backlight" to "70", "contrast" to "20"), written().toSet())
    }

    @Test
    fun `a write the TV ignores disables picture writes for that TV after the verification window`() {
        h.tv.appliesPictureWrites = false
        h.pair()
        h.eventually { h.state.picture == INITIAL }

        h.session.setPicture(PictureSetting.Backlight, 40)
        h.awaitRequests(FakeTv.CLOSE_ALERT)
        h.settle()
        h.advance(1900.milliseconds)
        assertEquals(Capability.Unknown, h.state.capabilities.pictureWrites)
        h.advance(200.milliseconds)

        assertEquals(Capability.Unavailable, h.state.capabilities.pictureWrites)
        assertEquals(INITIAL, h.state.picture)
        val learned = h.registry[h.tv.uuid]!!.capabilities
        assertEquals(Capability.Unavailable, learned.pictureWrites)
        assertEquals(h.tv.osVersion, learned.pictureWritesLearnedOn)
    }

    @Test
    fun `each write restarts the verification window, so only the latest value is judged`() {
        h.tv.appliesPictureWrites = false
        h.pair()
        h.eventually { h.state.picture == INITIAL }

        h.session.setPicture(PictureSetting.Backlight, 70)
        h.awaitRequests(FakeTv.CLOSE_ALERT, 1)
        h.advance(1500.milliseconds)
        h.session.setPicture(PictureSetting.Backlight, 60)
        h.awaitRequests(FakeTv.CLOSE_ALERT, 2)
        h.advance(1000.milliseconds)
        assertEquals(Capability.Unknown, h.state.capabilities.pictureWrites)

        h.advance(1100.milliseconds)
        assertEquals(Capability.Unavailable, h.state.capabilities.pictureWrites)
    }

    @Test
    fun `an alert the TV refuses counts as an ignored write`() {
        h.tv.on(FakeTv.CREATE_ALERT) { throw TvError("401 insufficient permissions") }
        h.pair()
        h.eventually { h.state.picture == INITIAL }

        h.session.setPicture(PictureSetting.Brightness, 40)
        h.awaitRequests(FakeTv.CREATE_ALERT)
        h.settle()
        h.advance(2100.milliseconds)

        assertTrue(h.tv.requests(FakeTv.CLOSE_ALERT).isEmpty())
        assertEquals(Capability.Unavailable, h.state.capabilities.pictureWrites)
    }

    @Test
    fun `while picture writes are unavailable nothing is sent`() {
        h.tv.appliesPictureWrites = false
        h.pair()
        h.eventually { h.state.picture == INITIAL }
        h.session.setPicture(PictureSetting.Backlight, 40)
        h.awaitRequests(FakeTv.CLOSE_ALERT)
        h.advance(2100.milliseconds)
        assertEquals(Capability.Unavailable, h.state.capabilities.pictureWrites)

        h.session.setPicture(PictureSetting.Backlight, 45)
        h.session.dragPicture(PictureSetting.Contrast, 10)
        h.session.setEnergySaving(EnergySaving.Max)
        h.settle()

        assertEquals(1, h.tv.requests(FakeTv.CREATE_ALERT).size)
    }

    @Test
    fun `after a webOS update the session tries picture writes again`() {
        disablePictureWrites()
        h.tv.powerOff()
        h.tv.osVersion = "11.3.0"
        h.tv.appliesPictureWrites = true
        h.tv.powerOn()

        h.reopen()
        h.session.connect()
        h.awaitConnection(ConnectionState.Connected)

        h.eventually { h.state.capabilities.pictureWrites == Capability.Unknown }
        assertEquals(Capability.Unknown, h.registry[h.tv.uuid]!!.capabilities.pictureWrites)
        h.eventually { h.state.picture == INITIAL }
        h.session.setPicture(PictureSetting.Backlight, 30)
        assertEquals(alertPayload("backlight", "30"), h.awaitRequests(FakeTv.CREATE_ALERT, 2)[1].payload)
        h.eventually { h.state.capabilities.pictureWrites == Capability.Available }
        val learned = h.registry[h.tv.uuid]!!.capabilities
        assertEquals(Capability.Available to "11.3.0", learned.pictureWrites to learned.pictureWritesLearnedOn)
    }

    @Test
    fun `on the same webOS version picture writes stay disabled across reconnections`() {
        disablePictureWrites()
        h.tv.appliesPictureWrites = true

        h.reopen()
        h.session.connect()
        h.awaitConnection(ConnectionState.Connected)
        h.eventually { h.state.picture == INITIAL }

        assertEquals(Capability.Unavailable, h.state.capabilities.pictureWrites)
        h.session.setPicture(PictureSetting.Backlight, 30)
        h.settle()
        assertEquals(1, h.tv.requests(FakeTv.CREATE_ALERT).size)
    }

    /** The settings each createAlert asked the TV to write, in order. */
    private fun written(): List<Pair<String, String>> = h.tv.requests(FakeTv.CREATE_ALERT).flatMap { alert ->
        alert["onclose"]!!.jsonObject["params"]!!.jsonObject["settings"]!!.jsonObject.map { (key, value) -> key to value.jsonPrimitive.content }
    }

    /** Pairs with a TV that ignores picture writes, and lets the session find that out. */
    private fun disablePictureWrites() {
        h.tv.appliesPictureWrites = false
        h.pair()
        h.eventually { h.state.picture == INITIAL }
        h.session.setPicture(PictureSetting.Backlight, 40)
        h.awaitRequests(FakeTv.CLOSE_ALERT)
        h.advance(2100.milliseconds)
        assertEquals(Capability.Unavailable, h.registry[h.tv.uuid]!!.capabilities.pictureWrites)
    }

    /** Changes picture values as the TV's own menu would, notifying subscribers. */
    private fun changeOnTv(vararg values: Pair<String, String>) {
        h.tv.picture.putAll(values)
        h.tv.push(
            FakeTv.GET_SETTINGS,
            FakeTv.ok {
                put("category", "picture")
                putJsonObject("settings") {
                    values.forEach { (key, value) -> if (h.tv.reportsPictureAsInts) put(key, value.toInt()) else put(key, value) }
                }
            },
        )
    }

    private companion object {
        val INITIAL = PictureValues(backlight = 80, brightness = 50, contrast = 85, color = 50, energySaving = EnergySaving.Off)

        /** The direct SSAP write, which answers 401 with an unsigned manifest; it must never be used. */
        const val SET_SETTINGS_SSAP = "ssap://settings/setSystemSettings"

        /** The createAlert payload of the alert workaround (bscpylgtv's `luna_request`) for one setting. */
        fun alertPayload(key: String, value: String): JsonObject {
            val params = buildJsonObject {
                put("category", "picture")
                putJsonObject("settings") { put(key, value) }
            }
            return buildJsonObject {
                put("message", " ")
                putJsonArray("buttons") {
                    addJsonObject {
                        put("label", "")
                        put("onClick", FakeTv.LUNA_SET_SETTINGS)
                        put("params", params)
                    }
                }
                putJsonObject("onclose") {
                    put("uri", FakeTv.LUNA_SET_SETTINGS)
                    put("params", params)
                }
                putJsonObject("onfail") {
                    put("uri", FakeTv.LUNA_SET_SETTINGS)
                    put("params", params)
                }
            }
        }
    }
}
