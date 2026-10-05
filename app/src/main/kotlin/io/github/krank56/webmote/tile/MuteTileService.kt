package io.github.krank56.webmote.tile

import android.service.quicksettings.Tile
import io.github.krank56.webmote.R
import io.github.krank56.webmote.service.TvSnapshot
import io.github.krank56.webmote.service.label
import io.github.krank56.webmote.service.snapshot
import io.github.krank56.webmote.service.toggleMute

/** Shows whether the TV is muted; toggles it. */
class MuteTileService : TvTileService() {
    override fun render(tile: Tile, tv: TvSnapshot) {
        tile.state = if (tv.connected && tv.muted) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_mute)
        tile.setSubtitleCompat(
            when {
                tv.tvName == null -> getString(R.string.system_no_tv)
                !tv.connected -> getString(tv.connection.label)
                tv.muted -> getString(R.string.system_state_muted)
                else -> getString(R.string.system_state_sound_on)
            },
        )
    }

    override fun onClick() {
        super.onClick()
        if (host.snapshot().needsApp) openApp() else host.toggleMute()
    }
}
