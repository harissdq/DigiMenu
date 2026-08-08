package com.digimenu.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the tenant of the signed-in manager. Every repository call from the
 * manager app passes [restaurantId] so each restaurant only ever sees its own
 * menu, tables and orders.
 */
@Singleton
class RestaurantSession @Inject constructor(
    private val authRepository: AuthRepository,
    private val restaurantRepository: RestaurantRepository,
) {

    private val _restaurantId = MutableStateFlow<String?>(null)
    val restaurantId: StateFlow<String?> = _restaurantId.asStateFlow()

    private val _restaurantName = MutableStateFlow<String?>(null)
    val restaurantName: StateFlow<String?> = _restaurantName.asStateFlow()

    /** Resolves the current user's tenant (and its display name) from the DB. */
    suspend fun refresh() {
        val id = authRepository.currentRestaurantId()
        if (id == null) {
            clear()
            return
        }
        // The name is best-effort: some rule sets / old seeds don't expose
        // restaurants/{id}/info, and a denied read here must never stop the
        // tenant from resolving (that used to crash the app on login).
        val name = runCatching { restaurantRepository.info(id)?.name }.getOrNull()
        _restaurantId.value = id
        _restaurantName.value = name ?: id
    }

    fun clear() {
        _restaurantId.value = null
        _restaurantName.value = null
    }
}
