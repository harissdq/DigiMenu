package com.digimenu.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {

    private fun order(id: String, total: Double, status: String) =
        Order(
            id = id,
            total = total,
            status = status,
            orderType = Order.ORDER_TYPE_DINE_IN,
            tableId = "Table_1",
        )

    @Test
    fun `billed orders exclude cancelled and rejected`() {
        val session = Session(
            tableId = "Table_1",
            orders = mutableMapOf(
                "a" to true,
                "b" to true,
                "c" to true,
                "d" to true,
            ),
        )
        val all = listOf(
            order("a", 100.0, Order.STATUS_DONE),
            order("b", 50.0, Order.STATUS_CANCELLED),
            order("c", 25.0, Order.STATUS_REJECTED),
            order("d", 10.0, Order.STATUS_READY),
        )
        val billed = session.billedOrders(all)
        assertEquals(setOf("a", "d"), billed.map { it.id }.toSet())
    }

    @Test
    fun `billed total sums only billable orders in the session`() {
        val session = Session(
            tableId = "Table_1",
            orders = mutableMapOf("a" to true, "b" to true, "other" to true),
        )
        val all = listOf(
            order("a", 120.0, Order.STATUS_DONE),
            order("b", 30.0, Order.STATUS_CANCELLED),
            // Order "other" is in the session map but absent from the live list.
        )
        assertEquals(120.0, session.billedTotal(all), 0.001)
    }

    @Test
    fun `orders not linked to the session never count`() {
        val session = Session(tableId = "Table_1", orders = mutableMapOf("a" to true))
        val all = listOf(order("a", 10.0, Order.STATUS_DONE), order("zz", 999.0, Order.STATUS_DONE))
        assertEquals(10.0, session.billedTotal(all), 0.001)
    }

    @Test
    fun `closed session keeps its settled total`() {
        val session = Session(
            tableId = "Table_1",
            status = Session.STATUS_CLOSED,
            total = 250.0,
            paid = true,
        )
        assertEquals(250.0, session.total, 0.001)
        assertTrue(session.paid)
        assertFalse(session.status == Session.STATUS_OPEN)
    }
}
