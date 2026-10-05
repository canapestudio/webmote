package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.faketv.FakeTv
import io.github.krank56.webmote.core.faketv.PinReply
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PinPairingTest {
    @TempDir lateinit var dir: File
    private val h by lazy { Harness(dir) }

    @AfterEach fun tearDown() = h.close()

    /** Starts pairing with a TV that only offers PIN pairing, up to the point where it shows its PIN. */
    private fun startPinPairing() {
        h.tv.pairingTypes = listOf("PIN")
        h.tv.powerOn()
        h.session.connect(h.tv.host)
        h.awaitConnection(ConnectionState.AwaitingPin)
    }

    @Test
    fun `a TV that only offers PIN pairing is registered for PIN and shows its PIN`() {
        startPinPairing()

        assertEquals("PIN", h.tv.requests.single { it.type == "register" }.string("pairingType"))
    }

    @ParameterizedTest
    @EnumSource(PinReply::class)
    fun `submitting the PIN the TV shows pairs it and stores its key as prompt pairing does`(reply: PinReply) {
        h.tv.pinReply = reply
        startPinPairing()

        h.session.submitPin("12345678")
        h.awaitConnection(ConnectionState.Connected)

        assertEquals("12345678", h.tv.requests(FakeTv.SET_PIN).single().string("pin"))
        val saved = assertNotNull(h.registry[h.tv.uuid])
        assertEquals(h.tv.issuedKeys.single(), saved.clientKey)
        assertNotNull(saved.certificatePin)
        assertEquals(h.tv.uuid, h.registry.activeTvId.value)
    }

    @Test
    fun `a wrong PIN is reported as declined and nothing is saved`() {
        startPinPairing()

        h.session.submitPin("00000000")
        h.awaitConnection(ConnectionState.PairingDeclined)

        assertTrue(h.registry.tvs.value.isEmpty())
        assertTrue(h.tv.issuedKeys.isEmpty())
    }

    @Test
    fun `a PIN-paired TV reconnects with its key without asking for a PIN again`() {
        startPinPairing()
        h.session.submitPin(h.tv.pin)
        h.awaitConnection(ConnectionState.Connected)

        h.reopen()
        h.session.connect()
        h.awaitConnection(ConnectionState.Connected)

        assertEquals(h.tv.issuedKeys.single(), h.tv.requests.last { it.type == "register" }.string("client-key"))
        assertEquals(1, h.tv.requests(FakeTv.SET_PIN).size)
    }

    @Test
    fun `a PIN submitted while the TV isn't waiting for one isn't sent`() {
        h.pair()

        h.session.submitPin("12345678")
        h.session.volumeUp()
        h.awaitRequests("ssap://audio/volumeUp")

        assertTrue(h.tv.requests(FakeTv.SET_PIN).isEmpty())
    }
}
