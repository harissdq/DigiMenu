package com.digimenu.core.data

import com.digimenu.core.firebase.FirebaseRefs
import com.digimenu.core.model.Order
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live order store. The manager dashboard subscribes to [observeOrders]; a
 * customer's checkout is a single `setValue` that lands in the same node, so
 * the manager sees the incoming order instantly (no refresh needed).
 */
@Singleton
class OrderRepository @Inject constructor(
    private val db: FirebaseDatabase,
) {

    fun observeOrders(restaurantId: String = FirebaseRefs.DEFAULT_RESTAURANT): Flow<List<Order>> =
        callbackFlow {
            val ref = FirebaseRefs.orders(db, restaurantId)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val orders = snapshot.children.mapNotNull { it.getValue(Order::class.java) }
                        .sortedByDescending { it.createdAt }
                    trySend(orders)
                }

                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            }
            ref.addValueEventListener(listener)
            awaitClose { ref.removeEventListener(listener) }
        }

    /** Places an order and returns its id. */
    suspend fun placeOrder(
        order: Order,
        restaurantId: String = FirebaseRefs.DEFAULT_RESTAURANT,
    ): String {
        val ref = FirebaseRefs.orders(db, restaurantId)
        val key = ref.push().key ?: error("Order push failed")
        ref.child(key).setValue(
            order.copy(id = key, createdAt = System.currentTimeMillis())
        ).await()
        return key
    }

    suspend fun updateStatus(
        orderId: String,
        status: String,
        restaurantId: String = FirebaseRefs.DEFAULT_RESTAURANT,
    ) {
        FirebaseRefs.orders(db, restaurantId).child(orderId)
            .child("status").setValue(status).await()
    }
}
