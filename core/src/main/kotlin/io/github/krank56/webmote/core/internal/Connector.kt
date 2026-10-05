package io.github.krank56.webmote.core.internal

import io.github.krank56.webmote.core.ConnectionState
import io.github.krank56.webmote.core.SavedTv
import io.github.krank56.webmote.core.TvInfo
import io.github.krank56.webmote.core.TvState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import java.io.IOException

/** Opens, pairs and registers the connection to a TV, and follows it until it closes. */
internal class Connector(private val core: SessionCore) {
    private var job: Job? = null

    /** Where the current or last attempt is aimed. */
    private var target: Target? = null

    /** A TV to reach at [host]. [tvId] is the saved TV meant there, if any; [name] names a new TV. */
    private class Target(val host: String, val name: String?, val tvId: String? = null)

    /** Connects to the active TV. Does nothing if it's already connected or connecting. */
    fun connect() {
        val tv = core.registry.activeTv ?: return
        start(Target(tv.host, name = null, tvId = tv.id), restart = false)
    }

    /** Connects to the TV at [host], pairing with it if it isn't saved yet. */
    fun connect(host: String, name: String?) = start(Target(host, name), restart = true)

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
            )
        }
        val outcome = try {
            attempt(target, saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failureState(e)
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
            // The deviceUUID says which TV this is, whatever was saved at this address.
            val known = core.registry[id]
            core.update { it.copy(tvId = id) }
            // A saved TV found at a new address must still present its pinned certificate.
            if (known?.certificatePin != null && known.certificatePin != pin) return abandon(socket, ConnectionState.CertificateMismatch)
            val webOsVersion = hello.string("deviceOSReleaseVersion") ?: hello.string("deviceOSVersion")
            val model = runCatching { socket.request(SYSTEM_INFO).string("modelName") }.getOrNull() ?: known?.model
            core.update { it.copy(tvId = id, info = TvInfo(model, webOsVersion)) }

            val clientKey = when (val registration = register(socket, known?.clientKey)) {
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
            core.update { it.copy(tvId = id, connection = ConnectionState.Connected) }
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

        /** Registration stopped in [state]: the pairing was declined. */
        class Stopped(val state: ConnectionState) : Registration
    }

    /**
     * Registers with the TV, pairing by prompt if it asks to. Only `registered` is success; the TV may
     * ask for the prompt first, with a `response` naming the pairing type.
     */
    private suspend fun register(socket: SsapSocket, storedKey: String?): Registration {
        val replies = socket.send("register", payload = Manifest.registerPayload(storedKey, PROMPT))
        while (true) {
            val reply = replies.receiveCatching().getOrNull() ?: throw SocketClosedException()
            when {
                reply.type == "registered" -> {
                    val key = reply.payload.string("client-key") ?: storedKey
                    return if (key != null) Registration.Registered(key) else Registration.Stopped(ConnectionState.PairingDeclined)
                }
                reply.type == "error" || reply.payload.bool("returnValue") == false ->
                    return Registration.Stopped(ConnectionState.PairingDeclined)
                // With a stored key, this means the TV ignored it and asks again (research §3.4).
                reply.type == "response" -> when (reply.payload.string("pairingType")) {
                    PROMPT -> core.update { it.copy(connection = ConnectionState.AwaitingPrompt) }
                }
            }
        }
    }

    private fun failureState(error: Exception): ConnectionState = when {
        isCertificateMismatch(error) -> ConnectionState.CertificateMismatch
        else -> ConnectionState.Off
    }

    private fun closeLink() {
        core.link?.close()
        core.link = null
    }

    private companion object {
        const val SYSTEM_INFO = "ssap://system/getSystemInfo"
        const val PROMPT = "PROMPT"

        fun defaultName(model: String?): String = model?.let { "LG $it" } ?: "LG TV"
    }
}

/** [host] as it goes in a URL: an IPv6 address goes in brackets, as in `wss://[fe80::1]:3001/`. */
internal fun urlHost(host: String): String = if (':' in host && !host.startsWith("[")) "[$host]" else host

