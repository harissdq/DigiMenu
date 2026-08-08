package com.digimenu.manager.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digimenu.core.model.TableSeat
import com.digimenu.core.qr.QrCodeGenerator
import com.digimenu.manager.ui.theme.TextSecondary
import com.digimenu.manager.ui.viewmodel.QrViewModel

@Composable
fun QrCodesScreen(viewModel: QrViewModel = hiltViewModel()) {
    val tables by viewModel.tables.collectAsStateWithLifecycle()
    val label by viewModel.label.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val restaurantId by viewModel.restaurantId.collectAsStateWithLifecycle()
    var showQrFor by remember { mutableStateOf<TableSeat?>(null) }
    var showTakeaway by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Generate a QR code per physical table. Customers scan it to open " +
                "this table's menu and place an order. The public Take Away QR lets " +
                "customers order from home — they add name, phone and a delivery address.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )

        Card(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Public Take Away", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "One QR for the whole restaurant — customers order from anywhere.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Button(onClick = { showTakeaway = true }, enabled = restaurantId != null) {
                    Text("Show QR")
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = viewModel::onLabelChange,
                label = { Text("Table label, e.g. Table_1") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::addTable) { Text("Add + QR") }
        }

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tables.isEmpty()) {
                item {
                    Text(
                        text = "No tables yet. Add one above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
            items(tables, key = { it.id }) { table ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(table.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { showQrFor = table }) { Text("Show QR") }
                }
            }
        }
    }

    showQrFor?.let { table ->
        val content = viewModel.qrContent(table)
        if (content != null) {
            QrDialog(title = "QR for ${table.label}", content = content, onDismiss = { showQrFor = null })
        }
    }

    if (showTakeaway) {
        val content = viewModel.takeawayContent()
        if (content != null) {
            QrDialog(
                title = "Public Take Away QR",
                content = content,
                caption = "Customers scan this from anywhere and order for delivery.",
                onDismiss = { showTakeaway = false },
            )
        }
    }
}

@Composable
private fun QrDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit,
    caption: String? = null,
) {
    val bitmap = remember(content) { QrCodeGenerator.generate(content, 640) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier.size(280.dp),
                )
                Spacer(Modifier.height(8.dp))
                if (caption != null) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
