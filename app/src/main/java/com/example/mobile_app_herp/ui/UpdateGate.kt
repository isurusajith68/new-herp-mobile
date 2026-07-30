package com.example.mobile_app_herp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mobile_app_herp.data.Herp
import com.example.mobile_app_herp.data.UpdateInfo
import com.example.mobile_app_herp.ui.theme.HerpType

/**
 * Checks GitHub Releases on launch. If a newer build exists, offers it and — on
 * accept — opens the APK download in a browser, which installs it. The app never
 * installs APKs itself, so it stays off Play Protect's radar.
 *
 * Best-effort and never blocking: a failed check, no network, or an unreachable
 * GitHub all leave the app exactly as it was. Someone mid-shift must never be
 * stopped by an update they did not ask for.
 */
@Composable
fun UpdateGate() {
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        info = runCatching { Herp.updater.checkForUpdate() }.getOrNull()
    }

    val update = info ?: return
    if (dismissed) return

    AlertDialog(
        onDismissRequest = { dismissed = true },
        shape = CardShape,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = {
            Column {
                Text(
                    "UPDATE AVAILABLE",
                    style = HerpType.Eyebrow,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Version ${update.version}",
                    style = HerpType.Title,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                Text(
                    "Downloading opens your browser, which installs the update.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (update.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    BrassRule(24)
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            update.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Herp.updater.openDownload(update)
                dismissed = true
            }) {
                Text("UPDATE", style = HerpType.Action, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = { dismissed = true }) {
                Text(
                    "LATER",
                    style = HerpType.Action,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
