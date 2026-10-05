package io.github.krank56.webmote.tile

import android.service.quicksettings.Tile
import io.github.krank56.webmote.R
import io.github.krank56.webmote.service.TvSnapshot
import io.github.krank56.webmote.service.label
import io.github.krank56.webmote.service.togglePower

/** Shows whether the TV is on; turns it off, or wakes it with Wake-on-LAN. */
class PowerTileService : TvTileService() {
    override fun render(tile: Tile, tv: TvSnapshot) {
        tile.state = if (tv.connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_power)
        tile.setSubtitleCompat(
            when {
                tv.tvName == null -> getString(R.string.system_no_tv)
                tv.connected -> getString(R.string.system_state_on)
                else -> getString(tv.connection.label)
            },
        )
    }

    override fun onClick() {
        super.onClick()
        if (!host.togglePower()) openApp()
    }
}
