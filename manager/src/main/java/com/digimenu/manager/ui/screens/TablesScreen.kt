package com.digimenu.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import com.digimenu.core.model.Session
import com.digimenu.manager.ui.theme.DangerRed
import com.digimenu.manager.ui.theme.MaroonPrimary
import com.digimenu.manager.ui.theme.TextSecondary
import com.digimenu.manager.ui.viewmodel.TablesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TablesScreen(viewModel: TablesViewModel = hiltViewModel()) {
    val tables by viewModel.tables.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Live table occupancy. A session opens automatically when the " +
                    "first order arrives; close it when the guests pay.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        item { Text("Tables (${tables.size})", style = MaterialTheme.typography.titleMedium) }
        if (tables.isEmpty()) {
            item {
                Text(
                    text = "No tables yet. Add them under QR Codes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }
        items(tables, key = { it.table.id }) { state ->
            TableCard(state, viewModel)
        }
    }
}

@Composable
private fun TableCard(state: TablesViewModel.TableState, viewModel: TablesViewModel) {
    val session = state.activeSession
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
                Text(state.table.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when {
                        state.isPaid -> "Paid"
                        state.isOpen -> "Seated"
                        session != null -> "Bill"
                        else -> "Free"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        state.isPaid -> TextSecondary
                        state.isOpen -> MaroonPrimary
                        session != null -> DangerRed
                        else -> TextSecondary
                    },
                )
            }

            if (session == null) {
                Text(
                    text = "Free — no active session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            } else {
                Text(
                    text = if (state.isOpen) {
                        "Seated since ${formatTime(session.openedAt)}"
                    } else {
                        "Closed at ${formatTime(session.closedAt)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Text(
                    text = "Orders: ${session.orders.size}  •  Bill: Rs. ${"%.2f".format(state.billTotal)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                state.sessionOrders.forEach { order ->
                    Text(
                        text = if (order.orderType == Order.ORDER_TYPE_TAKEAWAY) {
                            "${order.customerName} (take away) — Rs. ${"%.2f".format(order.total)}"
                        } else {
                            "${order.customerName} — ${order.items.values.sumOf { it.qty }} item(s) — Rs. ${"%.2f".format(order.total)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            when {
                state.isOpen -> Button(
                    onClick = { viewModel.closeTable(state) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Close & bill") }

                session != null && !state.isPaid -> Button(
                    onClick = { viewModel.markPaid(state) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Mark paid") }

                state.isPaid -> Text(
                    text = "Bill settled. " +
                        "A new session opens automatically with the next order.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    if (millis <= 0L) "—" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
