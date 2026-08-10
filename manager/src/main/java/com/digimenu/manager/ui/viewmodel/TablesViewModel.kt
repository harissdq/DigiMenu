package com.digimenu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digimenu.core.data.OrderRepository
import com.digimenu.core.data.RestaurantSession
import com.digimenu.core.data.SessionRepository
import com.digimenu.core.data.TableRepository
import com.digimenu.core.model.Order
import com.digimenu.core.model.Session
import com.digimenu.core.model.TableSeat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TablesViewModel @Inject constructor(
    private val tableRepository: TableRepository,
    private val sessionRepository: SessionRepository,
    private val orderRepository: OrderRepository,
    private val session: RestaurantSession,
) : ViewModel() {

    /** One table's live occupancy + bill, joined from the three real-time nodes. */
    data class TableState(
        val table: TableSeat,
        val activeSession: Session?,
        val sessionOrders: List<Order>,
    ) {
        val isOpen: Boolean get() = activeSession?.status == Session.STATUS_OPEN
        val isPaid: Boolean get() = activeSession?.paid == true
        /** Live total while open; the settled amount once closed. */
        val billTotal: Double
            get() {
                val s = activeSession ?: return 0.0
                return if (s.status == Session.STATUS_CLOSED) s.total
                else s.billedTotal(sessionOrders)
            }
    }

    val tables: StateFlow<List<TableState>> = session.restaurantId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else combine(
                tableRepository.observeTables(id),
                sessionRepository.observeSessions(id),
                orderRepository.observeOrders(id),
            ) { tables, sessions, orders ->
                tables.map { table ->
                    val mine = sessions.filter { it.tableId == table.id }
                    val selected = mine.firstOrNull { it.status == Session.STATUS_OPEN }
                        ?: mine.firstOrNull { it.status == Session.STATUS_CLOSED && !it.paid }
                        ?: mine.maxByOrNull { it.openedAt }
                    TableState(
                        table = table,
                        activeSession = selected,
                        sessionOrders = selected?.orders?.keys
                            ?.mapNotNull { key -> orders.firstOrNull { it.id == key } }
                            .orEmpty(),
                    )
                }.sortedBy { it.table.label }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun closeTable(state: TableState) {
        val active = state.activeSession ?: return
        viewModelScope.launch {
            val restaurantId = session.restaurantId.value ?: return@launch
            runCatching {
                sessionRepository.closeSession(
                    restaurantId = restaurantId,
                    sessionId = active.id,
                    total = state.billTotal,
                )
            }
        }
    }

    fun markPaid(state: TableState) {
        val active = state.activeSession ?: return
        viewModelScope.launch {
            val restaurantId = session.restaurantId.value ?: return@launch
            runCatching {
                sessionRepository.markPaid(restaurantId = restaurantId, sessionId = active.id)
            }
        }
    }
}
