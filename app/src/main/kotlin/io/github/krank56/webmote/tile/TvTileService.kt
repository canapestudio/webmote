package io.github.krank56.webmote.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.krank56.webmote.MainActivity
import io.github.krank56.webmote.R
import io.github.krank56.webmote.SessionHost
import io.github.krank56.webmote.service.SystemSurfaces
import io.github.krank56.webmote.service.TvSnapshot
import io.github.krank56.webmote.service.snapshots
import io.github.krank56.webmote.sessionHost
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A Quick Settings tile acting on the active TV through the session host.
 *
 * Tiles are declared active (`ACTIVE_TILE`): Android binds them only for taps and when [Tiles.refresh]
 * asks, which [SystemSurfaces] does whenever what they show changes.
 */
abstract class TvTileService : TileService() {
    private val scope = MainScope()
    private var listening: Job? = null

    protected val host: SessionHost get() = sessionHost

    override fun onCreate() {
        super.onCreate()
        SystemSurfaces.install(this)
    }

    /** An active tile isn't bound on its own when the user adds it: ask to draw it straight away. */
    override fun onTileAdded() {
        super.onTileAdded()
        Tiles.refresh(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch {
            host.snapshots().collect { tv ->
                val tile = qsTile ?: return@collect
                render(tile, tv)
                tile.updateTile()
            }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Shows [tv] on [tile]; [Tile.updateTile] follows. */
    internal abstract fun render(tile: Tile, tv: TvSnapshot)

    internal fun Tile.setSubtitleCompat(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = text
    }

    internal fun TvSnapshot.nameOrPlaceholder(): String = tvName ?: getString(R.string.system_no_tv)

    /** Opens the remote, collapsing the Quick Settings panel. */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    protected fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

/** Asks Android to refresh the tiles; it binds each one (it's active) and it redraws from the session. */
internal object Tiles {
    private val all = listOf(
        PowerTileService::class.java,
        MuteTileService::class.java,
        ScreenOffTileService::class.java,
        OpenRemoteTileService::class.java,
    )

    fun refresh(context: Context) {
        for (tile in all) {
            try {
                TileService.requestListeningState(context, ComponentName(context, tile))
            } catch (e: RuntimeException) {
                // Quick Settings isn't available (e.g. no SystemUI for this user); the tile redraws when shown.
            }
        }
    }
}
