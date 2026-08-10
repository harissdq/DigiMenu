package com.digimenu.manager.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digimenu.core.model.MenuItem
import com.digimenu.manager.ui.theme.DangerRed
import com.digimenu.manager.ui.theme.TextSecondary
import com.digimenu.manager.ui.util.base64ToBitmap
import com.digimenu.manager.ui.viewmodel.MenuForm
import com.digimenu.manager.ui.viewmodel.MenuViewModel

@Composable
fun MenuScreen(viewModel: MenuViewModel = hiltViewModel()) {
    val menuItems by viewModel.items.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val editingId by viewModel.editingId.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.startAdd()
                showEditor = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add item")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            message?.let {
                item {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (menuItems.isEmpty()) {
                item {
                    Text(
                        text = "No items yet. Tap + to add your first dish.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            } else {
                menuItems.groupBy { it.category }.forEach { (category, items) ->
                    item { Text(category, style = MaterialTheme.typography.titleMedium) }
                    items(items, key = { it.id }) { item ->
                        MenuItemCard(
                            item = item,
                            onEdit = {
                                viewModel.startEdit(item)
                                showEditor = true
                            },
                            onDelete = { viewModel.delete(item) },
                            onToggle = { viewModel.toggleAvailability(item) },
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        MenuEditorDialog(
            form = form,
            isEditing = editingId != null,
            saving = saving,
            onName = viewModel::onNameChange,
            onDescription = viewModel::onDescriptionChange,
            onPrice = viewModel::onPriceChange,
            onCategory = viewModel::onCategoryChange,
            onPhotoPicked = viewModel::onPhotoPicked,
            onPhotoRemove = viewModel::onPhotoRemove,
            onSave = {
                viewModel.save()
                showEditor = false
            },
            onDismiss = { showEditor = false },
        )
    }
}

@Composable
private fun MenuItemCard(
    item: MenuItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit,
) {
    val photoBitmap = remember(item.photo) { base64ToBitmap(item.photo, maxDim = 256) }
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (photoBitmap != null) {
                Box(
                    Modifier
                        .size(64.dp)
                        .padding(end = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = photoBitmap.asImageBitmap(),
                        contentDescription = item.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                            ),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    if (!item.available) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "OUT OF STOCK",
                            style = MaterialTheme.typography.labelSmall,
                            color = DangerRed,
                        )
                    }
                }
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
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TextButton(onClick = onToggle) {
                Text(if (item.available) "Out of stock" else "In stock")
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = DangerRed)
            }
        }
    }
}

@Composable
private fun MenuEditorDialog(
    form: MenuForm,
    isEditing: Boolean,
    saving: Boolean,
    onName: (String) -> Unit,
    onDescription: (String) -> Unit,
    onPrice: (String) -> Unit,
    onCategory: (String) -> Unit,
    onPhotoPicked: (ByteArray) -> Unit,
    onPhotoRemove: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { onPhotoPicked(it.readBytes()) }
            }
        }
    }

    val photoBitmap = remember(form.photo) { base64ToBitmap(form.photo, maxDim = 512) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit item" else "Add item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = onName,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.description,
                    onValueChange = onDescription,
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.price,
                    onValueChange = onPrice,
                    label = { Text("Price") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.category,
                    onValueChange = onCategory,
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (photoBitmap != null) {
                        Image(
                            bitmap = photoBitmap.asImageBitmap(),
                            contentDescription = "Dish photo",
                            modifier = Modifier
                                .size(72.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (form.photo.isBlank()) "Choose photo" else "Change photo")
                    }
                    if (form.photo.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onPhotoRemove) { Text("Remove") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !saving && form.name.isNotBlank(),
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
