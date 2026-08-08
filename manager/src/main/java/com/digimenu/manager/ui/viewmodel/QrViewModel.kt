package com.digimenu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digimenu.core.data.TableRepository
import com.digimenu.core.model.TableSeat
import com.digimenu.core.qr.TableQrCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QrViewModel @Inject constructor(
    private val tableRepository: TableRepository,
) : ViewModel() {

    val tables: StateFlow<List<TableSeat>> = tableRepository.observeTables()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _label = MutableStateFlow("")
    val label: StateFlow<String> = _label.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun onLabelChange(value: String) {
        _label.value = value
    }

    /** Creates the table (if new) so its QR is valid, then returns its canonical id. */
    fun addTable() {
        val raw = _label.value.trim()
        if (raw.isBlank()) {
            _message.value = "Enter a table label first."
            return
        }
        val id = TableQrCode.normalize(raw)
        viewModelScope.launch {
            runCatching { tableRepository.ensureTable(id, raw) }
                .onSuccess { _message.value = "Table '$id' is ready — show its QR below." }
                .onFailure { _message.value = "Failed to create table: ${it.message}" }
        }
    }

    fun qrContent(table: TableSeat): String = TableQrCode.encode(table.id)

    fun clearMessage() {
        _message.value = null
    }
}
