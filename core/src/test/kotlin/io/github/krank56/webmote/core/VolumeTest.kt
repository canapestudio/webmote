package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.faketv.FakeTv
import io.github.krank56.webmote.core.faketv.FakeTv.Companion.ok
import io.github.krank56.webmote.core.faketv.TvError
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.math.ceil
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class VolumeTest {
    @TempDir lateinit var dir: File
    private val h by lazy { Harness(dir) }

    @AfterEach fun tearDown() = h.close()

    @Test
    fun `the session subscribes to the TV's volume and reports its level and mute state`() {
        h.tv.volume = 17
        h.tv.muted = true
        h.pair()

        h.eventually { h.state.volume == Volume(level = 17, muted = true) }
        assertEquals("subscribe", h.awaitRequests(FakeTv.GET_VOLUME).single().type)
        assertEquals(Capability.Available, h.state.capabilities.volumeLevel)
        assertEquals(Capability.Available, h.registry[h.tv.uuid]!!.capabilities.volumeLevel)
    }

    @Test
    fun `the level follows changes made with the physical remote`() {
        h.pair()
        h.eventually { h.state.volume.level == 12 }

        h.tv.changeVolumeFromRemote(30)
        h.eventually { h.state.volume.level == 30 }

        h.tv.changeVolumeFromRemote(30, muted = true)
        h.eventually { h.state.volume == Volume(level = 30, muted = true) }
    }

    @Test
    fun `a TV that reports no level has no volume level capability, and remembers it`() {
        h.tv.volume = null
        h.pair()

        h.eventually { h.state.capabilities.volumeLevel == Capability.Unavailable }
        assertNull(h.state.volume.level)
        assertEquals(Capability.Unavailable, h.registry[h.tv.uuid]!!.capabilities.volumeLevel)
    }

    @Test
    fun `volume buttons and mute keep working without a reported level`() {
        h.tv.volume = null
        h.pair()
        h.eventually { h.state.capabilities.volumeLevel == Capability.Unavailable }

        h.session.volumeUp()
        h.session.volumeDown()
        h.session.setMute(true)

        h.awaitRequests("ssap://audio/volumeUp")
        h.awaitRequests("ssap://audio/volumeDown")
        h.eventually { h.state.volume.muted }
        assertNull(h.state.volume.level)
    }

    @Test
    fun `the level capability comes back when the TV reports a level again`() {
        h.tv.volume = null
        h.pair()
        h.eventually { h.state.capabilities.volumeLevel == Capability.Unavailable }

        h.tv.changeVolumeFromRemote(20)

        h.eventually { h.state.volume.level == 20 }
        assertEquals(Capability.Available, h.state.capabilities.volumeLevel)
        assertEquals(Capability.Available, h.registry[h.tv.uuid]!!.capabilities.volumeLevel)
    }

    @Test
    fun `a volume status without a level counts as no level`() {
        h.tv.on(FakeTv.GET_VOLUME) { ok { putJsonObject("volumeStatus") { put("muteStatus", true) } } }
        h.pair()

        h.eventually { h.state.capabilities.volumeLevel == Capability.Unavailable }
        assertEquals(Volume(level = null, muted = true), h.state.volume)
    }

    @Test
    fun `older firmware's flat volume payload is understood`() {
        h.tv.on(FakeTv.GET_VOLUME) { ok { put("scenario", "mastervolume_tv_speaker"); put("volume", 98); put("mute", true) } }
        h.pair()

        h.eventually { h.state.volume == Volume(level = 98, muted = true) }
        assertEquals(Capability.Available, h.state.capabilities.volumeLevel)
    }

    @Test
    fun `older firmware's muted key is understood`() {
        h.tv.on(FakeTv.GET_VOLUME) { ok { put("volume", 5); put("muted", true) } }
        h.pair()

        h.eventually { h.state.volume == Volume(level = 5, muted = true) }
    }

    @Test
    fun `mute sends a boolean and the state follows the TV`() {
        h.pair()
        h.eventually { h.state.volume.level != null }

        h.session.setMute(true)
        h.eventually { h.state.volume.muted }
        h.session.setMute(false)
        h.eventually { !h.state.volume.muted }

        val sent = h.awaitRequests(SET_MUTE, 2).map { it["mute"] as JsonPrimitive }
        assertEquals(listOf(true, false), sent.map { it.boolean })
        assertTrue(sent.none { it.isString })
    }

    @Test
    fun `setting the volume sends the level, clamped to 0-100`() {
        h.pair()

        h.session.setVolume(40)
        h.session.setVolume(150)
        h.session.setVolume(-5)

        assertEquals(listOf(40, 100, 0), h.awaitRequests(SET_VOLUME, 3).map { it.int("volume") })
        h.eventually { h.state.volume.level == 0 }
    }

    @Test
    fun `dragging the slider sends at most one level per interval, then the release value`() {
        h.pair()
        val interval = h.tv.sessionConfig().throttleInterval
        val step = 10.milliseconds
        val levels = (21..40).toList()

        levels.forEach { level ->
            h.session.dragVolume(level)
            h.advance(step)
        }
        h.session.setVolume(41)

        val dragged = step * levels.size
        val maxRequests = ceil(dragged / interval).toInt() + 1
        h.awaitRequests(SET_VOLUME)
        h.settle()
        val sent = h.tv.requests(SET_VOLUME).map { it.int("volume") }
        assertTrue(sent.size <= maxRequests, "expected at most $maxRequests setVolume requests, got $sent")
        assertEquals(21, sent.first())
        assertEquals(41, sent.last())
        assertTrue(sent.dropLast(1).all { it in levels }, "sent $sent")
        assertEquals(sent, sent.sortedBy { it }, "levels went backwards: $sent")
        h.eventually { h.state.volume.level == 41 }

        h.advance(interval * 4)
        h.settle()
        assertEquals(sent, h.tv.requests(SET_VOLUME).map { it.int("volume") })
    }

    @Test
    fun `the latest dragged value is sent once the interval has passed`() {
        h.pair()
        val interval = h.tv.sessionConfig().throttleInterval

        h.session.dragVolume(10)
        h.session.dragVolume(11)
        h.session.dragVolume(12)
        h.awaitRequests(SET_VOLUME)
        h.settle()
        assertEquals(listOf(10), h.tv.requests(SET_VOLUME).map { it.int("volume") })

        h.advance(interval)

        assertEquals(listOf(10, 12), h.awaitRequests(SET_VOLUME, 2).map { it.int("volume") })
    }

    @Test
    fun `setting the level it was last set to is still sent after the volume changed elsewhere`() {
        h.pair()
        h.session.setVolume(30)
        h.eventually { h.state.volume.level == 30 }

        h.tv.changeVolumeFromRemote(20)
        h.eventually { h.state.volume.level == 20 }
        h.session.setVolume(30)

        h.eventually { h.state.volume.level == 30 }
        assertEquals(listOf(30, 30), h.awaitRequests(SET_VOLUME, 2).map { it.int("volume") })
    }

    @Test
    fun `when getVolume can't be subscribed to, the session follows getStatus instead`() {
        h.tv.on(FakeTv.GET_VOLUME) { throw TvError("404 no such service or method") }
        h.pair()

        h.eventually { h.state.volume.level == 12 }
        assertEquals("subscribe", h.awaitRequests(GET_STATUS).single().type)

        h.tv.push(GET_STATUS, ok { put("volume", 25); put("mute", true) })
        h.eventually { h.state.volume == Volume(level = 25, muted = true) }
    }

    @Test
    fun `when neither volume subscription works, no level is reported but the buttons still work`() {
        h.tv.on(FakeTv.GET_VOLUME) { throw TvError("401 insufficient permissions") }
        h.tv.on(GET_STATUS) { throw TvError("401 insufficient permissions") }
        h.pair()

        h.eventually { h.state.capabilities.volumeLevel == Capability.Unavailable }
        h.session.volumeUp()
        h.awaitRequests("ssap://audio/volumeUp")
        assertEquals(ConnectionState.Connected, h.state.connection)
    }

    private companion object {
        const val GET_STATUS = "ssap://audio/getStatus"
        const val SET_VOLUME = "ssap://audio/setVolume"
        const val SET_MUTE = "ssap://audio/setMute"
    }
}
