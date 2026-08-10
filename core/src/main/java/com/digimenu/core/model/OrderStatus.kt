package com.digimenu.core.model

/**
 * Canonical order statuses and the allowed transitions between them.
 *
 * Lifecycle (all transitions are manager-driven except the initial create):
 *
 *   NEW --accept--> ACCEPTED --start--> PREPARING --ready--> READY --complete--> DONE
 *    |   \-reject--> REJECTED (with declineReason)
 *    \----cancel--------------------------> CANCELLED
 *
 * Terminal states: DONE, CANCELLED, REJECTED.
 *
 * Pure JVM so it can be unit tested without Android dependencies.
 */
object OrderStatus {

    /** Statuses that still need manager attention (shown in the active feed). */
    val ACTIVE_FOR_MANAGER: List<String> =
        listOf(Order.STATUS_NEW, Order.STATUS_ACCEPTED, Order.STATUS_PREPARING, Order.STATUS_READY)

    val TERMINAL: Set<String> =
        setOf(Order.STATUS_DONE, Order.STATUS_CANCELLED, Order.STATUS_REJECTED)

    private val allowedTransitions: Map<String, Set<String>> = mapOf(
        Order.STATUS_NEW to setOf(
            Order.STATUS_ACCEPTED,
            Order.STATUS_REJECTED,
            Order.STATUS_CANCELLED,
        ),
        Order.STATUS_ACCEPTED to setOf(
            Order.STATUS_PREPARING,
            Order.STATUS_CANCELLED,
        ),
        Order.STATUS_PREPARING to setOf(
            Order.STATUS_READY,
            Order.STATUS_CANCELLED,
        ),
        Order.STATUS_READY to setOf(
            Order.STATUS_DONE,
            Order.STATUS_CANCELLED,
        ),
        Order.STATUS_DONE to emptySet(),
        Order.STATUS_CANCELLED to emptySet(),
        Order.STATUS_REJECTED to emptySet(),
    )

    /** Whether [from] may legally move to [to]. Unknown statuses are locked. */
    fun canTransition(from: String, to: String): Boolean =
        allowedTransitions[from]?.contains(to) == true

    /** Human-friendly label for display in the manager app and web tracker. */
    fun label(status: String): String = when (status) {
        Order.STATUS_NEW -> "New"
        Order.STATUS_ACCEPTED -> "Accepted"
        Order.STATUS_PREPARING -> "Preparing"
        Order.STATUS_READY -> "Ready"
        Order.STATUS_DONE -> "Completed"
        Order.STATUS_CANCELLED -> "Cancelled"
        Order.STATUS_REJECTED -> "Rejected"
        else -> status
    }

    /**
     * Position of a status on the customer-facing timeline
     * Placed → Accepted → Preparing → Ready → Completed. -1 when the order is
     * not on the happy path (rejected/cancelled).
     */
    fun timelineIndex(status: String): Int = when (status) {
        Order.STATUS_NEW -> 0
        Order.STATUS_ACCEPTED -> 1
        Order.STATUS_PREPARING -> 2
        Order.STATUS_READY -> 3
        Order.STATUS_DONE -> 4
        else -> -1
    }
}
