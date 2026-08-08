package com.digimenu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digimenu.core.data.RestaurantSession
import com.digimenu.core.data.TableRepository
import com.digimenu.core.model.TableSeat
import com.digimenu.core.qr.TableQrCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QrViewModel @Inject constructor(
    private val tableRepository: TableRepository,
    private val session: RestaurantSession,
) : ViewModel() {

    val restaurantId: StateFlow<String?> = session.restaurantId

    val tables: StateFlow<List<TableSeat>> = session.restaurantId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else tableRepository.observeTables(id)
        }
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
        val restaurantId = session.restaurantId.value
        if (restaurantId == null) {
            _message.value = "Not signed in to a restaurant."
            return
        }
        viewModelScope.launch {
            runCatching { tableRepository.ensureTable(restaurantId = restaurantId, id = id, label = raw) }
                .onSuccess { _message.value = "Table '$id' is ready — show its QR below." }
                .onFailure { _message.value = "Failed to create table: ${it.message}" }
        }
    }

    /** Payload for a physical table's QR code. */
    fun qrContent(table: TableSeat): String? {
        val restaurantId = session.restaurantId.value ?: return null
        return TableQrCode.encode(restaurantId, table.id)
    }

    /** Payload for the public take-away QR (no table; customers order from home). */
    fun takeawayContent(): String? {
        val restaurantId = session.restaurantId.value ?: return null
        return TableQrCode.encodeTakeaway(restaurantId)
    }

    fun clearMessage() {
        _message.value = null
    }
}
