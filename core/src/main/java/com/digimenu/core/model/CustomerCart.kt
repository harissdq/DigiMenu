package com.digimenu.core.model

/** A customer selected an item and some quantity. Kept client-side until checkout. */
data class CartItem(
    val item: MenuItem,
    var qty: Int = 1,
) {
    val lineTotal: Double get() = item.price * qty

    fun toOrderLine(): OrderLine = OrderLine(name = item.name, price = item.price, qty = qty)
}

/**
 * Lead captured before the customer can browse the menu. Required so the
 * manager knows who ordered from which table.
 */
data class CustomerLead(
    val name: String = "",
    val phone: String = "",
) {
    val isValid: Boolean get() = name.isNotBlank() && phone.replace(Regex("\\D"), "").length >= 7
}
