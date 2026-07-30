package com.example.mobile_app_herp.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mobile_app_herp.BuildConfig
import com.example.mobile_app_herp.data.Herp
import com.example.mobile_app_herp.data.HerpConfig
import com.example.mobile_app_herp.data.UpdateInfo
import com.example.mobile_app_herp.ui.theme.HerpType
import kotlinx.coroutines.launch

/** What the update check currently knows. */
private sealed interface UpdateState {
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Failed(val reason: String) : UpdateState
}

/**
 * App settings. Small on purpose — the only genuinely useful controls on a phone
 * used for one job are "which build am I on" and "is there a newer one".
 *
 * The update state is shown explicitly rather than only appearing as a dialog on
 * launch: someone told "update the app" needs a place to go and check, and
 * "You're up to date" is as useful an answer as the offer itself.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun check() {
        state = UpdateState.Checking
        runCatching { Herp.updater.checkForUpdate() }
            .onSuccess { info ->
                state = if (info == null) UpdateState.UpToDate else UpdateState.Available(info)
            }
            .onFailure {
                state = UpdateState.Failed("Couldn't reach GitHub. Check your connection.")
            }
    }

    LaunchedEffect(Unit) { check() }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            check()
            refreshing = false
        }
    }

    Refreshable(refreshing = refreshing, onRefresh = ::refresh, modifier = modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gutter),
        ) {
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = onBack,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text("← PROFILE", style = HerpType.Action, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(6.dp))
            ScreenHeader(eyebrow = "App", title = "Settings")

            Spacer(Modifier.height(22.dp))

            // ── Version + updates ─────────────────────────────────────────────
            val spine = when (state) {
                is UpdateState.Available -> MaterialTheme.colorScheme.primary
                is UpdateState.Failed -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.outline
            }
            SpineCard(spine = spine) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stamp("This build", MaterialTheme.colorScheme.onSurfaceVariant)
                    FactRow("Version", BuildConfig.VERSION_NAME)
                    FactRow("Build", BuildConfig.VERSION_CODE.toString())
                    // Not labelled "Live / Test": both build types talk to the
                    // same domain, so claiming one is a test server would be
                    // false. What this says is which build you are holding.
                    FactRow(
                        "Build type",
                        if (HerpConfig.isProduction) "Release" else "Debug",
                    )
                    FactRow("Server", HerpConfig.DOMAIN_BASE)

                    when (val s = state) {
                        UpdateState.Checking -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Checking for updates…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        UpdateState.UpToDate -> Text(
                            "You're on the latest release.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        is UpdateState.Failed -> Text(
                            s.reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )

                        is UpdateState.Available -> Column {
                            Text(
                                "Version ${s.info.version} is available.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (s.info.notes.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    s.info.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { Herp.updater.openDownload(s.info) },
                                shape = CardShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("DOWNLOAD UPDATE", style = HerpType.Action) }
                        }
                    }

                    if (state !is UpdateState.Checking) {
                        OutlinedButton(
                            onClick = ::refresh,
                            shape = CardShape,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "CHECK AGAIN",
                                style = HerpType.Action,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Notifications ─────────────────────────────────────────────────
            SpineCard(spine = MaterialTheme.colorScheme.outline) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stamp("Notifications", MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (Herp.pushEnabled) {
                            "This device is registered. You'll be alerted when a request is assigned to you."
                        } else {
                            "Push isn't available on this server, so requests won't alert you."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // Android owns the on/off switch — deep-link rather than
                    // pretend to control it from here.
                    OutlinedButton(
                        onClick = {
                            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            } else {
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(android.net.Uri.parse("package:${context.packageName}"))
                            }
                            runCatching { context.startActivity(intent) }
                        },
                        shape = CardShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "ANDROID NOTIFICATION SETTINGS",
                            style = HerpType.Action,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
