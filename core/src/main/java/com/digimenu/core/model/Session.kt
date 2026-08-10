package com.digimenu.core.model

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties

/**
 * A table session: the interval during which a group of diners occupies a
 * physical table, the orders they placed, and the resulting bill.
 *
 * Sessions are manager-side bookkeeping — the manager app opens one
 * automatically when the first dine-in order for a table arrives (see
 * [com.digimenu.core.data.SessionRepository.ensureOpen]) and closes it when the
 * guests pay. A closed, unpaid session is an open bill; marking it paid
 * archives it. Take-away orders (table id `"TAKEAWAY"`) never get sessions.
 */
@IgnoreExtraProperties
data class Session(
    var id: String = "",
    var tableId: String = "",
    var status: String = STATUS_OPEN,
    var openedAt: Long = 0L,
    var closedAt: Long = 0L,
    var orders: MutableMap<String, Boolean> = mutableMapOf(),
    var paid: Boolean = false,
    var total: Double = 0.0,
) {
    /** Orders referenced by [orders] that count towards the bill. */
    @Exclude
    fun billedOrders(allOrders: Collection<Order>): List<Order> =
        allOrders
            .filter { it.id in orders.keys }
            .filter { it.status != Order.STATUS_CANCELLED && it.status != Order.STATUS_REJECTED }

    /** Total of [billedOrders] using each order's own (server-recorded) total. */
    @Exclude
    fun billedTotal(allOrders: Collection<Order>): Double =
        billedOrders(allOrders).sumOf { it.total }

    companion object {
        const val STATUS_OPEN = "OPEN"
        const val STATUS_CLOSED = "CLOSED"
    }
}
