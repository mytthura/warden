package dev.warden.settings

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.warden.design.ButtonKind
import dev.warden.design.Warden
import dev.warden.design.WardenButton
import dev.warden.design.R as DesignR

/** Offers the Quick Settings tile: Android's own prompt on 13+, directions on older versions. */
@Composable
internal fun AddTileRow() {
    val c = Warden.colors
    val context = LocalContext.current
    var showHow by rememberSaveable { mutableStateOf(false) }
    LinkRow(DesignR.drawable.ph_shield_check, stringResource(R.string.settings_tile)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestTile(context) else showHow = true
    }
    if (showHow) {
        AlertDialog(
            onDismissRequest = { showHow = false },
            title = { Text(stringResource(R.string.settings_tile), style = Warden.type.title) },
            text = { Text(stringResource(R.string.settings_tile_how), style = Warden.type.body) },
            confirmButton = { WardenButton(stringResource(R.string.settings_tile_ok), { showHow = false }, kind = ButtonKind.Text) },
            containerColor = c.surface,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestTile(context: Context) {
    context.getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(context, TILE_SERVICE),
        context.getString(R.string.settings_tile_name),
        Icon.createWithResource(context, DesignR.drawable.ph_shield_check),
        context.mainExecutor,
    ) { result ->
        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
            Toast.makeText(context, R.string.settings_tile_already, Toast.LENGTH_SHORT).show()
        }
    }
}

// Named here so this module doesn't need the engine just for one class name.
private const val TILE_SERVICE = "dev.warden.engine.ProtectionTileService"
