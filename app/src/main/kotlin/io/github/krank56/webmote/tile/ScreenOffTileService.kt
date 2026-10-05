package io.github.krank56.webmote.tile

import android.service.quicksettings.Tile
import io.github.krank56.webmote.R
import io.github.krank56.webmote.service.TvSnapshot
import io.github.krank56.webmote.service.snapshot

/** Turns the TV's picture off while the sound keeps playing. */
class ScreenOffTileService : TvTileService() {
    override fun render(tile: Tile, tv: TvSnapshot) {
        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_screen_off)
        tile.setSubtitleCompat(tv.nameOrPlaceholder())
    }

    override fun onClick() {
        super.onClick()
        if (host.snapshot().needsApp) openApp() else host.perform { it.screenOff() }
    }
}
