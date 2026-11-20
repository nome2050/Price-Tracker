package com.pricetracker.app.a11y

import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class TrackTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply { state = if (PriceAccessibilityService.instance != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; updateTile() }
    }

    override fun onClick() {
        val s = PriceAccessibilityService.instance
        if (s != null) s.scanLater(700)
        else startActivityAndCollapseCompat(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseCompat(i: Intent) {
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(android.app.PendingIntent.getActivity(this, 0, i, android.app.PendingIntent.FLAG_IMMUTABLE))
        } else startActivityAndCollapse(i)
    }
}
