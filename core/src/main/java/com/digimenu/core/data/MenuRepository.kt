package com.digimenu.core.data

import com.digimenu.core.firebase.FirebaseRefs
import com.digimenu.core.model.MenuItem
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
 * Real-time menu store. The exposed [observeMenu] flow re-emits every time any
 * client writes to the menu node, so a price change made by the manager appears
 * instantly in every customer's open menu with zero polling.
 */
@Singleton
class MenuRepository @Inject constructor(
    private val db: FirebaseDatabase,
) {

    fun observeMenu(restaurantId: String = FirebaseRefs.DEFAULT_RESTAURANT): Flow<List<MenuItem>> =
        callbackFlow {
            val ref = FirebaseRefs.menu(db, restaurantId)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val items = snapshot.children.mapNotNull { it.getValue(MenuItem::class.java) }
                        .sortedWith(compareBy<MenuItem> { it.category }.thenBy { it.name })
                    trySend(items)
                }

                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            }
            ref.addValueEventListener(listener)
            awaitClose { ref.removeEventListener(listener) }
        }

    suspend fun addItem(
        item: MenuItem,
        restaurantId: String = FirebaseRefs.DEFAULT_RESTAURANT,
    ): String {
        val ref = FirebaseRefs.menu(db, restaurantId)
        val key = ref.push().key ?: error("Menu push failed")
        ref.child(key).setValue(item.copy(id = key, updatedAt = System.currentTimeMillis()).toMap()).await()
        return key
    }

    suspend fun updateItem(
        item: MenuItem,
        restaurantId: String = FirebaseRefs.DEFAULT_RESTAURANT,
    ) {
        val copy = item.copy(updatedAt = System.currentTimeMillis())
        FirebaseRefs.menu(db, restaurantId).child(item.id).setValue(copy.toMap()).await()
    }

    suspend fun deleteItem(
        itemId: String,
        restaurantId: String = FirebaseRefs.DEFAULT_RESTAURANT,
    ) {
        FirebaseRefs.menu(db, restaurantId).child(itemId).removeValue().await()
    }

    /** Marks an item as out of stock (or back in stock) in real time. */
    suspend fun setAvailability(
        itemId: String,
        available: Boolean,
        restaurantId: String = FirebaseRefs.DEFAULT_RESTAURANT,
    ) {
        FirebaseRefs.menu(db, restaurantId)
            .child(itemId)
            .updateChildren(
                mapOf(
                    "available" to available,
                    "updatedAt" to System.currentTimeMillis(),
                )
            ).await()
    }
}
