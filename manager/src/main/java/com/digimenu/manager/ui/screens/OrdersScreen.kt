package com.digimenu.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digimenu.core.model.Order
import com.digimenu.core.model.OrderStatus
import com.digimenu.manager.ui.theme.DangerRed
import com.digimenu.manager.ui.theme.MaroonPrimary
import com.digimenu.manager.ui.theme.TextSecondary
import com.digimenu.manager.ui.viewmodel.OrdersViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OrdersScreen(viewModel: OrdersViewModel = hiltViewModel()) {
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    var rejectOrder by remember { mutableStateOf<Order?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    val incoming = orders.filter { it.status == Order.STATUS_NEW }
    val active = orders.filter { it.status in OrderStatus.ACTIVE_FOR_MANAGER && it.status != Order.STATUS_NEW }
    val finished = orders.filter { it.status !in OrderStatus.ACTIVE_FOR_MANAGER }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Incoming (${incoming.size})", style = MaterialTheme.typography.titleMedium) }
        if (incoming.isEmpty()) {
            item {
                Text(
                    text = "No new orders yet. They appear here live.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
        items(incoming, key = { it.id }) { order ->
            OrderCard(order, viewModel, onReject = { rejectOrder = it })
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("Active (${active.size})", style = MaterialTheme.typography.titleMedium)
        }
        if (active.isEmpty()) {
            item {
                Text(
                    text = "Accepted orders move here while they are prepared.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
        items(active, key = { it.id }) { order ->
            OrderCard(order, viewModel, onReject = { rejectOrder = it })
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("Finished (${finished.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(finished, key = { it.id }) { order ->
            OrderCard(order, viewModel, showActions = false, onReject = {})
        }
    }

    rejectOrder?.let { order ->
        AlertDialog(
            onDismissRequest = {
                rejectOrder = null
                rejectReason = ""
            },
            title = { Text("Reject order?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The customer will see the reason on their order page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        label = { Text("Reason (required)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateStatus(order, Order.STATUS_REJECTED, rejectReason.trim())
                        rejectOrder = null
                        rejectReason = ""
                    },
                    enabled = rejectReason.isNotBlank(),
                ) {
                    Text("Reject", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    rejectOrder = null
                    rejectReason = ""
                }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun OrderCard(
    order: Order,
    viewModel: OrdersViewModel,
    onReject: (Order) -> Unit,
    showActions: Boolean = true,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (order.orderType == Order.ORDER_TYPE_TAKEAWAY) {
                        Order.TAKEAWAY_TABLE_LABEL
                    } else {
                        "Table: ${order.tableLabel}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = OrderStatus.label(order.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = when (order.status) {
                        Order.STATUS_NEW -> MaterialTheme.colorScheme.primary
                        Order.STATUS_ACCEPTED, Order.STATUS_PREPARING -> MaterialTheme.colorScheme.tertiary
                        Order.STATUS_READY -> MaroonPrimary
                        Order.STATUS_REJECTED -> DangerRed
                        else -> TextSecondary
                    },
                )
            }
            Text(
                text = "${order.customerName}  •  ${order.customerPhone}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (order.orderType == Order.ORDER_TYPE_TAKEAWAY && order.address.isNotBlank()) {
                Text(
                    text = "Deliver to: ${order.address}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            order.items.values.forEach { line ->
                Text(
                    text = "${line.qty} × ${line.name}  —  Rs. ${"%.2f".format(line.price * line.qty)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "Total: Rs. ${"%.2f".format(order.total)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (order.status == Order.STATUS_REJECTED && order.declineReason.isNotBlank()) {
                Text(
                    text = "Reason: ${order.declineReason}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DangerRed,
                )
            }
            if (order.statusChangedAt > 0L) {
                Text(
                    text = "Updated ${formatTime(order.statusChangedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }

            if (showActions) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (order.status) {
                        Order.STATUS_NEW -> {
                            Button(
                                onClick = { viewModel.updateStatus(order, Order.STATUS_ACCEPTED) },
                            ) { Text("Accept") }
                            OutlinedButton(onClick = { onReject(order) }) { Text("Reject") }
                        }

                        Order.STATUS_ACCEPTED -> {
                            Button(
                                onClick = { viewModel.updateStatus(order, Order.STATUS_PREPARING) },
                            ) { Text("Start preparing") }
                            OutlinedButton(
                                onClick = { viewModel.updateStatus(order, Order.STATUS_CANCELLED) },
                            ) { Text("Cancel") }
                        }

                        Order.STATUS_PREPARING -> {
                            Button(
                                onClick = { viewModel.updateStatus(order, Order.STATUS_READY) },
                            ) { Text("Ready") }
                            OutlinedButton(
                                onClick = { viewModel.updateStatus(order, Order.STATUS_CANCELLED) },
                            ) { Text("Cancel") }
                        }

                        Order.STATUS_READY -> {
                            Button(
                                onClick = { viewModel.updateStatus(order, Order.STATUS_DONE) },
                            ) { Text("Mark done") }
                            OutlinedButton(
                                onClick = { viewModel.updateStatus(order, Order.STATUS_CANCELLED) },
                            ) { Text("Cancel") }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
