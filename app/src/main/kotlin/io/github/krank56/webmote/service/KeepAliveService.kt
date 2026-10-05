package io.github.krank56.webmote.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.krank56.webmote.SessionHost
import io.github.krank56.webmote.core.ConnectionState
import io.github.krank56.webmote.sessionHost
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The foreground service that holds the connection to the active TV while it's on.
 *
 * It owns nothing itself: the single TV session lives in [SessionHost.session], shared with the app,
 * tiles and widgets. The service keeps the process in the foreground while that session is live,
 * shows the notification, holds the volume-keys MediaSession, and tells the session when the network
 * changes. It stops once the session settles in a state that isn't live: the TV turned off, the phone
 * left its network, or pairing needs the user in the app.
 */
class KeepAliveService : LifecycleService() {
    private val host: SessionHost by lazy { sessionHost }
    private var started = false

    /** What the notification shows now. */
    private var posted: TvSnapshot? = null
    private var stopWatch: Job? = null

    override fun onCreate() {
        super.onCreate()
        SystemSurfaces.install(this)
        KeepAliveNotification.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!enterForeground()) {
            stop()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            // A session nobody has connected yet (e.g. the process just started for a tile tap).
            if (host.session.state.value.connection == ConnectionState.Disconnected) host.session.connect()
            follow()
        }
        watchForStop()
        return START_NOT_STICKY
    }

    private fun enterForeground(): Boolean {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        val tv = host.snapshot()
        return try {
            ServiceCompat.startForeground(this, KeepAliveNotification.ID, KeepAliveNotification.build(this, tv), type)
            posted = tv
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+): started from the background without an exemption.
            Log.w(TAG, "Couldn't enter the foreground", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Couldn't enter the foreground", e)
            false
        }
    }

    /** Starts what runs for the service's whole life: the notification, the volume keys and the network watch. */
    private fun follow() {
        lifecycleScope.launch {
            host.snapshots().collect { tv ->
                if (tv == posted) return@collect
                posted = tv
                KeepAliveNotification.update(this@KeepAliveService, tv)
            }
        }
        lifecycleScope.launch { VolumeKeys(this@KeepAliveService, host).run() }
        lifecycleScope.launch {
            defaultNetworkChanges(this@KeepAliveService).collectLatest {
                delay(NETWORK_SETTLE)
                host.session.onNetworkChanged()
            }
        }
    }

    /**
     * Stops the service once the session has stayed out of a live state for a moment. Right after a
     * start it may not have begun connecting yet, so it gets a longer grace period until it has been live.
     */
    private fun watchForStop() {
        stopWatch?.cancel()
        val startedAt = SystemClock.elapsedRealtime()
        stopWatch = lifecycleScope.launch {
            var wasLive = false
            host.session.state.map { it.connection.isLive }.distinctUntilChanged().collectLatest { live ->
                if (live) {
                    wasLive = true
                    return@collectLatest
                }
                val graceLeft = if (wasLive) 0L else START_GRACE.inWholeMilliseconds - (SystemClock.elapsedRealtime() - startedAt)
                delay(maxOf(SETTLE.inWholeMilliseconds, graceLeft))
                stop()
            }
        }
    }

    private fun stop() {
        running = false
        stopWatch?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Android 15+ ends foreground services that run past their type's time limit. `connectedDevice` has none today. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service timed out")
        stop()
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Webmote"
        private val START_GRACE = 5.seconds
        private val SETTLE = 2.seconds
        private val NETWORK_SETTLE = 500.milliseconds

        /** Whether the service has been started and hasn't stopped since: further starts are no-ops. */
        @Volatile
        private var running = false

        /**
         * Starts the service, from a user interaction (the app, a tile, a widget or the notification):
         * those are exempt from Android's background limits on starting foreground services. Does nothing
         * when it's already running, or when there's nothing to connect to: no TV yet, or a pairing problem
         * only the app can resolve.
         */
        fun start(context: Context) {
            val app = context.applicationContext
            SystemSurfaces.install(app)
            if (running) return
            val host = app.sessionHost
            val connection = host.session.state.value.connection
            val wanted = connection.isLive || (host.registry.activeTv != null && !connection.needsApp)
            if (!wanted) return
            try {
                ContextCompat.startForegroundService(app, Intent(app, KeepAliveService::class.java))
                running = true
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (Android 12+): not from an exempt interaction.
                Log.w(TAG, "Couldn't start the keep-alive service", e)
            }
        }
    }
}
