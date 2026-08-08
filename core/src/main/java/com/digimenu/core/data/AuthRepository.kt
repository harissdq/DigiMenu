package com.digimenu.core.data

import com.digimenu.core.firebase.FirebaseRefs
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager authentication. Owners sign in with Firebase Auth (email/password);
 * authorisation is a separate uid check against the restaurant's `managers`
 * node, so an authenticated-but-unauthorised account is not enough.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseDatabase,
) {

    val currentUser: FirebaseUser? get() = auth.currentUser

    fun authState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun login(email: String, password: String): Result<Unit> =
        runCatching {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
        }.map { }

    suspend fun logout() {
        auth.signOut()
    }

    /** True when the signed-in user is an authorised manager of the restaurant. */
    suspend fun isManager(restaurantId: String = FirebaseRefs.DEFAULT_RESTAURANT): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val snapshot = runCatching {
            FirebaseRefs.managers(db, restaurantId).child(uid).get().await()
        }.getOrNull()
        return snapshot?.value == true
    }
}
