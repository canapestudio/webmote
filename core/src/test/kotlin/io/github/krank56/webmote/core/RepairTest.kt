package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.faketv.FakeTv
import io.github.krank56.webmote.core.faketv.KeyPolicy
import io.github.krank56.webmote.core.faketv.PromptAnswer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepairTest {
    @TempDir lateinit var dir: File
    private val h by lazy { Harness(dir) }

    /** Fake TVs a test starts besides [Harness.tv]. */
    private val otherTvs = mutableListOf<FakeTv>()

    @AfterEach fun tearDown() {
        h.close()
        otherTvs.forEach { it.close() }
    }

    /** Pairs, then gives the TV a new certificate, as if it were replaced by another device. */
    private fun pairThenChangeCertificate() {
        h.pair()
        h.tv.powerOff()
        h.tv.certificate = FakeTv.newCertificate()
        h.tv.powerOn()
    }

    /**
     * Pairs, then reopens the app and reconnects once the TV treats stored keys by [policy] and
     * answers any later prompt with [thenPrompt].
     */
    private fun reconnectWithStoredKey(policy: KeyPolicy, thenPrompt: PromptAnswer = PromptAnswer.Accept) {
        h.pair()
        h.tv.storedKeys = policy
        h.tv.promptAnswer = thenPrompt
        h.reopen()
        h.session.connect()
    }

    @Test
    fun `a stored key the TV rejects moves the session to needs pairing`() {
        reconnectWithStoredKey(KeyPolicy.RejectWithError)

        h.awaitConnection(ConnectionState.NeedsPairing)
    }

    @Test
    fun `a TV that ignores the stored key and prompts again is re-paired when the prompt is accepted`() {
        reconnectWithStoredKey(KeyPolicy.Prompt, thenPrompt = PromptAnswer.Wait)
        val oldKey = h.tv.issuedKeys.single()

        h.awaitConnection(ConnectionState.AwaitingPrompt)
        h.tv.acceptPrompt()
        h.awaitConnection(ConnectionState.Connected)

        val newKey = h.tv.issuedKeys.last()
        assertNotEquals(oldKey, newKey)
        assertEquals(newKey, h.registry[h.tv.uuid]?.clientKey)
    }

    @Test
    fun `repair discards the rejected key and pairs again by prompt`() {
        reconnectWithStoredKey(KeyPolicy.RejectWithError, thenPrompt = PromptAnswer.Wait)
        h.awaitConnection(ConnectionState.NeedsPairing)

        h.session.repair()
        h.awaitConnection(ConnectionState.AwaitingPrompt)

        assertNull(h.registry[h.tv.uuid]?.clientKey)
        assertNull(h.tv.requests.last { it.type == "register" }.string("client-key"))
        h.tv.acceptPrompt()
        h.awaitConnection(ConnectionState.Connected)
        assertEquals(h.tv.issuedKeys.last(), h.registry[h.tv.uuid]?.clientKey)
    }

    @Test
    fun `repair pairs again by PIN when the TV only offers PIN pairing`() {
        h.tv.pairingTypes = listOf("PIN")
        h.tv.powerOn()
        h.session.connect(h.tv.host)
        h.awaitConnection(ConnectionState.AwaitingPin)
        h.session.submitPin(h.tv.pin)
        h.awaitConnection(ConnectionState.Connected)
        h.tv.storedKeys = KeyPolicy.RejectWithError
        h.reopen()
        h.session.connect()
        h.awaitConnection(ConnectionState.NeedsPairing)

        h.session.repair()
        h.awaitConnection(ConnectionState.AwaitingPin)
        h.session.submitPin(h.tv.pin)
        h.awaitConnection(ConnectionState.Connected)

        assertEquals(2, h.tv.issuedKeys.size)
        assertEquals(h.tv.issuedKeys.last(), h.registry[h.tv.uuid]?.clientKey)
    }

    @Test
    fun `repair while connected closes the connection and pairs again`() {
        h.pair()
        h.tv.promptAnswer = PromptAnswer.Wait

        h.session.repair()
        h.awaitConnection(ConnectionState.AwaitingPrompt)

        h.eventually(message = { "only the pairing connection open, got ${h.tv.openConnections}" }) { h.tv.openConnections == 1 }
        h.tv.acceptPrompt()
        h.awaitConnection(ConnectionState.Connected)
        assertEquals(h.tv.issuedKeys.last(), h.registry[h.tv.uuid]?.clientKey)
    }

    @Test
    fun `a changed certificate stops the connection before anything is sent`() {
        pairThenChangeCertificate()
        val received = h.tv.requests.size

        h.reopen()
        h.session.connect()
        h.awaitConnection(ConnectionState.CertificateMismatch)
        h.settle()

        assertEquals(received, h.tv.requests.size)
        assertEquals(0, h.tv.openConnections)
    }

    @Test
    fun `repair after a certificate change pins the new certificate and the remote works again`() {
        pairThenChangeCertificate()
        val oldPin = h.registry[h.tv.uuid]?.certificatePin
        h.reopen()
        h.session.connect()
        h.awaitConnection(ConnectionState.CertificateMismatch)

        h.session.repair()
        h.awaitConnection(ConnectionState.Connected)
        val newPin = assertNotNull(h.registry[h.tv.uuid]?.certificatePin)
        assertNotEquals(oldPin, newPin)

        h.reopen()
        h.session.connect()
        h.awaitConnection(ConnectionState.Connected)
        h.session.volumeUp()
        h.awaitRequests("ssap://audio/volumeUp")
        assertEquals(newPin, h.registry[h.tv.uuid]?.certificatePin)
    }

    @Test
    fun `a saved TV found at another address with another certificate is a mismatch, and repair pins it there`() {
        h.pair()
        val oldPin = h.registry[h.tv.uuid]?.certificatePin
        h.tv.powerOff()
        val moved = FakeTv(host = "::1", port = h.tv.port, legacyPort = h.tv.legacyPort).also(otherTvs::add)
        moved.uuid = h.tv.uuid
        moved.powerOn()

        h.session.connect(moved.host)
        h.awaitConnection(ConnectionState.CertificateMismatch)

        assertTrue(moved.requests.none { it.type == "register" })
        assertEquals(h.tv.host, h.registry[h.tv.uuid]?.host)
        assertEquals(oldPin, h.registry[h.tv.uuid]?.certificatePin)

        h.session.repair()
        h.awaitConnection(ConnectionState.Connected)
        assertEquals(moved.host, h.registry[h.tv.uuid]?.host)
        assertNotEquals(oldPin, h.registry[h.tv.uuid]?.certificatePin)
        assertEquals(1, moved.issuedKeys.size)
    }
}
