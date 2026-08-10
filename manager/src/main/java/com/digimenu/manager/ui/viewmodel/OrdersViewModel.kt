package com.digimenu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digimenu.core.data.OrderRepository
import com.digimenu.core.data.RestaurantSession
import com.digimenu.core.data.SessionRepository
import com.digimenu.core.model.Order
import com.digimenu.core.model.OrderStatus
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
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val seenOrders = mutableSetOf<String>()
    private val linkedSessionOrders = mutableSetOf<String>()
    private var primed = false

    val orders: StateFlow<List<Order>> = session.restaurantId
        .flatMapLatest { id ->
            seenOrders.clear()
            linkedSessionOrders.clear()
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
                linkDineInOrders(id, list)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Keeps table sessions in sync with real orders: every active dine-in order
     * gets linked to its table's session (opening it on first arrival).
     * Idempotent — the repository no-ops when already linked or the session is
     * closed, so this is safe after a restart or repeated snapshots.
     */
    private fun linkDineInOrders(restaurantId: String, orders: List<Order>) {
        orders.forEach { order ->
            if (order.orderType != Order.ORDER_TYPE_DINE_IN) return@forEach
            if (order.id.isBlank()) return@forEach
            if (order.status in OrderStatus.TERMINAL) return@forEach
            if (order.id in linkedSessionOrders) return@forEach
            linkedSessionOrders += order.id
            viewModelScope.launch {
                runCatching {
                    sessionRepository.ensureOpen(
                        restaurantId = restaurantId,
                        tableId = order.tableId,
                        orderId = order.id,
                    )
                }
            }
        }
    }

    fun updateStatus(order: Order, status: String, declineReason: String = "") {
        // Guard against illegal transitions (e.g. a stale tap after the order
        // already moved on) — the DB rules also enforce tenant access.
        if (!OrderStatus.canTransition(order.status, status)) return
        viewModelScope.launch {
            val restaurantId = session.restaurantId.value ?: return@launch
            runCatching {
                orderRepository.updateStatus(
                    restaurantId = restaurantId,
                    orderId = order.id,
                    status = status,
                    declineReason = declineReason,
                )
            }
        }
    }
}
