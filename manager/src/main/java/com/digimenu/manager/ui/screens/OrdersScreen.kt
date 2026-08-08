package com.digimenu.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digimenu.core.model.Order
import com.digimenu.manager.ui.theme.TextSecondary
import com.digimenu.manager.ui.viewmodel.OrdersViewModel

@Composable
fun OrdersScreen(viewModel: OrdersViewModel = hiltViewModel()) {
    val orders by viewModel.orders.collectAsStateWithLifecycle()

    val incoming = orders.filter { it.status == Order.STATUS_NEW }
    val preparing = orders.filter { it.status == Order.STATUS_PREPARING }
    val finished = orders.filter {
        it.status == Order.STATUS_DONE || it.status == Order.STATUS_CANCELLED
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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
        items(incoming, key = { it.id }) { OrderCard(it, viewModel) }

        item { Spacer(Modifier.height(8.dp)); Text("Preparing (${preparing.size})", style = MaterialTheme.typography.titleMedium) }
        items(preparing, key = { it.id }) { OrderCard(it, viewModel) }

        item { Spacer(Modifier.height(8.dp)); Text("Finished (${finished.size})", style = MaterialTheme.typography.titleMedium) }
        items(finished, key = { it.id }) { OrderCard(it, viewModel, showActions = false) }
    }
}

@Composable
private fun OrderCard(
    order: Order,
    viewModel: OrdersViewModel,
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
                Text("Table: ${order.tableLabel}", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = order.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (order.status) {
                        Order.STATUS_NEW -> MaterialTheme.colorScheme.primary
                        Order.STATUS_PREPARING -> MaterialTheme.colorScheme.tertiary
                        else -> TextSecondary
                    },
                )
            }
            Text(
                text = "${order.customerName}  •  ${order.customerPhone}",
                style = MaterialTheme.typography.bodyMedium,
            )
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

            if (showActions) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (order.status) {
                        Order.STATUS_NEW -> Button(
                            onClick = { viewModel.updateStatus(order, Order.STATUS_PREPARING) },
                        ) { Text("Start preparing") }

                        Order.STATUS_PREPARING -> Button(
                            onClick = { viewModel.updateStatus(order, Order.STATUS_DONE) },
                        ) { Text("Mark done") }
                    }
                    OutlinedButton(
                        onClick = { viewModel.updateStatus(order, Order.STATUS_CANCELLED) },
                    ) { Text("Cancel") }
                }
            }
        }
    }
}
