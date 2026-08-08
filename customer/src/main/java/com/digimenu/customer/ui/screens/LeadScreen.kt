package com.digimenu.customer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.digimenu.core.model.CustomerLead
import com.digimenu.customer.ui.theme.TextSecondary

/** Captures the customer's details before showing the menu. */
@Composable
fun LeadScreen(
    tableLabel: String,
    lead: CustomerLead,
    message: String?,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Welcome!",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "You scanned table $tableLabel. Enter your details to open the menu.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )

        OutlinedTextField(
            value = lead.name,
            onValueChange = onNameChange,
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = lead.phone,
            onValueChange = onPhoneChange,
            label = { Text("Phone number") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onContinue,
            enabled = lead.isValid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("View menu") }
    }
}
