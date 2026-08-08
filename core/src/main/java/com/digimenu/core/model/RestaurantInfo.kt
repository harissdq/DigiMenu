package com.digimenu.core.model

import com.google.firebase.database.IgnoreExtraProperties

/** Public profile of a restaurant tenant, stored under `restaurants/{id}/info`. */
@IgnoreExtraProperties
data class RestaurantInfo(
    var id: String = "",
    var name: String = "",
)
