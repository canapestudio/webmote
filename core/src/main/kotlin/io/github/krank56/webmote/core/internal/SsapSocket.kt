package io.github.krank56.webmote.core.internal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

/** The TV answered a request with an `error` message, or with `returnValue: false`. */
internal class SsapException(message: String) : Exception(message)

/** The socket closed while a reply was still expected. */
internal class SocketClosedException(cause: Throwable? = null) : IOException("SSAP socket closed", cause)

/** One SSAP message from the TV. */
internal class SsapMessage(val type: String, val id: String?, val payload: JsonObject, val error: String?)

/**
 * An open SSAP WebSocket. Replies are matched to the message that caused them by `id`.
 *
 * Thread-safe: OkHttp delivers messages on its own threads, and callers may send from any thread.
 */
internal class SsapSocket private constructor(private val requestTimeout: Duration) {
    private lateinit var webSocket: WebSocket
    private val replies = ConcurrentHashMap<String, Channel<SsapMessage>>()
    private val nextId = AtomicInteger()

    /** Completes when the socket closes, for whatever reason. */
    val closed = CompletableDeferred<Unit>()

    /** Sends a message and returns every reply the TV sends with the same id, until [forget] or close. */
    fun send(type: String, uri: String? = null, payload: JsonObject? = null, id: String = newId()): ReceiveChannel<SsapMessage> {
        val channel = Channel<SsapMessage>(Channel.UNLIMITED)
        replies[id] = channel
        if (closed.isCompleted) channel.close(SocketClosedException())
        val message = buildJsonObject {
            put("type", type)
            put("id", id)
            if (uri != null) put("uri", uri)
            if (payload != null) put("payload", payload)
        }
        if (!webSocket.send(message.toString())) channel.close(SocketClosedException())
        return channel
    }

    /** Sends a request and returns the reply's payload, or throws [SsapException] if the TV refused it. */
    suspend fun request(uri: String, payload: JsonObject? = null): JsonObject {
        val id = newId()
        val channel = send("request", uri, payload, id)
        try {
            val reply = withTimeout(requestTimeout) { channel.receive() }
            reply.throwIfError(uri)
            return reply.payload
        } finally {
            forget(id)
        }
    }

    fun forget(id: String) {
        replies.remove(id)?.close()
    }

    fun close() {
        webSocket.close(NORMAL_CLOSURE, null)
        onClosed(null)
    }

    private fun newId(): String = "m${nextId.incrementAndGet()}"

    private fun onMessage(text: String) {
        val message = parse(text) ?: return
        val id = message.id?.takeIf(replies::containsKey) ?: message.type.takeIf(replies::containsKey) ?: return
        replies[id]?.trySend(message)
    }

    private fun onClosed(cause: Throwable?) {
        if (!closed.complete(Unit)) return
        val error = SocketClosedException(cause)
        replies.values.forEach { it.close(error) }
        replies.clear()
    }

    private fun SsapMessage.throwIfError(uri: String) {
        if (type == "error") throw SsapException("$uri: ${error ?: "error"}")
        val returnValue = payload["returnValue"]?.jsonPrimitive?.booleanOrNull
        if (returnValue == false) {
            val text = payload["errorText"]?.jsonPrimitive?.contentOrNull ?: payload["errorCode"]?.toString()
            throw SsapException("$uri: ${text ?: "returnValue false"}")
        }
    }

    companion object {
        private const val NORMAL_CLOSURE = 1000

        /** Opens a WebSocket to [url]. Throws whatever stopped the connection (refused, TLS, timeout). */
        suspend fun open(client: OkHttpClient, url: String, requestTimeout: Duration): SsapSocket {
            val socket = SsapSocket(requestTimeout)
            val opened = CompletableDeferred<Unit>()
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened.complete(Unit)
                }

                override fun onMessage(webSocket: WebSocket, text: String) = socket.onMessage(text)

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(NORMAL_CLOSURE, null)
                    socket.onClosed(null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = socket.onClosed(null)

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    opened.completeExceptionally(t)
                    socket.onClosed(t)
                }
            }
            socket.webSocket = client.newWebSocket(Request.Builder().url(url).build(), listener)
            try {
                opened.await()
            } catch (e: Throwable) {
                socket.webSocket.cancel()
                throw e
            }
            return socket
        }

        private val json = Json { ignoreUnknownKeys = true }

        private fun parse(text: String): SsapMessage? {
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
            return SsapMessage(
                type = obj.string("type") ?: return null,
                id = obj.string("id"),
                payload = (obj["payload"] as? JsonObject) ?: JsonObject(emptyMap()),
                error = obj.string("error"),
            )
        }
    }
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
