package io.github.krank56.webmote.service

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.krank56.webmote.sessionHost
import io.github.krank56.webmote.tile.Tiles
import io.github.krank56.webmote.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * Keeps the tiles and widgets in step with the session while the process lives, whether or not the
 * keep-alive service runs. It also starts the service when the TV connects while the app is in the
 * foreground (e.g. once the "TV is off" screen's polling finds it), since no interaction did then.
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
            // What the surfaces show now is drawn by them; follow the changes from here.
            host.snapshots().drop(1).collectLatest { tv ->
                delay(SETTLE)
                Tiles.refresh(app)
                Widgets.refresh(app)
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
