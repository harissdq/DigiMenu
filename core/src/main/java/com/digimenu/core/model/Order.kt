package com.digimenu.core.model

import com.google.firebase.database.IgnoreExtraProperties

/** One line of an order: which item, how many, at what unit price. */
@IgnoreExtraProperties
data class OrderLine(
    var name: String = "",
    var price: Double = 0.0,
    var qty: Int = 0,
)

/**
 * An order placed by a customer. Written by the customer web app and observed
 * live by the manager dashboard (a new order arrives the instant it is placed).
 * Dine-in orders carry [tableId]/[tableLabel]; take-away orders carry
 * [address] and use [Order.ORDER_TYPE_TAKEAWAY].
 */
@IgnoreExtraProperties
data class Order(
    var id: String = "",
    var orderType: String = Order.ORDER_TYPE_DINE_IN,
    var tableId: String = "",
    var tableLabel: String = "",
    var customerName: String = "",
    var customerPhone: String = "",
    var address: String = "",
    var items: MutableMap<String, OrderLine> = mutableMapOf(),
    var total: Double = 0.0,
    var status: String = STATUS_NEW,
    var createdAt: Long = 0L,
) {
    companion object {
        const val ORDER_TYPE_DINE_IN = "dine-in"
        const val ORDER_TYPE_TAKEAWAY = "takeaway"
        const val TAKEAWAY_TABLE_ID = "TAKEAWAY"
        const val TAKEAWAY_TABLE_LABEL = "Take Away"

        const val STATUS_NEW = "NEW"
        const val STATUS_PREPARING = "PREPARING"
        const val STATUS_DONE = "DONE"
        const val STATUS_CANCELLED = "CANCELLED"

        fun visibleToManager(): List<String> =
            listOf(STATUS_NEW, STATUS_PREPARING)
    }
}
