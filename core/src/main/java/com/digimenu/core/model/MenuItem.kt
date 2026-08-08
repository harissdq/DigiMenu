package com.digimenu.core.model

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties

/**
 * A single item on the digital menu. All mutable state lives in Firebase
 * Realtime Database, so every change made by the manager (price, stock, name)
 * is pushed immediately and observed by every customer app with an open menu.
 */
@IgnoreExtraProperties
data class MenuItem(
    var id: String = "",
    var name: String = "",
    var description: String = "",
    var price: Double = 0.0,
    var category: String = "Main",
    var available: Boolean = true,
    var updatedAt: Long = 0L,
) {
    /** Compact representation for writes (avoids sending null/default noise). */
    @Exclude
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "price" to price,
        "category" to category,
        "available" to available,
        "updatedAt" to updatedAt,
    )
}
