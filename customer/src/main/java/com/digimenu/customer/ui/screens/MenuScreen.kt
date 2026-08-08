package com.digimenu.customer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.digimenu.core.model.CartItem
import com.digimenu.core.model.MenuItem
import com.digimenu.customer.ui.theme.TextSecondary

/** Live menu grouped by category with add-to-cart controls and a checkout bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    menu: List<MenuItem>,
    cart: List<CartItem>,
    placing: Boolean,
    message: String?,
    onAdd: (MenuItem) -> Unit,
    onRemove: (String) -> Unit,
    onPlaceOrder: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Menu") }) },
        bottomBar = {
            if (cart.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "${cart.sumOf { it.qty }} items",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "Rs. ${"%.2f".format(cart.sumOf { it.lineTotal })}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Button(onClick = onPlaceOrder, enabled = !placing) {
                            Text(if (placing) "Placing order..." else "Place order")
                        }
                    }
                }
            }
        },
    ) { padding ->
        val available = menu.filter { it.available }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            message?.let {
                item(key = "message") {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (available.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "The menu is empty right now. Please wait a moment.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            available.groupBy { it.category }.forEach { (category, categoryItems) ->
                item(key = "category-$category") {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                categoryItems.forEach { item ->
                    item(key = item.id) {
                        CustomerMenuItemCard(
                            item = item,
                            qty = cart.firstOrNull { it.item.id == item.id }?.qty ?: 0,
                            onAdd = { onAdd(item) },
                            onRemove = { onRemove(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerMenuItemCard(
    item: MenuItem,
    qty: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Text(
                    text = "Rs. ${"%.2f".format(item.price)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (qty == 0) {
                OutlinedButton(onClick = onAdd) { Text("Add") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Remove, contentDescription = "Remove one")
                    }
                    Text("$qty", style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Filled.Add, contentDescription = "Add one")
                    }
                }
            }
        }
    }
}
