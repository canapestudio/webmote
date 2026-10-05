package io.github.krank56.webmote.core

import io.github.krank56.webmote.core.faketv.FakeTv
import io.github.krank56.webmote.core.faketv.PromptAnswer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingTest {
    @TempDir lateinit var dir: File
    private val h by lazy { Harness(dir) }

    @AfterEach fun tearDown() = h.close()

    @Test
    fun `first pairing by prompt stores the TV with its client key and certificate pin`() {
        h.tv.promptAnswer = PromptAnswer.Wait
        h.tv.powerOn()

        h.session.connect(h.tv.host)
        h.awaitConnection(ConnectionState.AwaitingPrompt)
        h.tv.acceptPrompt()
        h.awaitConnection(ConnectionState.Connected)

        val saved = assertNotNull(h.registry[h.tv.uuid])
        assertEquals(h.tv.host, saved.host)
        assertEquals(h.tv.model, saved.model)
        assertEquals(h.tv.osVersion, saved.webOsVersion)
        assertEquals(h.tv.issuedKeys.single(), saved.clientKey)
        assertNotNull(saved.certificatePin)
        assertEquals(h.tv.uuid, h.registry.activeTvId.value)
        assertEquals(h.tv.uuid, h.state.tvId)
        assertEquals(TvInfo(h.tv.model, h.tv.osVersion), h.state.info)
    }

    @Test
    fun `the handshake is hello, then getSystemInfo, then register with an unsigned manifest and prompt pairing`() {
        h.pair()

        val types = h.tv.requests.map { it.type to it.uri }
        assertEquals(listOf("hello" to null, "request" to FakeTv.SYSTEM_INFO, "register" to null), types.take(3))

        val register = h.tv.requests.first { it.type == "register" }
        assertEquals("PROMPT", register.string("pairingType"))
        assertNull(register.string("client-key"))
        val manifest = register["manifest"] as JsonObject
        assertFalse("signatures" in manifest)
        assertFalse("signed" in manifest)
        val permissions = manifest["permissions"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue(permissions.containsAll(listOf("CONTROL_MOUSE_AND_KEYBOARD", "WRITE_SETTINGS", "WRITE_NOTIFICATION_ALERT")))
    }

    @Test
    fun `reopening the app reconnects with the stored key without prompting`() {
        h.pair()
        val key = h.tv.issuedKeys.single()

        h.reopen()
        h.tv.promptAnswer = PromptAnswer.Wait
        h.session.connect()
        h.awaitConnection(ConnectionState.Connected)

        val register = h.tv.requests.filter { it.type == "register" }.last()
        assertEquals(key, register.string("client-key"))
        assertEquals(1, h.tv.issuedKeys.size)
    }

    @Test
    fun `the state names the address of the TV the session is reaching`() {
        h.tv.promptAnswer = PromptAnswer.Wait
        h.tv.powerOn()
        assertNull(h.state.host)

        h.session.connect(h.tv.host)

        h.awaitConnection(ConnectionState.AwaitingPrompt)
        assertEquals(h.tv.host, h.state.host)
        h.tv.acceptPrompt()
        h.awaitConnection(ConnectionState.Connected)
        assertEquals(h.tv.host, h.state.host)
    }

    @Test
    fun `a declined prompt is reported and nothing is saved`() {
        h.tv.promptAnswer = PromptAnswer.Decline
        h.tv.powerOn()

        h.session.connect(h.tv.host)
        h.awaitConnection(ConnectionState.PairingDeclined)

        assertTrue(h.registry.tvs.value.isEmpty())
    }

    @Test
    fun `volume up and down reach the TV`() {
        h.pair()

        h.session.volumeUp()
        h.session.volumeDown()
        h.session.volumeUp()

        h.eventually { h.tv.requests.count { it.uri?.startsWith("ssap://audio/volume") == true } == 3 }
        assertEquals(
            listOf("ssap://audio/volumeUp", "ssap://audio/volumeDown", "ssap://audio/volumeUp"),
            h.tv.requests.mapNotNull { it.uri }.filter { it.startsWith("ssap://audio/volume") },
        )
    }

    @Test
    fun `an unreachable TV is reported as off`() {
        h.session.connect(h.tv.host)

        h.awaitConnection(ConnectionState.Off)
    }

    @Test
    fun `a saved TV that doesn't answer is reported as off`() {
        h.pair()
        h.tv.powerOff()

        h.reopen()
        h.session.connect()

        h.awaitConnection(ConnectionState.Off)
    }

    @Test
    fun `later connections require the pinned certificate`() {
        h.pair()
        h.tv.powerOff()
        h.tv.certificate = FakeTv.newCertificate()
        h.tv.powerOn()

        h.reopen()
        h.session.connect()

        h.awaitConnection(ConnectionState.CertificateMismatch)
        assertEquals(1, h.tv.requests.count { it.type == "register" })
    }
}
