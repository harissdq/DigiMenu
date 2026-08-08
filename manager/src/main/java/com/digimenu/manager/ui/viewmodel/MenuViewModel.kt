package com.digimenu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digimenu.core.data.MenuRepository
import com.digimenu.core.model.MenuItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MenuForm(
    val name: String = "",
    val description: String = "",
    val price: String = "",
    val category: String = "Main",
)

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val menuRepository: MenuRepository,
) : ViewModel() {

    val items: StateFlow<List<MenuItem>> = menuRepository.observeMenu()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _form = MutableStateFlow(MenuForm())
    val form: StateFlow<MenuForm> = _form.asStateFlow()

    private val _editingId = MutableStateFlow<String?>(null)
    val editingId: StateFlow<String?> = _editingId.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun onNameChange(value: String) = _form.update { it.copy(name = value) }
    fun onDescriptionChange(value: String) = _form.update { it.copy(description = value) }
    fun onPriceChange(value: String) = _form.update { it.copy(price = value.filter { c -> c.isDigit() || c == '.' }) }
    fun onCategoryChange(value: String) = _form.update { it.copy(category = value) }

    fun startAdd() {
        _form.value = MenuForm()
        _editingId.value = null
    }

    fun startEdit(item: MenuItem) {
        _form.value = MenuForm(
            name = item.name,
            description = item.description,
            price = "%.2f".format(item.price),
            category = item.category,
        )
        _editingId.value = item.id
    }

    fun save() {
        val form = _form.value
        val price = form.price.toDoubleOrNull()
        if (form.name.isBlank() || price == null) {
            _message.value = "Name and a valid price are required."
            return
        }
        viewModelScope.launch {
            _saving.value = true
            runCatching {
                val editingId = _editingId.value
                if (editingId == null) {
                    menuRepository.addItem(
                        MenuItem(
                            name = form.name.trim(),
                            description = form.description.trim(),
                            price = price,
                            category = form.category.trim().ifBlank { "Main" },
                        )
                    )
                } else {
                    val current = items.value.firstOrNull { it.id == editingId }
                    menuRepository.updateItem(
                        MenuItem(
                            id = editingId,
                            name = form.name.trim(),
                            description = form.description.trim(),
                            price = price,
                            category = form.category.trim().ifBlank { "Main" },
                            available = current?.available ?: true,
                        )
                    )
                }
            }.onSuccess { _message.value = "Item saved." }
                .onFailure { _message.value = "Save failed: ${it.message}" }
            _saving.value = false
        }
    }

    fun delete(item: MenuItem) {
        viewModelScope.launch {
            runCatching { menuRepository.deleteItem(item.id) }
                .onSuccess { _message.value = "Deleted ${item.name}." }
                .onFailure { _message.value = "Delete failed: ${it.message}" }
        }
    }

    fun toggleAvailability(item: MenuItem) {
        viewModelScope.launch {
            menuRepository.setAvailability(item.id, !item.available)
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
