package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.faketv.FakeTv
import io.github.krank56.webmote.core.faketv.TvError
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class PowerTest {
    @TempDir lateinit var dir: File
    private val h by lazy { Harness(dir) }

    @AfterEach fun tearDown() = h.close()

    @Test
    fun `pairing stores the TV's wired and Wi-Fi MACs, upper-case and colon-separated`() {
        h.tv.wiredMac = "60:75:6c:27:a9:02"
        h.tv.wifiMac = "08-27-A8-9C-52-C8"

        h.pair()

        h.eventually { h.registry[h.tv.uuid]?.wifiMac != null }
        val saved = assertNotNull(h.registry[h.tv.uuid])
        assertEquals("60:75:6C:27:A9:02", saved.wiredMac)
        assertEquals("08:27:A8:9C:52:C8", saved.wifiMac)
    }

    @Test
    fun `a TV without getinfo still connects, and a TV saved without MACs gets them on its next connection`() {
        h.tv.on(GET_INFO) { throw TvError("404 no such service or method") }
        h.pair()
        h.awaitRequests(GET_INFO)
        h.settle()
        assertEquals(ConnectionState.Connected, h.state.connection)
        assertNull(h.registry[h.tv.uuid]?.wiredMac)

        h.tv.on(GET_INFO) { FakeTv.ok { putJsonObject("wiredInfo") { put("macAddress", "a8:23:fe:00:00:01") } } }
        h.reopen()
        h.session.connect()
        h.awaitConnection(ConnectionState.Connected)

        h.eventually { h.registry[h.tv.uuid]?.wiredMac != null }
        assertEquals("A8:23:FE:00:00:01", h.registry[h.tv.uuid]?.wiredMac)
        assertNull(h.registry[h.tv.uuid]?.wifiMac)
    }

    @Test
    fun `getinfo isn't requested again once both MACs are stored`() {
        h.pair()
        h.eventually { h.registry[h.tv.uuid]?.wifiMac != null }

        h.reopen()
        h.session.connect()
        h.awaitConnection(ConnectionState.Connected)
        h.settle()

        assertEquals(1, h.tv.requests(GET_INFO).size)
    }

    @Test
    fun `power off sends turnOff and reports the TV off`() {
        h.pair()

        h.session.powerOff()

        h.awaitRequests(TURN_OFF)
        h.awaitConnection(ConnectionState.Off)
        h.eventually { !h.tv.isOn }
        h.settle()
        assertEquals(ConnectionState.Off, h.state.connection)
    }

    @Test
    fun `power off closes the connection and doesn't reconnect, even while the TV is still shutting down`() {
        h.tv.on(TURN_OFF) { FakeTv.ok() }
        h.pair()

        h.session.powerOff()
        h.awaitConnection(ConnectionState.Off)
        h.settle(300)
        h.advance(5.seconds)
        h.settle()

        assertEquals(ConnectionState.Off, h.state.connection)
        assertEquals(1, h.tv.requests.count { it.type == "register" })
        assertEquals(1, h.tv.requests(TURN_OFF).size)
    }

    private companion object {
        const val GET_INFO = "ssap://com.webos.service.connectionmanager/getinfo"
        const val TURN_OFF = "ssap://system/turnOff"
    }
}
