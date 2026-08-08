package com.digimenu.core.data

import com.digimenu.core.firebase.FirebaseRefs
import com.digimenu.core.model.RestaurantInfo
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Tenant registry: each manager account belongs to one restaurant. */
@Singleton
class RestaurantRepository @Inject constructor(
    private val db: FirebaseDatabase,
) {

    suspend fun info(restaurantId: String): RestaurantInfo? =
        FirebaseRefs.info(db, restaurantId).get().await()
            .getValue(RestaurantInfo::class.java)
            ?.copy(id = restaurantId)

    suspend fun ensureInfo(restaurantId: String, name: String) {
        val ref = FirebaseRefs.info(db, restaurantId)
        if (!ref.get().await().exists()) {
            ref.setValue(RestaurantInfo(id = restaurantId, name = name)).await()
        }
    }
}
