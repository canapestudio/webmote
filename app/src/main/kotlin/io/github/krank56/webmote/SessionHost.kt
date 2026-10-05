package io.github.krank56.webmote

import android.content.Context
import android.net.wifi.WifiManager
import io.github.krank56.webmote.core.Discovery
import io.github.krank56.webmote.core.TvCandidate
import io.github.krank56.webmote.core.TvRegistry
import io.github.krank56.webmote.core.TvSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/** Holds the app's single [TvSession] for the active TV, so there's never a second connection. */
class SessionHost(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val registry = TvRegistry(File(context.filesDir, "tvs"))
    val session = TvSession(registry, scope)
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
}
