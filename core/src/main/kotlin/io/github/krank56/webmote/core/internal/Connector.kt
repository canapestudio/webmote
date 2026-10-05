package io.github.krank56.webmote.core.internal

import io.github.krank56.webmote.core.Capabilities
import io.github.krank56.webmote.core.ConnectionState
import io.github.krank56.webmote.core.SavedTv
import io.github.krank56.webmote.core.TvInfo
import io.github.krank56.webmote.core.TvState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException

/** Opens, pairs and registers the connection to a TV, and follows it until it closes. */
internal class Connector(private val core: SessionCore) {
    private var job: Job? = null

    /** Where the current or last attempt is aimed. */
    private var target: Target? = null

    /** Takes the PIN the user submits, while a registration waits for one. */
    private var pinEntry: Channel<String>? = null

    /** A TV to reach at [host]. [tvId] is the saved TV meant there, if any; [name] names a new TV. */
    private class Target(val host: String, val name: String?, val tvId: String? = null)

    /** Connects to the active TV. Does nothing if it's already connected or connecting. */
    fun connect() {
        val tv = core.registry.activeTv ?: return
        start(Target(tv.host, name = null, tvId = tv.id), restart = false)
    }

    /** Connects to the TV at [host], pairing with it if it isn't saved yet. */
    fun connect(host: String, name: String?) = start(Target(host, name), restart = true)

    /** Sends [pin] to the TV, if it's waiting for one. */
    fun submitPin(pin: String) {
        if (core.state.value.connection != ConnectionState.AwaitingPin) return
        pinEntry?.trySend(pin.trim())
    }

    /**
     * Discards the session's TV's client key and certificate pin, closes any connection, and pairs
     * with it again. It's re-paired where the session last reached it, which may be a new address.
     */
    fun repair() {
        val id = core.state.value.tvId ?: core.registry.activeTvId.value ?: return
        val last = target?.takeIf { core.state.value.tvId == id }
        val host = last?.host ?: core.registry[id]?.host ?: return
        core.registry.update(id) { it.copy(clientKey = null, certificatePin = null) }
        start(Target(host, last?.name, id), restart = true)
    }

    fun disconnect() {
        job?.cancel()
        job = null
        closeLink()
        core.update { it.copy(connection = ConnectionState.Disconnected) }
    }

    private fun start(target: Target, restart: Boolean) {
        val busy = job?.isActive == true && this.target?.host == target.host
        if (busy && !restart) return
        job?.cancel()
        closeLink()
        this.target = target
        job = core.scope.launch { run(target) }
    }

    /** Connects to [target] and follows the link until it ends. */
    private suspend fun run(target: Target) {
        val saved = target.tvId?.let { core.registry[it] } ?: core.registry.findByHost(target.host)
        core.update {
            TvState(
                tvId = saved?.id,
                host = target.host,
                connection = ConnectionState.Connecting,
                info = TvInfo(saved?.model, saved?.webOsVersion),
                capabilities = saved?.learnedCapabilities() ?: Capabilities(),
            )
        }
        val outcome = try {
            attempt(target, saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failureState(e, target, saved)
        }
        if (outcome != ConnectionState.Connected) {
            closeLink()
            core.update { it.copy(connection = outcome) }
        }
    }

    /** Runs one connection attempt to the end: returns the state it ended in. */
    private suspend fun attempt(target: Target, saved: SavedTv?): ConnectionState {
        val pin = saved?.certificatePin ?: captureCertificate(target.host)
        val client = PinnedTls.pinnedClient(core.http, pin)
        val socket = SsapSocket.open(client, "wss://${urlHost(target.host)}:${core.config.port}/", core.config.requestTimeout)
        try {
            val hello = hello(socket)
            val id = hello.string("deviceUUID") ?: throw IOException("The TV's hello has no deviceUUID")
            // Another TV answers at a saved TV's address: the saved TV isn't there, and this one
            // mustn't get its key.
            if (target.tvId != null && id != target.tvId) return abandon(socket, ConnectionState.Off)
            // The deviceUUID says which TV this is, whatever was saved at this address. A mismatch
            // below is reported for it, so repair() re-pairs this TV here.
            val known = core.registry[id]
            core.update { it.copy(tvId = id) }
            // A saved TV found at a new address must still present its pinned certificate.
            if (known?.certificatePin != null && known.certificatePin != pin) return abandon(socket, ConnectionState.CertificateMismatch)
            val webOsVersion = hello.string("deviceOSReleaseVersion") ?: hello.string("deviceOSVersion")
            val model = runCatching { socket.request(SYSTEM_INFO).string("modelName") }.getOrNull() ?: known?.model
            core.update { it.copy(tvId = id, info = TvInfo(model, webOsVersion)) }

            val clientKey = when (val registration = register(socket, known?.clientKey, pairingType(hello))) {
                is Registration.Registered -> registration.clientKey
                is Registration.Stopped -> return abandon(socket, registration.state)
            }
            val tv = (known ?: SavedTv(id = id, name = target.name ?: defaultName(model), host = target.host)).copy(
                host = target.host,
                model = model,
                webOsVersion = webOsVersion,
                clientKey = clientKey,
                certificatePin = pin,
            )
            core.registry.save(tv)
            core.registry.setActive(id)

            val link = Link(id, target.host, webOsVersion, socket, client, core.scope)
            core.link = link
            core.update {
                it.copy(
                    tvId = id,
                    connection = ConnectionState.Connected,
                    capabilities = tv.learnedCapabilities(),
                )
            }
            core.features.forEach { it.onConnected(link) }
            socket.closed.await()
            core.link = null
            link.scope.coroutineContext[Job]?.cancel()
            return ConnectionState.Off
        } catch (e: Throwable) {
            socket.close()
            throw e
        }
    }

    private suspend fun captureCertificate(host: String): String = withContext(core.config.ioDispatcher) {
        PinnedTls.fingerprint(
            PinnedTls.captureCertificate(host, core.config.port, core.config.connectTimeout.inWholeMilliseconds.toInt()),
        )
    }

    private suspend fun hello(socket: SsapSocket): JsonObject {
        val replies = socket.send("hello", id = "hello", payload = Manifest.helloPayload())
        try {
            return withTimeout(core.config.requestTimeout) { replies.receive() }.payload
        } finally {
            socket.forget("hello")
        }
    }

    /** Ends an attempt that stopped before the connection was registered. */
    private fun abandon(socket: SsapSocket, state: ConnectionState): ConnectionState {
        socket.close()
        return state
    }

    private sealed interface Registration {
        class Registered(val clientKey: String) : Registration

        /** Registration stopped in [state]: declined, or the TV wants pairing again. */
        class Stopped(val state: ConnectionState) : Registration
    }

    /**
     * Registers with the TV, pairing by [pairingType] if it asks to. Only `registered` is success;
     * the TV may ask for the prompt or a PIN first, with a `response` naming the pairing type.
     *
     * While the TV waits for a PIN, a PIN from [submitPin] goes to `ssap://pairing/setPin`. The TV
     * may answer that request before sending `registered`, or answer it with `registered`.
     */
    private suspend fun register(socket: SsapSocket, storedKey: String?, pairingType: String): Registration {
        val replies = socket.send("register", payload = Manifest.registerPayload(storedKey, pairingType))
        val pins = Channel<String>(Channel.CONFLATED).also { pinEntry = it }
        var pinReplies: ReceiveChannel<SsapMessage>? = null
        // Whether the TV went on to ask the user (prompt or PIN) rather than take the stored key.
        var asked = false
        try {
            while (true) {
                val reply = select<SsapMessage?> {
                    replies.onReceiveCatching { it.getOrNull() ?: throw SocketClosedException() }
                    pins.onReceive { pin ->
                        pinReplies = socket.send("request", SET_PIN, buildJsonObject { put("pin", pin) })
                        null
                    }
                    pinReplies?.let { channel -> channel.onReceiveCatching { it.getOrNull() ?: throw SocketClosedException() } }
                } ?: continue
                when {
                    reply.type == "registered" -> {
                        val key = reply.payload.string("client-key") ?: storedKey
                        return if (key != null) Registration.Registered(key) else Registration.Stopped(ConnectionState.PairingDeclined)
                    }
                    // A refused stored key: 401 insufficient permissions, or webOS 26's 403 blacklisted.
                    reply.type == "error" || reply.payload.bool("returnValue") == false ->
                        return Registration.Stopped(
                            if (storedKey != null && !asked) ConnectionState.NeedsPairing else ConnectionState.PairingDeclined,
                        )
                    // With a stored key, this means the TV ignored it and asks again (research §3.4).
                    reply.type == "response" -> when (reply.payload.string("pairingType")) {
                        PROMPT -> {
                            asked = true
                            core.update { it.copy(connection = ConnectionState.AwaitingPrompt) }
                        }
                        PIN -> {
                            asked = true
                            core.update { it.copy(connection = ConnectionState.AwaitingPin) }
                        }
                    }
                }
            }
        } finally {
            pinEntry = null
        }
    }

    /** Prompt pairing, unless the TV only offers PIN pairing. */
    private fun pairingType(hello: JsonObject): String {
        val offered = hello.stringList("pairingTypes")
        return if (PIN in offered && PROMPT !in offered) PIN else PROMPT
    }

    private suspend fun failureState(error: Exception, target: Target, saved: SavedTv?): ConnectionState = when {
        isCertificateMismatch(error) -> ConnectionState.CertificateMismatch
        // A TV with a pinned certificate has already paired on the encrypted port, so it isn't a
        // webOS 1-3 set even if only the plain port answers for now (booting, or standby).
        isRefused(error) && saved?.certificatePin == null && answersOnLegacyPort(target.host) -> ConnectionState.Unsupported
        else -> ConnectionState.Off
    }

    /** Whether the TV didn't accept the TCP connection at all, as opposed to failing later. */
    private fun isRefused(error: Exception): Boolean =
        error is ConnectException || error is SocketTimeoutException || error is NoRouteToHostException

    /** Whether the plain port that only pre-2018 TVs rely on accepts a TCP connection. */
    private suspend fun answersOnLegacyPort(host: String): Boolean = core.acceptsConnection(host, core.config.legacyPort)

    private fun closeLink() {
        core.link?.close()
        core.link = null
    }

    private companion object {
        const val SYSTEM_INFO = "ssap://system/getSystemInfo"
        const val PROMPT = "PROMPT"
        const val PIN = "PIN"
        const val SET_PIN = "ssap://pairing/setPin"

        fun defaultName(model: String?): String = model?.let { "LG $it" } ?: "LG TV"
    }
}

/** [host] as it goes in a URL: an IPv6 address goes in brackets, as in `wss://[fe80::1]:3001/`. */
internal fun urlHost(host: String): String = if (':' in host && !host.startsWith("[")) "[$host]" else host

/**
 * Whether [host] accepts a TCP connection on [port] within the connect timeout. A cheap probe that
 * sends nothing, so it doesn't disturb the TV or the reported state. Runs on the IO dispatcher.
 */
internal suspend fun SessionCore.acceptsConnection(host: String, port: Int): Boolean = withContext(config.ioDispatcher) {
    try {
        Socket().use { it.connect(InetSocketAddress(host, port), config.connectTimeout.inWholeMilliseconds.toInt()) }
        true
    } catch (e: IOException) {
        false
    }
}

internal fun SavedTv.learnedCapabilities() = Capabilities(
    pointer = capabilities.pointer,
    volumeLevel = capabilities.volumeLevel,
)

internal fun JsonObject.stringList(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()
