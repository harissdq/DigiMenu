package com.digimenu.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderStatusTest {

    @Test
    fun `new can be accepted rejected or cancelled`() {
        assertTrue(OrderStatus.canTransition(Order.STATUS_NEW, Order.STATUS_ACCEPTED))
        assertTrue(OrderStatus.canTransition(Order.STATUS_NEW, Order.STATUS_REJECTED))
        assertTrue(OrderStatus.canTransition(Order.STATUS_NEW, Order.STATUS_CANCELLED))
    }

    @Test
    fun `happy path follows accept start ready done`() {
        assertTrue(OrderStatus.canTransition(Order.STATUS_ACCEPTED, Order.STATUS_PREPARING))
        assertTrue(OrderStatus.canTransition(Order.STATUS_PREPARING, Order.STATUS_READY))
        assertTrue(OrderStatus.canTransition(Order.STATUS_READY, Order.STATUS_DONE))
    }

    @Test
    fun `active orders can be cancelled`() {
        assertTrue(OrderStatus.canTransition(Order.STATUS_ACCEPTED, Order.STATUS_CANCELLED))
        assertTrue(OrderStatus.canTransition(Order.STATUS_PREPARING, Order.STATUS_CANCELLED))
        assertTrue(OrderStatus.canTransition(Order.STATUS_READY, Order.STATUS_CANCELLED))
    }

    @Test
    fun `terminal states are locked`() {
        assertFalse(OrderStatus.canTransition(Order.STATUS_DONE, Order.STATUS_NEW))
        assertFalse(OrderStatus.canTransition(Order.STATUS_DONE, Order.STATUS_READY))
        assertFalse(OrderStatus.canTransition(Order.STATUS_CANCELLED, Order.STATUS_NEW))
        assertFalse(OrderStatus.canTransition(Order.STATUS_REJECTED, Order.STATUS_ACCEPTED))
    }

    @Test
    fun `skipping steps and going backwards are rejected`() {
        assertFalse(OrderStatus.canTransition(Order.STATUS_NEW, Order.STATUS_PREPARING))
        assertFalse(OrderStatus.canTransition(Order.STATUS_NEW, Order.STATUS_DONE))
        assertFalse(OrderStatus.canTransition(Order.STATUS_ACCEPTED, Order.STATUS_NEW))
        assertFalse(OrderStatus.canTransition(Order.STATUS_PREPARING, Order.STATUS_ACCEPTED))
        assertFalse(OrderStatus.canTransition(Order.STATUS_READY, Order.STATUS_PREPARING))
    }

    @Test
    fun `unknown statuses cannot transition`() {
        assertFalse(OrderStatus.canTransition("UNKNOWN", Order.STATUS_NEW))
        assertFalse(OrderStatus.canTransition(Order.STATUS_NEW, "UNKNOWN"))
    }

    @Test
    fun `active set covers all non-terminal statuses`() {
        val active = OrderStatus.ACTIVE_FOR_MANAGER.toSet()
        assertTrue(active.containsAll(listOf(
            Order.STATUS_NEW,
            Order.STATUS_ACCEPTED,
            Order.STATUS_PREPARING,
            Order.STATUS_READY,
        )))
        OrderStatus.TERMINAL.forEach { assertFalse("$it should not be active", it in active) }
    }

    @Test
    fun `labels are human friendly`() {
        assertEquals("New", OrderStatus.label(Order.STATUS_NEW))
        assertEquals("Accepted", OrderStatus.label(Order.STATUS_ACCEPTED))
        assertEquals("Preparing", OrderStatus.label(Order.STATUS_PREPARING))
        assertEquals("Ready", OrderStatus.label(Order.STATUS_READY))
        assertEquals("Completed", OrderStatus.label(Order.STATUS_DONE))
        assertEquals("Cancelled", OrderStatus.label(Order.STATUS_CANCELLED))
        assertEquals("Rejected", OrderStatus.label(Order.STATUS_REJECTED))
        assertEquals("UNKNOWN", OrderStatus.label("UNKNOWN"))
    }

    @Test
    fun `timeline index orders the happy path`() {
        assertEquals(0, OrderStatus.timelineIndex(Order.STATUS_NEW))
        assertEquals(1, OrderStatus.timelineIndex(Order.STATUS_ACCEPTED))
        assertEquals(2, OrderStatus.timelineIndex(Order.STATUS_PREPARING))
        assertEquals(3, OrderStatus.timelineIndex(Order.STATUS_READY))
        assertEquals(4, OrderStatus.timelineIndex(Order.STATUS_DONE))
        assertEquals(-1, OrderStatus.timelineIndex(Order.STATUS_REJECTED))
        assertEquals(-1, OrderStatus.timelineIndex(Order.STATUS_CANCELLED))
    }
}
