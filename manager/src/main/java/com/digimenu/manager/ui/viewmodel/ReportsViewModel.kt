package com.digimenu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digimenu.core.data.OrderRepository
import com.digimenu.core.data.RestaurantSession
import com.digimenu.core.report.ReportStats
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * P7 reports: aggregates the live [OrderRepository.observeOrders] stream into
 * [ReportStats] for the selected period. Fully derived data — nothing extra is
 * written to the database.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val session: RestaurantSession,
) : ViewModel() {

    enum class Period(val label: String, val daysBack: Long) {
        Today("Today", 1L),
        Week("7 days", 7L),
        Month("30 days", 30L),
        All("All time", Long.MAX_VALUE),
    }

    data class UiState(
        val period: Period = Period.Today,
        val stats: ReportStats = ReportStats.aggregate(emptyList()),
        val fromMillis: Long = 0L,
    )

    private val _period = MutableStateFlow(Period.Today)
    val period: StateFlow<Period> = _period.asStateFlow()

    val state: StateFlow<UiState> = combine(
        session.restaurantId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else orderRepository.observeOrders(id)
        },
        _period,
    ) { orders, period ->
        val from = startOfDay(period.daysBack)
        UiState(
            period = period,
            stats = ReportStats.aggregate(orders, fromMillis = from),
            fromMillis = from,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun selectPeriod(period: Period) {
        _period.value = period
    }

    /** Flat CSV for the selected period, ready to share. */
    fun buildCsv(state: UiState): String {
        val s = state.stats
        val sb = StringBuilder()
        sb.appendLine("DigiMenu sales report - ${state.period.label}")
        sb.appendLine("From,${state.fromMillis}")
        sb.appendLine("Total orders,${s.totalOrders}")
        sb.appendLine("Completed,${s.completedOrders}")
        sb.appendLine("Cancelled/rejected,${s.cancelledRejectedOrders}")
        sb.appendLine("Revenue,".plus("Rs. %.2f".format(s.revenue)))
        sb.appendLine("Average order value,".plus("Rs. %.2f".format(s.avgOrderValue)))
        sb.appendLine("Dine-in,${s.dineInCount}")
        sb.appendLine("Take-away,${s.takeawayCount}")
        sb.appendLine()
        sb.appendLine("Category,Items,Revenue")
        s.byCategory.forEach { g ->
            sb.appendLine("${g.label},${g.count},${g.revenue}")
        }
        sb.appendLine()
        sb.appendLine("Item,Qty,Revenue")
        s.byItem.forEach { g ->
            sb.appendLine("${g.label},${g.count},${g.revenue}")
        }
        return sb.toString()
    }

    private fun startOfDay(daysBack: Long): Long {
        if (daysBack >= Long.MAX_VALUE) return 0L
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -(daysBack.toInt() - 1))
        return cal.timeInMillis
    }
}
