package io.github.krank56.webmote.core.faketv

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

    @Volatile var certificate: HeldCertificate = newCertificate()

    // Recording

    val requests: MutableList<Received> = CopyOnWriteArrayList()

    // Wiring

    private val handlers = java.util.concurrent.ConcurrentHashMap<String, (Received) -> JsonObject>()
    private val clients = CopyOnWriteArrayList<Client>()
    private var server: MockWebServer? = null

    val isOn: Boolean get() = server != null

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

        private fun request(received: Received) {
            val uri = received.uri ?: return sendError(received.id, "400 no uri")
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

    companion object {
        fun newCertificate(): HeldCertificate = HeldCertificate.Builder()
            .commonName("LG webOS TV ${UUID.randomUUID()}")
            .addSubjectAlternativeName("localhost")
            .build()

        private fun reservePort(): Int = ServerSocket(0).use { it.localPort }
    }
}

/** A message the fake TV received on its SSAP socket. */
data class Received(val type: String, val id: String?, val uri: String?, val payload: JsonObject)

/** Thrown by a handler to answer with an SSAP error. */
class TvError(message: String) : Exception(message)
