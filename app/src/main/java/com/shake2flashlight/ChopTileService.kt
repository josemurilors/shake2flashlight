package com.shake2flashlight

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/** Quick-settings tile that turns the chop-chop service on and off. */
@RequiresApi(Build.VERSION_CODES.N)
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
