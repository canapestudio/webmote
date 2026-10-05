package io.github.krank56.webmote.core.faketv

import io.github.krank56.webmote.core.SessionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

/**
 * A scriptable stand-in for a webOS TV on loopback: an encrypted SSAP WebSocket server with a
 * self-signed certificate.
 *
 * Every behaviour is a property tests can change before or during a test. Everything the TV
 * receives is recorded in [requests].
 */
class FakeTv(
    val host: String = "127.0.0.1",
    /** The encrypted SSAP port. Nothing listens on it while the TV is off. */
    val port: Int = reservePort(),
) : AutoCloseable {
    private val loopback: InetAddress = InetAddress.getByName(host)

    // Identity

    @Volatile var uuid: String = "fake-tv-uuid-1"
    @Volatile var model: String = "OLED55C6LA"
    /** The hello's `deviceOSReleaseVersion`; webOS 26 reports 11.x. */
    @Volatile var osVersion: String = "11.2.0"
    @Volatile var certificate: HeldCertificate = newCertificate()

    // Pairing

    /** How the user answers the pairing prompt. [PromptAnswer.Wait] leaves it to [acceptPrompt] / [declinePrompt]. */
    @Volatile var promptAnswer: PromptAnswer = PromptAnswer.Accept

    /** The client keys this TV has issued. */
    val issuedKeys: MutableList<String> = CopyOnWriteArrayList()

    // Recording

    val requests: MutableList<Received> = CopyOnWriteArrayList()

    // Wiring

    private val handlers = java.util.concurrent.ConcurrentHashMap<String, (Received) -> JsonObject>()
    private val clients = CopyOnWriteArrayList<Client>()
    private var server: MockWebServer? = null
    private val pendingPrompts = CopyOnWriteArrayList<() -> Unit>()
    private val pendingDeclines = CopyOnWriteArrayList<() -> Unit>()

    val isOn: Boolean get() = server != null

    init {
        installDefaultHandlers()
    }

    /** The session configuration that aims a session at this TV. */
    fun sessionConfig(): SessionConfig = SessionConfig(
        port = port,
        connectTimeout = 2.seconds,
        ioDispatcher = Dispatchers.IO,
    )

    // Power

    /** Starts listening, with the current [certificate]. */
    @Synchronized
    fun powerOn() {
        if (isOn) return
        server = MockWebServer().apply {
            useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory())
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse.Builder().webSocketUpgrade(Client()).build()
            }
            start(loopback, this@FakeTv.port)
        }
    }

    /** Stops listening and drops every connection, like a TV going to standby. */
    @Synchronized
    fun powerOff() {
        clients.forEach { it.socket.close(1001, "going away") }
        clients.clear()
        server?.close()
        server = null
    }

    // Pairing prompt

    fun acceptPrompt() {
        pendingPrompts.forEach { it() }
        pendingPrompts.clear()
        pendingDeclines.clear()
    }

    fun declinePrompt() {
        pendingDeclines.forEach { it() }
        pendingPrompts.clear()
        pendingDeclines.clear()
    }

    // Scripting

    /** Replaces how the TV answers [uri]. The handler returns the reply payload, or throws [TvError]. */
    fun on(uri: String, handler: (Received) -> JsonObject) {
        handlers[uri] = handler
    }

    override fun close() {
        powerOff()
    }

    // SSAP

    private inner class Client : WebSocketListener() {
        lateinit var socket: WebSocket
        @Volatile var registered = false

        override fun onOpen(webSocket: WebSocket, response: Response) {
            socket = webSocket
            clients += this
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = Json.parseToJsonElement(text).jsonObject
            val received = Received(
                type = message["type"]?.jsonPrimitive?.contentOrNull ?: "",
                id = message["id"]?.jsonPrimitive?.contentOrNull,
                uri = message["uri"]?.jsonPrimitive?.contentOrNull,
                payload = message["payload"] as? JsonObject ?: JsonObject(emptyMap()),
            )
            requests += received
            when (received.type) {
                // Firmware 33.20 and later ignores a hello without a payload key.
                "hello" -> if ("payload" in message) send("hello", received.id, helloPayload())
                "register" -> register(received)
                "request" -> request(received)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            clients -= this
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            clients -= this
        }

        private fun register(received: Received) {
            val manifest = received.payload["manifest"] as? JsonObject
            if (manifest != null && ("signatures" in manifest || "signed" in manifest)) {
                sendError(received.id, "403 Error!! blacklisted certificate")
                return
            }
            val key = received.payload["client-key"]?.jsonPrimitive?.contentOrNull
            if (key != null && key in issuedKeys) {
                return registered(received.id, key)
            } else if (key != null) {
                return sendError(received.id, "401 insufficient permissions")
            }
            // Ready for the user's answer before the client can report the prompt.
            if (promptAnswer == PromptAnswer.Wait) {
                pendingPrompts += { registered(received.id, newKey()) }
                pendingDeclines += { sendError(received.id, "403 cancelled") }
            }
            send("response", received.id, buildJsonObject { put("pairingType", "PROMPT"); put("returnValue", true) })
            when (promptAnswer) {
                PromptAnswer.Accept -> registered(received.id, newKey())
                PromptAnswer.Decline -> sendError(received.id, "403 cancelled")
                PromptAnswer.Wait -> Unit
            }
        }

        private fun registered(id: String?, key: String) {
            registered = true
            send("registered", id, buildJsonObject { put("client-key", key) })
        }

        private fun request(received: Received) {
            val uri = received.uri ?: return sendError(received.id, "400 no uri")
            // Newer firmware only answers getSystemInfo before registration, and nothing else until then.
            if (registered && uri == SYSTEM_INFO) return sendError(received.id, "401 insufficient permissions")
            if (!registered && uri != SYSTEM_INFO) return sendError(received.id, "401 insufficient permissions (not registered)")
            val handler = handlers[uri] ?: return sendError(received.id, "404 no such service or method")
            try {
                send("response", received.id, handler(received))
            } catch (e: TvError) {
                sendError(received.id, e.message ?: "500 error")
            }
        }

        fun send(type: String, id: String?, payload: JsonObject) {
            socket.send(
                buildJsonObject {
                    put("type", type)
                    if (id != null) put("id", id)
                    put("payload", payload)
                }.toString(),
            )
        }

        fun sendError(id: String?, error: String) {
            socket.send(
                buildJsonObject {
                    put("type", "error")
                    if (id != null) put("id", id)
                    put("error", error)
                    put("payload", JsonObject(emptyMap()))
                }.toString(),
            )
        }
    }

    private fun helloPayload() = buildJsonObject {
        put("protocolVersion", 1)
        put("deviceType", "tv")
        put("deviceOS", "webOS")
        put("deviceOSVersion", "4.1.0")
        put("deviceOSReleaseVersion", osVersion)
        put("deviceUUID", uuid)
    }

    private fun newKey(): String = UUID.randomUUID().toString().replace("-", "").also { issuedKeys += it }

    // Default behaviour for every URI in the command contract

    private fun installDefaultHandlers() {
        on(SYSTEM_INFO) { ok { put("modelName", model); put("receiverType", "dvb") } }

        on("ssap://audio/volumeUp") { ok() }
        on("ssap://audio/volumeDown") { ok() }
    }

    companion object {
        const val SYSTEM_INFO = "ssap://system/getSystemInfo"

        fun newCertificate(): HeldCertificate = HeldCertificate.Builder()
            .commonName("LG webOS TV ${UUID.randomUUID()}")
            .addSubjectAlternativeName("localhost")
            .build()

        private fun reservePort(): Int = ServerSocket(0).use { it.localPort }

        /** A successful reply payload. */
        fun ok(build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}): JsonObject = buildJsonObject {
            put("returnValue", true)
            build()
        }
    }
}

/** A message the fake TV received on its SSAP socket. */
data class Received(val type: String, val id: String?, val uri: String?, val payload: JsonObject) {
    fun string(key: String): String? = (payload[key] as? JsonPrimitive)?.contentOrNull
    operator fun get(key: String): JsonElement? = payload[key]
}

/** Thrown by a handler to answer with an SSAP error. */
class TvError(message: String) : Exception(message)

enum class PromptAnswer { Accept, Decline, Wait }
