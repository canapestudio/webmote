package io.github.krank56.webmote.tile

import android.service.quicksettings.Tile
import io.github.krank56.webmote.R
import io.github.krank56.webmote.service.TvSnapshot

/** Opens the remote. */
class OpenRemoteTileService : TvTileService() {
    override fun render(tile: Tile, tv: TvSnapshot) {
        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_remote)
        tile.setSubtitleCompat(tv.nameOrPlaceholder())
    }

    override fun onClick() {
        super.onClick()
        openApp()
    }
}
