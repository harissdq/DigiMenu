package com.digimenu.manager.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digimenu.manager.ui.theme.TextSecondary
import com.digimenu.manager.ui.viewmodel.AdminViewModel

@Composable
fun AdminScreen(viewModel: AdminViewModel = hiltViewModel()) {
    val restaurants by viewModel.restaurants.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var tables by remember { mutableStateOf("") }
    var managerEmail by remember { mutableStateOf("") }
    var managerPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Admin — create new restaurants and link managers. " +
                "Only the main admin account can use this.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Add restaurant", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Restaurant name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = tables,
                    onValueChange = { tables = it },
                    label = { Text("Tables (comma-separated, optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = managerEmail,
                    onValueChange = { managerEmail = it },
                    label = { Text("Manager email (optional — you become the manager if blank)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = managerPassword,
                    onValueChange = { managerPassword = it },
                    label = { Text("Manager password (required with a manager email)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = {
                        viewModel.createRestaurant(name, tables, managerEmail, managerPassword)
                    },
                    enabled = !busy && name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Create restaurant")
                    }
                }
            }
        }

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            text = "Existing restaurants (${restaurants.size})",
            style = MaterialTheme.typography.titleSmall,
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (restaurants.isEmpty()) {
                item {
                    Text(
                        text = "None yet. Create the first one above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
            items(restaurants, key = { it.id }) { restaurant ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        text = "${restaurant.name}  (${restaurant.id})",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
