package com.digimenu.core.data

import com.digimenu.core.firebase.FirebaseRefs
import com.digimenu.core.model.TableSeat
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
 * Table registry. Each physical table has a canonical id (the value inside its
 * QR code) plus a human label. The QR resolver verifies a scanned payload
 * against this registry so only tables that actually exist can place orders.
 */
@Singleton
class TableRepository @Inject constructor(
    private val db: FirebaseDatabase,
) {

    fun observeTables(restaurantId: String = FirebaseRefs.DEFAULT_RESTAURANT): Flow<List<TableSeat>> =
        callbackFlow {
            val ref = FirebaseRefs.tables(db, restaurantId)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val tables = snapshot.children.mapNotNull { it.getValue(TableSeat::class.java) }
                        .sortedBy { it.label }
                    trySend(tables)
                }

                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            }
            ref.addValueEventListener(listener)
            awaitClose { ref.removeEventListener(listener) }
        }

    /** Creates a table (used by the manager's QR generator) or no-ops if it exists. */
    suspend fun ensureTable(
        id: String,
        label: String = id,
        restaurantId: String = FirebaseRefs.DEFAULT_RESTAURANT,
    ) {
        val ref = FirebaseRefs.tables(db, restaurantId).child(id)
        if (!ref.get().await().exists()) {
            ref.setValue(TableSeat(id = id, label = label, createdAt = System.currentTimeMillis())).await()
        }
    }

    /** True when the table exists. Throws on network failure so callers can show an offline state. */
    suspend fun exists(
        id: String,
        restaurantId: String = FirebaseRefs.DEFAULT_RESTAURANT,
    ): Boolean = FirebaseRefs.tables(db, restaurantId).child(id).get().await().exists()
}
