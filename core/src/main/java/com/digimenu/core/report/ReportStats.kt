package com.digimenu.core.report

import com.digimenu.core.model.Order
import com.digimenu.core.model.OrderLine

/**
 * Pure, side-effect-free aggregation over orders. Kept free of Firebase types so it
 * can be unit-tested on the JVM and reused by any client. All monetary values are
 * in the app's currency (Rs.).
 */
data class ReportStats(
    val totalOrders: Int = 0,
    val completedOrders: Int = 0,
    val cancelledRejectedOrders: Int = 0,
    val revenue: Double = 0.0,
    val avgOrderValue: Double = 0.0,
    val dineInCount: Int = 0,
    val takeawayCount: Int = 0,
    val byCategory: List<GroupedValue> = emptyList(),
    val byItem: List<GroupedValue> = emptyList(),
) {
    data class GroupedValue(
        val label: String,
        val count: Int,
        val revenue: Double,
    )

    companion object {
        /** Statuses that never produce revenue (a done order most certainly does). */
        val NON_REVENUE_STATUSES = setOf(
            Order.STATUS_CANCELLED,
            Order.STATUS_REJECTED,
        )

        /**
         * Aggregates [orders] placed at or after [fromMillis]. Cancelled and rejected
         * orders are counted but never contribute to revenue.
         */
        fun aggregate(orders: Collection<Order>, fromMillis: Long = 0L): ReportStats {
            var total = 0
            var completed = 0
            var cancelledRejected = 0
            var revenue = 0.0
            var dineIn = 0
            var takeaway = 0
            val catIndex = linkedMapOf<String, MutableList<OrderLine>>()
            val itemIndex = linkedMapOf<String, MutableList<OrderLine>>()

            for (order in orders) {
                if (order.createdAt < fromMillis) continue
                total++
                val terminal = order.status in NON_REVENUE_STATUSES
                val countsTowardsRevenue = !terminal
                if (order.status == Order.STATUS_DONE) completed++
                if (order.status == Order.STATUS_CANCELLED || order.status == Order.STATUS_REJECTED) {
                    cancelledRejected++
                }
                if (order.orderType == Order.ORDER_TYPE_DINE_IN) dineIn++
                else if (order.orderType == Order.ORDER_TYPE_TAKEAWAY) takeaway++
                if (countsTowardsRevenue) revenue += order.total

                for (line in order.items.values) {
                    val label = line.name.ifBlank { "(unknown)" }
                    val bucket = itemIndex.getOrPut(label) { mutableListOf() }
                    bucket.add(line)
                    if (line.category.isNotBlank()) {
                        val catBucket = catIndex.getOrPut(line.category.trim()) { mutableListOf() }
                        catBucket.add(line)
                    }
                }
            }

            return ReportStats(
                totalOrders = total,
                completedOrders = completed,
                cancelledRejectedOrders = cancelledRejected,
                revenue = revenue,
                avgOrderValue = if (total == 0) 0.0 else revenue / total,
                dineInCount = dineIn,
                takeawayCount = takeaway,
                byCategory = groupBy(itemIndex = catIndex),
                byItem = groupBy(itemIndex = itemIndex),
            )
        }

        /** Line revenue is (price * qty) regardless of the order's final status. */
        private fun groupBy(itemIndex: Map<String, List<OrderLine>>): List<GroupedValue> =
            itemIndex.entries
                .map { (label, lines) ->
                    val count = lines.sumOf { it.qty }
                    val value = lines.sumOf { it.price * it.qty }
                    GroupedValue(label, count, value)
                }
                .sortedWith(compareByDescending<GroupedValue> { it.revenue }
                    .thenBy { it.label.lowercase() })
    }
}
