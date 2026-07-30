package com.example.mobile_app_herp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import com.example.mobile_app_herp.data.CurrentUser
import com.example.mobile_app_herp.data.Herp
import com.example.mobile_app_herp.data.HerpConfig
import com.example.mobile_app_herp.data.Workspace
import com.example.mobile_app_herp.ui.theme.HerpType
import kotlinx.coroutines.launch

/**
 * Who you are signed in as. Deliberately thin: the useful facts are the account
 * and the workspace it belongs to, because "am I on the right workspace?" is the
 * question staff actually ask when something looks wrong.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<CurrentUser?>(null) }
    var workspace by remember { mutableStateOf<Workspace?>(null) }
    var propertyCount by remember { mutableStateOf<Int?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        error = null
        runCatching {
            Triple(
                Herp.client.me(),
                runCatching { Herp.client.workspace() }.getOrNull(),
                runCatching { Herp.client.properties().size }.getOrNull(),
            )
        }.onSuccess { (me, ws, count) ->
            user = me
            workspace = ws
            propertyCount = count
        }.onFailure { error = it.message ?: "Couldn't load your account" }
    }

    LaunchedEffect(Unit) { load() }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            load()
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
                Text("← PROPERTIES", style = HerpType.Action, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(6.dp))
            ScreenHeader(eyebrow = "Account", title = user?.fullName ?: "Profile")

            Spacer(Modifier.height(22.dp))

            if (error != null) {
                Notice(error!!, label = "Didn't load")
                Spacer(Modifier.height(16.dp))
            }

            SpineCard(spine = MaterialTheme.colorScheme.primary) {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KeyTag(user?.fullName ?: "?", size = 42)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                user?.fullName ?: "—",
                                style = HerpType.Title,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                user?.email ?: Herp.prefs.email ?: "—",
                                style = HerpType.Record,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            SpineCard(spine = MaterialTheme.colorScheme.outline) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stamp("Workspace", MaterialTheme.colorScheme.onSurfaceVariant)
                    FactRow("Name", workspace?.name ?: Herp.prefs.slug ?: "—")
                    FactRow(
                        "Address",
                        "${Herp.prefs.slug.orEmpty()}-app.${HerpConfig.DOMAIN_BASE}",
                    )
                    FactRow("Properties", propertyCount?.toString() ?: "—")
                    FactRow(
                        "Notifications",
                        if (Herp.pushEnabled) "On for this device" else "Not available",
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            OutlinedButton(
                onClick = onSettings,
                shape = CardShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("SETTINGS", style = HerpType.Action, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onSignOut,
                shape = CardShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("SIGN OUT", style = HerpType.Action, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

/** Label on the left in stamped caps, value on the right in mono. */
@Composable
fun FactRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Stamp(label, MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = HerpType.Record,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
