package com.digimenu.core.model

import com.google.firebase.database.IgnoreExtraProperties

/**
 * A physical table that a QR code maps to. The `id` is the canonical value
 * embedded in the QR payload (e.g. "Table_1"); `label` is the human-readable
 * name shown to the customer and the manager.
 */
@IgnoreExtraProperties
data class TableSeat(
    var id: String = "",
    var label: String = "",
    var createdAt: Long = 0L,
)
