package io.github.krank56.webmote.service

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.krank56.webmote.sessionHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * Follows the session while the process lives, whether or not the keep-alive service runs: it starts
 * the service when the TV connects while the app is in the foreground (e.g. once the "TV is off"
 * screen's polling finds it), since no interaction did then.
 */
internal object SystemSurfaces {
    private val installed = AtomicBoolean(false)
    private val SETTLE = 300.milliseconds

    /** Starts following the session. Cheap and idempotent: call it from any surface. */
    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val app = context.applicationContext
        val host = app.sessionHost
        host.scope.launch {
            // Follow the changes from here.
            host.snapshots().drop(1).collectLatest { tv ->
                delay(SETTLE)
                if (tv.connected) {
                    withContext(Dispatchers.Main) {
                        val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                        if (foreground) KeepAliveService.start(app)
                    }
                }
            }
        }
    }
}
