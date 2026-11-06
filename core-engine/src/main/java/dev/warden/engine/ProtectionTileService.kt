package dev.warden.engine

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: protection on or off from the notification shade. On a locked phone it asks for
 * the unlock first, so nobody can switch the firewall off from the lock screen.
 */
class ProtectionTileService : TileService() {
    private var watching: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        watching = CoroutineScope(Dispatchers.Main).launch { Engine.running.collect(::show) }
    }

    override fun onStopListening() {
        watching?.cancel()
        watching = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun(::toggle) else toggle()
    }

    private fun toggle() {
        when {
            Engine.running.value -> WardenVpnService.stop(this)
            // Android's VPN consent needs an activity, so the first start goes through the app.
            VpnService.prepare(this) != null -> openApp()
            // Android can refuse a background start; the app can always start it.
            else -> runCatching { WardenVpnService.start(this) }.onFailure { openApp() }
        }
    }

    private fun show(on: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (on) R.string.engine_tile_on else R.string.engine_tile_off)
        }
        tile.updateTile()
    }

    private fun openApp() {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(launch)
        }
    }
}
