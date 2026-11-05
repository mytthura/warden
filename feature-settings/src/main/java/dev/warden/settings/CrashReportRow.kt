package dev.warden.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.warden.data.CrashLog
import dev.warden.design.ButtonKind
import dev.warden.design.IconBadge
import dev.warden.design.Spacing
import dev.warden.design.Warden
import dev.warden.design.WardenButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.warden.design.R as DesignR

/**
 * Appears only after a crash. Shows the saved report so people can read it before sharing, then copy it
 * into a bug report or delete it. Warden never sends it anywhere.
 */
@Composable
internal fun CrashReportRow() {
    val c = Warden.colors
    val context = LocalContext.current
    var deleted by remember { mutableStateOf(false) }
    val report by produceState<String?>(null) { value = withContext(Dispatchers.IO) { CrashLog.report(context) } }
    val text = report?.takeUnless { deleted } ?: return
    var open by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    HorizontalDivider(color = c.outline)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { open = true },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        IconBadge(DesignR.drawable.ph_pulse, c.warning)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.crash_row), style = Warden.type.body)
            Text(stringResource(R.string.crash_row_body), style = Warden.type.label, color = c.muted)
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.crash_row), style = Warden.type.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(stringResource(R.string.crash_note), style = Warden.type.label, color = c.muted)
                    Text(text, Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()), style = Warden.type.caption)
                }
            },
            confirmButton = {
                WardenButton(stringResource(if (copied) R.string.crash_copied else R.string.crash_copy), {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Warden crash report", text))
                    copied = true
                })
            },
            dismissButton = {
                WardenButton(stringResource(R.string.crash_delete), {
                    CrashLog.clear(context)
                    deleted = true
                    open = false
                }, kind = ButtonKind.Text)
            },
            containerColor = c.surface,
        )
    }
}
