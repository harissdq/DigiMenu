package com.digimenu.core.data

import com.digimenu.core.firebase.FirebaseRefs
import com.digimenu.core.model.Session
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
 * Live table sessions. Each session is a push key under `sessions/` with a
 * `tableId`, so one table has a full history of sessions (and bills) over time.
 * The manager app opens a session automatically when the first dine-in order
 * for a table arrives ([ensureOpen]), closes it when the guests pay
 * ([closeSession]) and marks it paid ([markPaid]).
 */
@Singleton
class SessionRepository @Inject constructor(
    private val db: FirebaseDatabase,
) {

    fun observeSessions(restaurantId: String = FirebaseRefs.DEFAULT_RESTAURANT): Flow<List<Session>> =
        callbackFlow {
            val ref = FirebaseRefs.sessions(db, restaurantId)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val sessions = snapshot.children.mapNotNull { snap ->
                        // The node key is the session id (writes use push()).
                        snap.getValue(Session::class.java)?.also { s ->
                            s.id = snap.key ?: s.id
                        }
                    }
                    trySend(sessions)
                }

                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            }
            ref.addValueEventListener(listener)
            awaitClose { ref.removeEventListener(listener) }
        }

    /**
     * Links [orderId] to [tableId]'s open session, creating a fresh session the
     * first time (or after the previous one was closed, so two consecutive
     * groups of guests never share a bill). Idempotent: no-op when the order is
     * already linked, so repeated calls after a restart are safe.
     */
    suspend fun ensureOpen(
        tableId: String,
        orderId: String,
        restaurantId: String = FirebaseRefs.DEFAULT_RESTAURANT,
    ) {
        val root = FirebaseRefs.sessions(db, restaurantId)
        val open = root.get().await().children
            .mapNotNull { snap ->
                snap.getValue(Session::class.java)?.also { s -> s.id = snap.key ?: s.id }
            }
            .firstOrNull { it.tableId == tableId && it.status == Session.STATUS_OPEN }

        if (open != null) {
            if (open.orders.containsKey(orderId)) return
            root.child(open.id).child("orders").child(orderId).setValue(true).await()
            return
        }

        val key = root.push().key ?: error("Session push failed")
        root.child(key).setValue(
            Session(
                id = key,
                tableId = tableId,
                status = Session.STATUS_OPEN,
                openedAt = System.currentTimeMillis(),
                orders = mutableMapOf(orderId to true),
            )
        ).await()
    }

    /** Closes the session and stores the settled bill total. */
    suspend fun closeSession(
        sessionId: String,
        total: Double,
        restaurantId: String = FirebaseRefs.DEFAULT_RESTAURANT,
    ) {
        val updates = HashMap<String, Any>()
        updates["status"] = Session.STATUS_CLOSED
        updates["closedAt"] = System.currentTimeMillis()
        updates["total"] = total
        FirebaseRefs.sessions(db, restaurantId).child(sessionId).updateChildren(updates).await()
    }

    /** Marks the session's bill as paid (archives it). */
    suspend fun markPaid(
        sessionId: String,
        restaurantId: String = FirebaseRefs.DEFAULT_RESTAURANT,
    ) {
        FirebaseRefs.sessions(db, restaurantId).child(sessionId)
            .updateChildren(mapOf("paid" to true)).await()
    }
}
