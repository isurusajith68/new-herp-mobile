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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mobile_app_herp.data.Herp
import com.example.mobile_app_herp.data.HerpHttpException
import com.example.mobile_app_herp.data.Property
import com.example.mobile_app_herp.data.Workspace
import com.example.mobile_app_herp.ui.theme.HerpType

@Composable
fun PropertyPickerScreen(
    onPick: (Property) -> Unit,
    onSessionLost: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var workspace by remember { mutableStateOf<Workspace?>(null) }
    var userEmail by remember { mutableStateOf<String?>(null) }
    var properties by remember { mutableStateOf<List<Property>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        error = null
        runCatching {
            val props = Herp.client.properties()
            val ws = runCatching { Herp.client.workspace() }.getOrNull()
            val me = runCatching { Herp.client.me() }.getOrNull()
            if (Herp.moduleTitles.isEmpty()) {
                Herp.moduleTitles =
                    runCatching { Herp.client.tenantModules() }.getOrDefault(emptyList())
            }
            Triple(props, ws, me)
        }.onSuccess { (props, ws, me) ->
            properties = props
            workspace = ws
            userEmail = me?.email ?: Herp.prefs.email
        }.onFailure {
            if (it is HerpHttpException && it.status == 401) onSessionLost()
            else error = it.message ?: "Could not load your properties"
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = Gutter)) {
        Spacer(Modifier.height(24.dp))
        ScreenHeader(
            eyebrow = "Workspace",
            title = workspace?.name ?: Herp.prefs.slug.orEmpty(),
            subtitle = userEmail,
            trailing = {
                TextButton(onClick = onSignOut) {
                    Text("SIGN OUT", style = HerpType.Action, color = MaterialTheme.colorScheme.primary)
                }
            },
        )

        Spacer(Modifier.height(22.dp))

        when {
            error != null -> ErrorState(error!!, onRetry = { attempt++ })

            properties == null -> LoadingState("Loading properties")

            properties!!.isEmpty() -> EmptyState(
                title = "No properties yet",
                detail = "Your account has no property access. Ask an administrator to add you, " +
                    "then pull this screen again.",
            )

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(properties!!, key = { it.id }) { property ->
                    PropertyRow(property) { onPick(property) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/**
 * A property reads as a plate on the rack: initials in a brass tag, the name, and
 * a mono line of the facts you'd check before walking in — where it sits in the
 * day, and what you're allowed to do there.
 */
@Composable
private fun PropertyRow(property: Property, onClick: () -> Unit) {
    SpineCard(spine = MaterialTheme.colorScheme.primary, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            KeyTag(property.name, size = 38)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    property.name,
                    style = HerpType.Title,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    listOfNotNull(
                        property.propertyType,
                        property.role,
                        property.timezone.takeIf { it.isNotEmpty() },
                    ).joinToString(" · ").ifEmpty { property.slug },
                    style = HerpType.Record,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    property.modules.size.toString(),
                    style = HerpType.Title,
                    color = MaterialTheme.colorScheme.primary,
                )
                Stamp(
                    if (property.modules.size == 1) "Module" else "Modules",
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
