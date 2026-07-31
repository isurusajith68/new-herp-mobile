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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mobile_app_herp.data.Property
import com.example.mobile_app_herp.ui.theme.HerpType

/**
 * What Inventory can do on a phone. Two things, both of them jobs you do while
 * standing somewhere the desktop isn't: raising a request, and photographing a
 * bill as the delivery arrives.
 */
@Composable
fun InventoryScreen(
    property: Property,
    onBack: () -> Unit,
    onRequests: () -> Unit,
    onUploadBill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = Gutter)) {
        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = onBack,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text("← MODULES", style = HerpType.Action, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
        ScreenHeader(eyebrow = property.name, title = "Inventory")

        Spacer(Modifier.height(22.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuRow(
                title = "Requests",
                detail = "Raise work for the store keeper, or see what is open",
                onClick = onRequests,
            )
            MenuRow(
                title = "Upload a bill",
                detail = "Photograph a supplier bill and have it read for you",
                onClick = onUploadBill,
            )
        }
    }
}

@Composable
private fun MenuRow(title: String, detail: String, onClick: () -> Unit) {
    SpineCard(spine = MaterialTheme.colorScheme.primary, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(title, style = HerpType.Title, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(3.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text("→", style = HerpType.Title, color = MaterialTheme.colorScheme.primary)
        }
    }
}
