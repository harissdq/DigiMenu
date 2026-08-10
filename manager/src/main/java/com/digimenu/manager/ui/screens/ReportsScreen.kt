package com.digimenu.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digimenu.core.report.ReportStats
import com.digimenu.manager.ui.theme.MaroonPrimary
import com.digimenu.manager.ui.theme.TextSecondary
import com.digimenu.manager.ui.viewmodel.ReportsViewModel
import kotlinx.coroutines.launch

@Composable
fun ReportsScreen(viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "Live sales figures for the selected period. Cancelled and " +
                        "rejected orders are counted but excluded from revenue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportsViewModel.Period.entries.forEach { period ->
                        FilterChip(
                            selected = state.period == period,
                            onClick = { viewModel.selectPeriod(period) },
                            label = { Text(period.label) },
                        )
                    }
                }
            }
            item { SummaryCard(state.stats) }
            if (state.stats.byCategory.isNotEmpty()) {
                item { BreakdownCard("Sales by category", state.stats.byCategory) }
            }
            if (state.stats.byItem.isNotEmpty()) {
                item { BreakdownCard("Top items", state.stats.byItem.take(10)) }
            }
            item {
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(viewModel.buildCsv(state)))
                        scope.launch { snackbar.showSnackbar("CSV report copied — paste it into a spreadsheet") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Copy CSV report") }
            }
        }
    }
}

@Composable
private fun SummaryCard(stats: ReportStats) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Metric("Orders", stats.totalOrders.toString())
                Metric("Dine-in", stats.dineInCount.toString())
                Metric("Take-away", stats.takeawayCount.toString())
            }
            Text(
                text = "Revenue  Rs. ${"%.2f".format(stats.revenue)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaroonPrimary,
            )
            Text(
                text = "Average order value: Rs. ${"%.2f".format(stats.avgOrderValue)}  •  " +
                    "Completed: ${stats.completedOrders}  •  " +
                    "Cancelled/rejected: ${stats.cancelledRejectedOrders}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}

@Composable
private fun BreakdownCard(title: String, rows: List<ReportStats.GroupedValue>) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        row.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                    Text(
                        text = "${row.count}  •  Rs. ${"%.2f".format(row.revenue)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}
