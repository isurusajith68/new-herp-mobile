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
import com.example.mobile_app_herp.data.MODULE_INVENTORY
import com.example.mobile_app_herp.data.Property
import com.example.mobile_app_herp.data.prettifyModuleKey
import com.example.mobile_app_herp.ui.theme.HerpType

private data class ModuleTile(
    val key: String,
    val title: String,
    val category: String,
    val onMobile: Boolean,
)

/**
 * Modules are rows, not square tiles. Their names are words — "Goods Received",
 * "Procurement" — and words want horizontal room; a grid of squares just
 * truncates them. Rows also let the list say the one thing that matters most
 * here: which of these you can actually open on a phone.
 */
@Composable
fun ModulesScreen(
    property: Property,
    onBack: () -> Unit,
    onOpenModule: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tiles by remember { mutableStateOf<List<ModuleTile>?>(null) }

    LaunchedEffect(property.id) {
        if (Herp.moduleTitles.isEmpty()) {
            Herp.moduleTitles =
                runCatching { Herp.client.tenantModules() }.getOrDefault(emptyList())
        }
        val byKey = Herp.moduleTitles.associateBy { it.key }
        tiles = property.modules
            .map { key ->
                val info = byKey[key]
                ModuleTile(
                    key = key,
                    title = info?.title ?: prettifyModuleKey(key),
                    category = info?.category.orEmpty(),
                    onMobile = key == MODULE_INVENTORY,
                )
            }
            // What you can use comes first; the rest stay visible so the list
            // still reflects what this property actually has.
            .sortedWith(compareByDescending<ModuleTile> { it.onMobile }.thenBy { it.title.lowercase() })
    }

    Column(modifier.fillMaxSize().padding(horizontal = Gutter)) {
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("← ALL PROPERTIES", style = HerpType.Action, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
        ScreenHeader(
            eyebrow = property.role ?: "Property",
            title = property.name,
        )

        Spacer(Modifier.height(22.dp))

        when {
            tiles == null -> LoadingState("Loading modules")

            tiles!!.isEmpty() -> EmptyState(
                title = "No modules granted",
                detail = "Nobody has given this account a module at this property. " +
                    "An administrator can grant them from Property Settings.",
            )

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tiles!!, key = { it.key }) { tile ->
                    ModuleRow(tile) { if (tile.onMobile) onOpenModule(tile.key) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ModuleRow(tile: ModuleTile, onClick: () -> Unit) {
    val live = tile.onMobile
    val spine =
        if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val titleColor =
        if (live) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

    SpineCard(spine = spine, onClick = if (live) onClick else null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(tile.title, style = HerpType.Title, color = titleColor)
                if (tile.category.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tile.category,
                        style = HerpType.Record,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            if (live) {
                Text("→", style = HerpType.Title, color = MaterialTheme.colorScheme.primary)
            } else {
                Stamp("Desktop only", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
