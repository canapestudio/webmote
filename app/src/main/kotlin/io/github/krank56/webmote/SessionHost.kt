package io.github.krank56.webmote

import android.content.Context
import android.net.wifi.WifiManager
import io.github.krank56.webmote.core.ConnectionState
import io.github.krank56.webmote.core.Discovery
import io.github.krank56.webmote.core.TvCandidate
import io.github.krank56.webmote.core.TvRegistry
import io.github.krank56.webmote.core.TvSession
import io.github.krank56.webmote.service.KeepAliveService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * Holds the app's single [TvSession] for the active TV. The UI, the keep-alive service, tiles,
 * widgets, the notification and the volume keys all share it, so there's never a second connection.
 */
class SessionHost(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val registry = TvRegistry(File(context.filesDir, "tvs"))
    val session = TvSession(registry, scope)
    val settings = AppSettings(context)
    private val discovery = Discovery()

    /** Searches the Wi-Fi network for TVs, holding a multicast lock while it does. */
    fun discover(): Flow<TvCandidate> = flow {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("webmote-discovery")?.apply { setReferenceCounted(false) }
        lock?.acquire()
        try {
            emitAll(discovery.search())
        } finally {
            lock?.release()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Call on each user interaction from the app, a tile or a widget: starts the keep-alive service,
     * which holds the connection to the active TV while it's on. Those interactions are exempt from
     * Android's background limits on starting foreground services.
     */
    fun onUserInteraction() = KeepAliveService.start(context)

    /**
     * Runs [action] on the session for a tile, widget or notification tap: starts the keep-alive
     * service, connects if needed, and runs [action] once connected. Gives up if the TV doesn't
     * connect in time (it's off, or needs pairing in the app).
     */
    fun perform(action: (TvSession) -> Unit) {
        onUserInteraction()
        if (session.state.value.connection == ConnectionState.Connected) {
            action(session)
            return
        }
        session.connect()
        scope.launch {
            val connected = withTimeoutOrNull(10.seconds) {
                session.state.first { it.connection == ConnectionState.Connected }
            }
            if (connected != null) action(session)
        }
    }
}
