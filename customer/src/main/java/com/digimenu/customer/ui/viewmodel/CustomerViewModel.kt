package com.digimenu.customer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digimenu.core.data.MenuRepository
import com.digimenu.core.data.OrderRepository
import com.digimenu.core.model.CartItem
import com.digimenu.core.model.CustomerLead
import com.digimenu.core.model.MenuItem
import com.digimenu.core.model.Order
import com.digimenu.core.qr.QrResolution
import com.digimenu.core.qr.QrTableResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the customer is in the flow. The scanner is the entry point. */
enum class CustomerStage {
    SCANNER,
    LEAD_CAPTURE,
    MENU,
    CONFIRMED,
}

data class CustomerUiState(
    val stage: CustomerStage = CustomerStage.SCANNER,
    val tableId: String = "",
    val tableLabel: String = "",
    val lead: CustomerLead = CustomerLead(),
    val cart: Map<String, CartItem> = emptyMap(),
    val message: String? = null,
    val placing: Boolean = false,
) {
    val cartItems: List<CartItem> get() = cart.values.toList()
    val cartTotal: Double get() = cartItems.sumOf { it.lineTotal }
}

@HiltViewModel
class CustomerViewModel @Inject constructor(
    private val resolver: QrTableResolver,
    private val menuRepository: MenuRepository,
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(CustomerUiState())
    val ui: StateFlow<CustomerUiState> = _ui.asStateFlow()

    val menu: StateFlow<List<MenuItem>> = menuRepository.observeMenu()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Handles every barcode the camera decodes; only the first valid one wins. */
    fun onQrScanned(raw: String) {
        if (_ui.value.stage != CustomerStage.SCANNER) return
        viewModelScope.launch {
            when (val result = resolver.resolve(raw)) {
                is QrResolution.Valid -> _ui.update {
                    it.copy(
                        stage = CustomerStage.LEAD_CAPTURE,
                        tableId = result.tableId,
                        tableLabel = result.tableLabel,
                        message = null,
                    )
                }
                is QrResolution.UnknownTable -> _ui.update {
                    it.copy(message = "Table ${result.tableId} was not found. Please ask staff.")
                }
                QrResolution.NotATable -> _ui.update {
                    it.copy(message = "This is not a DigiMenu table code.")
                }
                QrResolution.Offline -> _ui.update {
                    it.copy(message = "No internet connection. Please try again.")
                }
            }
        }
    }

    fun onLeadNameChange(value: String) {
        _ui.update { it.copy(lead = it.lead.copy(name = value), message = null) }
    }

    fun onLeadPhoneChange(value: String) {
        _ui.update { it.copy(lead = it.lead.copy(phone = value), message = null) }
    }

    fun saveLead() {
        val lead = _ui.value.lead
        if (!lead.isValid) {
            _ui.update { it.copy(message = "Please enter your name and a valid phone number.") }
            return
        }
        _ui.update { it.copy(stage = CustomerStage.MENU, message = null) }
    }

    fun addToCart(item: MenuItem) {
        _ui.update { state ->
            val cart = state.cart.toMutableMap()
            val existing = cart[item.id]
            if (existing != null) {
                cart[item.id] = CartItem(item, existing.qty + 1)
            } else {
                cart[item.id] = CartItem(item, 1)
            }
            state.copy(cart = cart, message = null)
        }
    }

    fun removeFromCart(itemId: String) {
        _ui.update { state ->
            val cart = state.cart.toMutableMap()
            val existing = cart[itemId]
            if (existing != null) {
                if (existing.qty > 1) cart[itemId] = CartItem(existing.item, existing.qty - 1)
                else cart.remove(itemId)
            }
            state.copy(cart = cart)
        }
    }

    fun placeOrder() {
        val state = _ui.value
        if (state.placing) return
        if (state.cart.isEmpty()) {
            _ui.update { it.copy(message = "Your cart is empty.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(placing = true, message = null) }
            val order = Order(
                tableId = state.tableId,
                tableLabel = state.tableLabel,
                customerName = state.lead.name.trim(),
                customerPhone = state.lead.phone.trim(),
                items = state.cart.mapValues { it.value.toOrderLine() }.toMutableMap(),
                total = state.cartTotal,
                status = Order.STATUS_NEW,
            )
            runCatching { orderRepository.placeOrder(order) }
                .onSuccess {
                    _ui.update {
                        it.copy(
                            stage = CustomerStage.CONFIRMED,
                            cart = emptyMap(),
                            placing = false,
                        )
                    }
                }
                .onFailure {
                    _ui.update {
                        it.copy(
                            placing = false,
                            message = "Order failed: ${it.message}",
                        )
                    }
                }
        }
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null) }
    }

    /** Returns the customer to the scanner (confirmation screen, restart). */
    fun restart() {
        _ui.value = CustomerUiState()
    }
}
