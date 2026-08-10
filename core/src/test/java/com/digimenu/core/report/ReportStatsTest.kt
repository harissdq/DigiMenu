package com.digimenu.core.report

import com.digimenu.core.model.Order
import com.digimenu.core.model.OrderLine
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportStatsTest {

    private fun line(name: String, price: Double, qty: Int, category: String = "Main") =
        OrderLine(name = name, price = price, qty = qty, category = category)

    private fun order(
        id: String,
        total: Double,
        status: String,
        type: String = Order.ORDER_TYPE_DINE_IN,
        createdAt: Long = 0L,
        lines: List<OrderLine> = listOf(line("Karahi", total, 1)),
    ) = Order(
        id = id,
        total = total,
        status = status,
        orderType = type,
        createdAt = createdAt,
        items = lines.mapIndexed { i, l -> "$id:$i" to l }.toMap().toMutableMap(),
    )

    @Test
    fun `empty input yields empty stats`() {
        val stats = ReportStats.aggregate(emptyList())
        assertEquals(0, stats.totalOrders)
        assertEquals(0.0, stats.revenue, 0.001)
        assertEquals(0.0, stats.avgOrderValue, 0.001)
        assertEquals(emptyList<ReportStats.GroupedValue>(), stats.byItem)
    }

    @Test
    fun `revenue counts only non-terminal orders`() {
        val orders = listOf(
            order("a", 100.0, Order.STATUS_DONE),
            order("b", 50.0, Order.STATUS_READY),
            order("c", 30.0, Order.STATUS_CANCELLED),
            order("d", 20.0, Order.STATUS_REJECTED),
        )
        val stats = ReportStats.aggregate(orders)
        assertEquals(4, stats.totalOrders)
        assertEquals(1, stats.completedOrders)
        assertEquals(2, stats.cancelledRejectedOrders)
        assertEquals(150.0, stats.revenue, 0.001)
        assertEquals(37.5, stats.avgOrderValue, 0.001)
    }

    @Test
    fun `orders placed before the cutoff are excluded`() {
        val orders = listOf(
            order("old", 100.0, Order.STATUS_DONE, createdAt = 500L),
            order("new", 200.0, Order.STATUS_DONE, createdAt = 1000L),
        )
        val stats = ReportStats.aggregate(orders, fromMillis = 600L)
        assertEquals(1, stats.totalOrders)
        assertEquals(200.0, stats.revenue, 0.001)
    }

    @Test
    fun `aggregates by category and item`() {
        val orders = listOf(
            order("a", 200.0, Order.STATUS_DONE, lines = listOf(
                line("Karahi", 200.0, 1, "Main"),
                line("Roti", 30.0, 2, "Bread"),
            )),
            order("b", 60.0, Order.STATUS_DONE, lines = listOf(
                line("Roti", 30.0, 2, "Bread"),
            )),
        )
        val stats = ReportStats.aggregate(orders)
        val main = stats.byCategory.first { it.label == "Main" }
        assertEquals(1, main.count)
        assertEquals(200.0, main.revenue, 0.001)
        val bread = stats.byCategory.first { it.label == "Bread" }
        assertEquals(4, bread.count)
        assertEquals(120.0, bread.revenue, 0.001)

        val karahi = stats.byItem.first { it.label == "Karahi" }
        assertEquals(1, karahi.count)
        assertEquals(200.0, karahi.revenue, 0.001)
        val roti = stats.byItem.first { it.label == "Roti" }
        assertEquals(4, roti.count)
        assertEquals(120.0, roti.revenue, 0.001)
    }

    @Test
    fun `groups by order type`() {
        val orders = listOf(
            order("a", 100.0, Order.STATUS_DONE, type = Order.ORDER_TYPE_DINE_IN),
            order("b", 50.0, Order.STATUS_DONE, type = Order.ORDER_TYPE_TAKEAWAY),
        )
        val stats = ReportStats.aggregate(orders)
        assertEquals(1, stats.dineInCount)
        assertEquals(1, stats.takeawayCount)
    }

    @Test
    fun `groups are sorted by revenue descending`() {
        val orders = listOf(
            order("a", 0.0, Order.STATUS_DONE, lines = listOf(
                line("Cheap", 10.0, 1),
                line("Costly", 500.0, 1),
                line("Middle", 100.0, 1),
            )),
        )
        val labels = ReportStats.aggregate(orders).byItem.map { it.label }
        assertEquals(listOf("Costly", "Middle", "Cheap"), labels)
    }
}
