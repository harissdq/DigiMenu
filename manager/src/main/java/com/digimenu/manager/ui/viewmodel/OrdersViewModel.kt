package com.digimenu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digimenu.core.data.OrderRepository
import com.digimenu.core.data.RestaurantSession
import com.digimenu.core.model.Order
import com.digimenu.manager.notification.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val session: RestaurantSession,
    private val notifications: NotificationHelper,
) : ViewModel() {

    private val seenOrders = mutableSetOf<String>()
    private var primed = false

    val orders: StateFlow<List<Order>> = session.restaurantId
        .flatMapLatest { id ->
            seenOrders.clear()
            primed = false
            if (id == null) flowOf(emptyList())
            else orderRepository.observeOrders(id).onEach { list ->
                if (!primed) {
                    // First snapshot for this restaurant: existing orders must not
                    // spam the notification, only newly arriving ones.
                    primed = true
                    list.forEach { seenOrders += it.id }
                } else {
                    val fresh = list.filter { it.status == Order.STATUS_NEW && it.id !in seenOrders }
                    list.forEach { seenOrders += it.id }
                    fresh.forEach { order ->
                        notifications.notifyNewOrder(session.restaurantName.value, order)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateStatus(order: Order, status: String) {
        viewModelScope.launch {
            val restaurantId = session.restaurantId.value ?: return@launch
            runCatching {
                orderRepository.updateStatus(restaurantId = restaurantId, orderId = order.id, status = status)
            }
        }
    }
}
