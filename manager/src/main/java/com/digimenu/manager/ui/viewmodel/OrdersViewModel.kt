package com.digimenu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digimenu.core.data.OrderRepository
import com.digimenu.core.data.RestaurantSession
import com.digimenu.core.model.Order
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val session: RestaurantSession,
) : ViewModel() {

    val orders: StateFlow<List<Order>> = session.restaurantId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else orderRepository.observeOrders(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateStatus(order: Order, status: String) {
        viewModelScope.launch {
            val restaurantId = session.restaurantId.value ?: return@launch
            runCatching { orderRepository.updateStatus(restaurantId = restaurantId, orderId = order.id, status = status) }
        }
    }
}
