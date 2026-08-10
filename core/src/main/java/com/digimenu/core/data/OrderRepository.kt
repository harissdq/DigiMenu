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
                    val orders = snapshot.children.mapNotNull { snap ->
                        // The customer web app writes orders via push() without an
                        // `id` field, so derive it from the node key. Without this,
                        // every order has id="" which makes LazyColumn keys collide
                        // (crash) and status updates write to the wrong path.
                        snap.getValue(Order::class.java)?.also { order ->
                            order.id = snap.key ?: order.id
                        }
                    }.sortedByDescending { it.createdAt }
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
        val now = System.currentTimeMillis()
        ref.child(key).setValue(
            order.copy(id = key, createdAt = now, statusChangedAt = now)
        ).await()
        return key
    }

    /**
     * Applies a status transition. Writes the new [status] and its timestamp
     * atomically so the customer tracker and the manager feed never see a
     * status without a [Order.statusChangedAt]. A rejected order additionally
     * stores [declineReason] for the customer.
     */
    suspend fun updateStatus(
        orderId: String,
        status: String,
        declineReason: String = "",
        restaurantId: String = FirebaseRefs.DEFAULT_RESTAURANT,
    ) {
        if (orderId.isBlank()) return
        val updates = HashMap<String, Any>()
        updates["status"] = status
        updates["statusChangedAt"] = System.currentTimeMillis()
        if (status == Order.STATUS_REJECTED) {
            updates["declineReason"] = declineReason
        }
        FirebaseRefs.orders(db, restaurantId).child(orderId)
            .updateChildren(updates).await()
    }
}
