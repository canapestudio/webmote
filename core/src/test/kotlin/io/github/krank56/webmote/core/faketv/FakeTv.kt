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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

/**
 * A scriptable stand-in for a webOS TV on loopback: an encrypted SSAP WebSocket server with a
 * self-signed certificate, the pointer socket it hands out, a UDP listener that captures magic
 * packets, and an SSDP responder.
 *
 * Every behaviour is a property tests can change before or during a test. Everything the TV
 * receives is recorded in [requests], [pointerMessages], [magicPackets] and [ssdpSearches].
 *
 * A second TV for the same session runs on another loopback address with the same ports, since a
 * session uses one port for every TV: `FakeTv(host = "::1", port = first.port, legacyPort = first.legacyPort)`.
 */
class FakeTv(
    val host: String = "127.0.0.1",
    /** The encrypted SSAP port. Nothing listens on it while the TV is off. */
    val port: Int = reservePort(),
    /** The plain pre-2018 port. Only listens when [legacyOnly] is set and the TV is on. */
    val legacyPort: Int = reservePort(),
) : AutoCloseable {
    private val loopback: InetAddress = InetAddress.getByName(host)

    /** [host] as it appears in a URL: IPv6 addresses go in brackets. */
    private val urlHost: String = if (':' in host) "[$host]" else host

    // Identity

    @Volatile var uuid: String = "fake-tv-uuid-1"
    @Volatile var model: String = "OLED55C6LA"
    /** The hello's `deviceOSReleaseVersion`; webOS 26 reports 11.x. */
    @Volatile var osVersion: String = "11.2.0"
    @Volatile var wiredMac: String? = "a8:23:fe:00:00:01"
    @Volatile var wifiMac: String? = "a8:23:fe:00:00:02"
    @Volatile var certificate: HeldCertificate = newCertificate()

    /** Only the plain port answers, as on webOS 1–3. */
    @Volatile var legacyOnly: Boolean = false

    // Pairing

    /** The pairing types the hello advertises. */
    @Volatile var pairingTypes: List<String> = listOf("PROMPT", "PIN")

    /** How the user answers the pairing prompt. [PromptAnswer.Wait] leaves it to [acceptPrompt] / [declinePrompt]. */
    @Volatile var promptAnswer: PromptAnswer = PromptAnswer.Accept

    /** The PIN the TV shows when pairing by PIN. */
    @Volatile var pin: String = "12345678"

    /** How the TV answers the right PIN; no capture shows which real TVs do (research §3.5). */
    @Volatile var pinReply: PinReply = PinReply.ResponseThenRegistered

    /** How the TV treats a stored client key it issued earlier. */
    @Volatile var storedKeys: KeyPolicy = KeyPolicy.Accept

    /** The client keys this TV has issued. */
    val issuedKeys: MutableList<String> = CopyOnWriteArrayList()

    // Volume

    /** The level the TV reports, or null when it reports none (sound goes to a soundbar). */
    @Volatile var volume: Int? = 12
    @Volatile var muted: Boolean = false

    // Pointer socket

    /** Whether the pointer socket accepts connections. When false the upgrade is refused. */
    @Volatile var pointerSocketAvailable: Boolean = true

    // Screen off

    /** The screen-off/on URIs this TV implements; the others answer with an error. */
    @Volatile var screenOffUris: Set<String> = ScreenOffUris.all

    // Wake-on-LAN

    /** Whether a magic packet for one of this TV's MACs powers it on. */
    @Volatile var wakesOnMagicPacket: Boolean = true

    // Recording

    val requests: MutableList<Received> = CopyOnWriteArrayList()
    val pointerMessages: MutableList<String> = CopyOnWriteArrayList()
    val magicPackets: MutableList<ByteArray> = CopyOnWriteArrayList()

    /** Received requests for [uri], in order. */
    fun requests(uri: String): List<Received> = requests.filter { it.uri == uri }

    // Wiring

    private val handlers = java.util.concurrent.ConcurrentHashMap<String, (Received) -> JsonObject>()
    private val clients = CopyOnWriteArrayList<Client>()
    private var server: MockWebServer? = null
    private var legacyServer: ServerSocket? = null
    private val pendingPrompts = CopyOnWriteArrayList<() -> Unit>()
    private val pendingDeclines = CopyOnWriteArrayList<() -> Unit>()
    private val pointerPath = "/resources/${UUID.randomUUID()}/netinput.pointer.sock"

    private val wakeSocket = DatagramSocket(0, loopback)
    val wakePort: Int get() = wakeSocket.localPort

    private val ssdpSocket = DatagramSocket(0, loopback)
    val ssdpPort: Int get() = ssdpSocket.localPort

    /** The name the TV's DLNA MediaRenderer announces in `DLNADeviceName.lge.com`, before URL encoding. */
    @Volatile var friendlyName: String = "[LG] webOS TV OLED55C6LA"

    /**
     * The SSDP responses the TV sends to an M-SEARCH, given its search target (`ST`). By default it
     * answers like a real TV (research §11.2): the second-screen search gets a response with no
     * name, and the MediaRenderer search one carrying `DLNADeviceName.lge.com`.
     */
    @Volatile var ssdpResponses: (searchTarget: String) -> List<String> = { defaultSsdpResponses(it) }

    /** Every M-SEARCH the SSDP responder received, as text. */
    val ssdpSearches: MutableList<String> = CopyOnWriteArrayList()

    val isOn: Boolean get() = server != null || legacyServer != null

    /** How many SSAP connections are open (not counting pointer sockets). */
    val openConnections: Int get() = clients.size

    init {
        installDefaultHandlers()
        thread(isDaemon = true, name = "fake-tv-wol") { receiveMagicPackets() }
        thread(isDaemon = true, name = "fake-tv-ssdp") { answerSsdp() }
    }

    /** The session configuration that aims a session at this TV. */
    fun sessionConfig(): SessionConfig = SessionConfig(
        port = port,
        legacyPort = legacyPort,
        wakeAddress = host,
        wakePort = wakePort,
        // The phone's real networks have nothing to do with a TV on loopback.
        localNetworks = { emptyList() },
        connectTimeout = 2.seconds,
        ioDispatcher = Dispatchers.IO,
    )

    // Power

    /** Starts listening, with the current [certificate]. */
    @Synchronized
    fun powerOn() {
        if (isOn) return
        if (legacyOnly) {
            legacyServer = ServerSocket().apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(loopback, legacyPort))
            }.also { socket -> thread(isDaemon = true) { acceptForever(socket) } }
            return
        }
        server = MockWebServer().apply {
            useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory())
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = route(request)
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
        legacyServer?.close()
        legacyServer = null
    }

    /** Closes every open connection but keeps listening. */
    fun dropConnections() {
        clients.forEach { it.socket.close(1001, "going away") }
        clients.clear()
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

    /** Sends [payload] to every client subscribed to [uri]. */
    fun push(uri: String, payload: JsonObject) {
        clients.forEach { it.push(uri, payload) }
    }

    /** Changes the volume as the physical remote would, notifying subscribers. */
    fun changeVolumeFromRemote(level: Int?, muted: Boolean = this.muted) {
        volume = level
        this.muted = muted
        pushVolume()
    }

    override fun close() {
        powerOff()
        wakeSocket.close()
        ssdpSocket.close()
    }

    // SSAP

    private fun route(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        return when {
            path == pointerPath && pointerSocketAvailable ->
                MockResponse.Builder().webSocketUpgrade(PointerListener()).build()
            path == pointerPath -> MockResponse.Builder().code(403).build()
            else -> MockResponse.Builder().webSocketUpgrade(Client()).build()
        }
    }

    private inner class Client : WebSocketListener() {
        lateinit var socket: WebSocket
        @Volatile var registered = false
        private val subscriptions = CopyOnWriteArrayList<Pair<String, String>>()

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
                "request", "subscribe" -> request(received)
                "unsubscribe" -> subscriptions.removeIf { it.second == received.id }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            clients -= this
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            clients -= this
        }

        fun push(uri: String, payload: JsonObject) {
            subscriptions.filter { it.first == uri }.forEach { (_, id) -> send("response", id, payload) }
        }

        private fun register(received: Received) {
            val manifest = received.payload["manifest"] as? JsonObject
            if (manifest != null && ("signatures" in manifest || "signed" in manifest)) {
                sendError(received.id, "403 Error!! blacklisted certificate")
                return
            }
            val key = received.payload["client-key"]?.jsonPrimitive?.contentOrNull
            if (key != null && key in issuedKeys) {
                when (storedKeys) {
                    KeyPolicy.Accept -> return registered(received.id, key)
                    KeyPolicy.RejectWithError -> return sendError(received.id, "401 insufficient permissions")
                    KeyPolicy.Prompt -> Unit
                }
            } else if (key != null) {
                return sendError(received.id, "401 insufficient permissions")
            }
            when (received.payload["pairingType"]?.jsonPrimitive?.contentOrNull ?: "PROMPT") {
                "PIN" -> {
                    // Ready for the PIN before telling the client to send it.
                    pinRegistration = received.id
                    send("response", received.id, buildJsonObject { put("pairingType", "PIN"); put("returnValue", true) })
                }
                else -> {
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
            }
        }

        @Volatile private var pinRegistration: String? = null

        private fun registered(id: String?, key: String) {
            registered = true
            send("registered", id, buildJsonObject { put("client-key", key) })
        }

        private fun request(received: Received) {
            val uri = received.uri ?: return sendError(received.id, "400 no uri")
            if (uri == SET_PIN) {
                val registration = pinRegistration
                val entered = received.payload["pin"]?.jsonPrimitive?.contentOrNull
                if (registration == null) return sendError(received.id, "400 not pairing")
                pinRegistration = null
                if (entered == pin) {
                    when (pinReply) {
                        PinReply.ResponseThenRegistered -> {
                            send("response", received.id, buildJsonObject { put("returnValue", true) })
                            registered(registration, newKey())
                        }
                        PinReply.RegisteredOnly -> registered(registration, newKey())
                        PinReply.RegisteredAsSetPinReply -> registered(received.id, newKey())
                    }
                } else {
                    sendError(received.id, "403 wrong PIN")
                    sendError(registration, "403 cancelled")
                }
                return
            }
            // Newer firmware only answers getSystemInfo before registration, and nothing else until then.
            if (registered && uri == SYSTEM_INFO) return sendError(received.id, "401 insufficient permissions")
            if (!registered && uri != SYSTEM_INFO) return sendError(received.id, "401 insufficient permissions (not registered)")
            val handler = handlers[uri] ?: return sendError(received.id, "404 no such service or method")
            if (received.type == "subscribe" && received.id != null) subscriptions += uri to received.id
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

    private val pointerSockets = CopyOnWriteArrayList<WebSocket>()

    /** Closes the open pointer sockets but keeps the main connections, as the TV sometimes does. */
    fun dropPointerSocket() {
        pointerSockets.forEach { it.close(1001, "going away") }
        pointerSockets.clear()
    }

    private inner class PointerListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            pointerSockets += webSocket
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            pointerMessages += text
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            pointerSockets -= webSocket
        }
    }

    private fun helloPayload() = buildJsonObject {
        put("protocolVersion", 1)
        put("deviceType", "tv")
        put("deviceOS", "webOS")
        put("deviceOSVersion", "4.1.0")
        put("deviceOSReleaseVersion", osVersion)
        put("deviceUUID", uuid)
        putJsonArray("pairingTypes") { pairingTypes.forEach { add(JsonPrimitive(it)) } }
    }

    private fun newKey(): String = UUID.randomUUID().toString().replace("-", "").also { issuedKeys += it }

    // Default behaviour for every URI in the command contract

    private fun installDefaultHandlers() {
        on(SYSTEM_INFO) { ok { put("modelName", model); put("receiverType", "dvb") } }

        on("ssap://audio/volumeUp") { volume = volume?.let { (it + 1).coerceAtMost(100) }; pushVolume(); ok() }
        on("ssap://audio/volumeDown") { volume = volume?.let { (it - 1).coerceAtLeast(0) }; pushVolume(); ok() }
        on("ssap://audio/setVolume") { r ->
            if (volume != null) volume = r.payload["volume"]!!.jsonPrimitive.int
            pushVolume()
            ok()
        }
        on("ssap://audio/setMute") { r ->
            muted = r.payload["mute"]!!.jsonPrimitive.contentOrNull == "true"
            pushVolume()
            ok()
        }
        on(GET_VOLUME) { volumePayload() }
        on("ssap://audio/getStatus") { volumePayload() }

        on("ssap://system/turnOff") { ok().also { thread(isDaemon = true) { Thread.sleep(50); powerOff() } } }
        ScreenOffUris.all.forEach { uri ->
            on(uri) { if (uri in screenOffUris) ok() else throw TvError("404 no such service or method") }
        }

        on("ssap://com.webos.service.connectionmanager/getinfo") {
            ok {
                wiredMac?.let { putJsonObject("wiredInfo") { put("macAddress", it) } }
                wifiMac?.let { putJsonObject("wifiInfo") { put("macAddress", it) } }
            }
        }
        on("ssap://com.webos.service.networkinput/getPointerInputSocket") {
            ok { put("socketPath", "wss://$urlHost:$port$pointerPath") }
        }
    }

    private fun pushVolume() = push(GET_VOLUME, volumePayload())

    private fun volumePayload(): JsonObject = ok {
        put("callerId", "secondscreen.client")
        putJsonObject("volumeStatus") {
            put("activeStatus", true)
            put("adjustVolume", volume != null)
            put("maxVolume", 100)
            put("muteStatus", muted)
            put("volume", volume ?: -1)
            put("mode", "normal")
            put("soundOutput", if (volume != null) "tv_speaker" else "external_arc")
        }
    }

    // UDP: Wake-on-LAN and SSDP

    private fun receiveMagicPackets() {
        val buffer = ByteArray(1024)
        while (!wakeSocket.isClosed) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                wakeSocket.receive(packet)
            } catch (_: SocketException) {
                return
            }
            val bytes = packet.data.copyOf(packet.length)
            magicPackets += bytes
            val macs = listOfNotNull(wiredMac, wifiMac).map(::macBytes)
            if (wakesOnMagicPacket && macs.any { isMagicPacketFor(bytes, it) }) powerOn()
        }
    }

    private fun answerSsdp() {
        val buffer = ByteArray(2048)
        while (!ssdpSocket.isClosed) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                ssdpSocket.receive(packet)
            } catch (_: SocketException) {
                return
            }
            val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
            if (!text.startsWith("M-SEARCH")) continue
            ssdpSearches += text
            val searchTarget = text.lineSequence()
                .firstOrNull { it.startsWith("ST:", ignoreCase = true) }
                ?.substringAfter(':')?.trim() ?: continue
            ssdpResponses(searchTarget).forEach { response ->
                val bytes = response.toByteArray(Charsets.UTF_8)
                ssdpSocket.send(DatagramPacket(bytes, bytes.size, packet.socketAddress))
            }
        }
    }

    /** How a real TV answers: only the search targets it implements, one response each. */
    fun defaultSsdpResponses(searchTarget: String): List<String> = when (searchTarget) {
        SECOND_SCREEN -> listOf(secondScreenResponse())
        MEDIA_RENDERER -> listOf(mediaRendererResponse())
        else -> emptyList()
    }

    /** The webOS second-screen response, as captured from a real TV: it carries no name. */
    fun secondScreenResponse(location: String? = "http://$urlHost:1048/"): String =
        "HTTP/1.1 200 OK\r\n" +
            (if (location != null) "Location: $location\r\n" else "") +
            "Cache-Control: max-age=1800\r\n" +
            "Server: WebOS/4.1.0 UPnP/1.0\r\n" +
            "EXT:\r\n" +
            "USN: uuid:$uuid::$SECOND_SCREEN\r\n" +
            "ST: $SECOND_SCREEN\r\n" +
            "Date: Mon, 05 Oct 2026 10:00:00 GMT\r\n" +
            "\r\n"

    /**
     * The DLNA MediaRenderer response, as captured from a real TV: its own UUID, and the friendly
     * [name] URL-encoded with lower-case hex in `DLNADeviceName.lge.com`.
     */
    fun mediaRendererResponse(name: String? = friendlyName, location: String = "http://$urlHost:1792/"): String =
        "HTTP/1.1 200 OK\r\n" +
            "Location: $location\r\n" +
            "Cache-Control: max-age=1800\r\n" +
            "Server: Linux/i686 UPnP/1,0 DLNADOC/1.50 LGE WebOS TV/Version 0.9\r\n" +
            "EXT:\r\n" +
            "USN: uuid:135f5df5-c5c5-572e-030d-be1a7940f080::$MEDIA_RENDERER\r\n" +
            "ST: $MEDIA_RENDERER\r\n" +
            "Date: Mon, 05 Oct 2026 10:00:01 GMT\r\n" +
            (if (name != null) "DLNADeviceName.lge.com: ${ssdpEncode(name)}\r\n" else "") +
            "\r\n"

    private fun acceptForever(socket: ServerSocket) {
        while (!socket.isClosed) {
            try {
                socket.accept().close()
            } catch (_: SocketException) {
                return
            }
        }
    }

    companion object {
        const val SYSTEM_INFO = "ssap://system/getSystemInfo"
        const val SET_PIN = "ssap://pairing/setPin"
        const val GET_VOLUME = "ssap://audio/getVolume"
        const val SECOND_SCREEN = "urn:lge-com:service:webos-second-screen:1"
        const val MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"

        /** URL-encodes [text] the way the TV writes `DLNADeviceName.lge.com`: `[LG] TV` is `%5bLG%5d%20TV`. */
        fun ssdpEncode(text: String): String = text.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
            val c = byte.toInt().toChar()
            if (byte >= 0 && (c.isLetterOrDigit() || c in "-._~")) c.toString() else "%%%02x".format(byte.toInt() and 0xFF)
        }

        fun newCertificate(): HeldCertificate = HeldCertificate.Builder()
            .commonName("LG webOS TV ${UUID.randomUUID()}")
            .addSubjectAlternativeName("localhost")
            .build()

        private fun reservePort(): Int = ServerSocket(0).use { it.localPort }

        fun macBytes(mac: String): ByteArray = mac.split(':', '-').map { it.toInt(16).toByte() }.toByteArray()

        fun isMagicPacketFor(packet: ByteArray, mac: ByteArray): Boolean =
            packet.size == 102 &&
                (0 until 6).all { packet[it] == 0xFF.toByte() } &&
                (0 until 16).all { rep -> (0 until 6).all { packet[6 + rep * 6 + it] == mac[it] } }

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
    fun int(key: String): Int? = (payload[key] as? JsonPrimitive)?.intOrNull
    operator fun get(key: String): JsonElement? = payload[key]
}

/** Thrown by a handler to answer with an SSAP error. */
class TvError(message: String) : Exception(message)

enum class PromptAnswer { Accept, Decline, Wait }

enum class PinReply {
    /** A `response` to the setPin request, then `registered` for the register message. */
    ResponseThenRegistered,

    /** Only `registered` for the register message. */
    RegisteredOnly,

    /** `registered` as the reply to the setPin request itself. */
    RegisteredAsSetPinReply,
}

enum class KeyPolicy {
    /** The stored key registers straight away. */
    Accept,

    /** The TV refuses the stored key with an error, as after some firmware updates. */
    RejectWithError,

    /** The TV ignores the stored key and shows the pairing prompt again. */
    Prompt,
}

object ScreenOffUris {
    const val PANEL = "ssap://com.webos.service.panelcontroller/setScreenOnOff"
    const val TVPOWER_OFF = "ssap://com.webos.service.tvpower/power/turnOffScreen"
    const val TVPOWER_ON = "ssap://com.webos.service.tvpower/power/turnOnScreen"
    const val WEBOS4_OFF = "ssap://com.webos.service.tv.power/turnOffScreen"
    const val WEBOS4_ON = "ssap://com.webos.service.tv.power/turnOnScreen"
    val all: Set<String> = setOf(PANEL, TVPOWER_OFF, TVPOWER_ON, WEBOS4_OFF, WEBOS4_ON)
}
