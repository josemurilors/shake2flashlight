package com.shake2flashlight

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick-settings tile that turns the chop-chop service on and off. */
class ChopTileService : TileService() {

    override fun onStartListening() = render(ChopService.running)

    override fun onClick() {
        val on = !ChopService.running
        ChopService.setEnabled(this, on)
        render(on)
    }

    private fun render(on: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
