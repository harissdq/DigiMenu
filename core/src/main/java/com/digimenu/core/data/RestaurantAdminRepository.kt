package com.digimenu.core.data

import com.digimenu.core.firebase.FirebaseRefs
import com.digimenu.core.model.RestaurantInfo
import com.digimenu.core.qr.TableQrCode
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Admin operations: create restaurant tenants and link manager accounts to them.
 * Only the bootstrap admin account (see [FirebaseRefs.ADMIN_EMAIL]) is allowed
 * to use these — enforced by the Realtime Database rules.
 */
@Singleton
class RestaurantAdminRepository @Inject constructor(
    private val db: FirebaseDatabase,
    private val auth: FirebaseAuth,
) {

    /** All tenants, sorted by name. Reads require the admin role. */
    suspend fun listRestaurants(): List<RestaurantInfo> =
        FirebaseRefs.restaurantsRoot(db).get().await().children
            .mapNotNull { snap ->
                val id = snap.key ?: return@mapNotNull null
                val name = snap.child("info").child("name").getValue(String::class.java)
                RestaurantInfo(id = id, name = name ?: id)
            }
            .sortedBy { it.name.lowercase() }

    /**
     * Creates a tenant and links a manager to it. When [managerEmail] is given,
     * the manager's Auth account is created first (using the project's web API
     * key) and linked to the new restaurant; otherwise the admin themselves
     * becomes its manager.
     */
    suspend fun createRestaurant(
        name: String,
        tableLabels: List<String>,
        managerEmail: String?,
        managerPassword: String?,
    ): RestaurantInfo {
        val adminUid = auth.currentUser?.uid ?: error("Not signed in.")
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Restaurant name is required." }

        val managerUid = if (managerEmail.isNullOrBlank()) {
            adminUid
        } else {
            val password = managerPassword ?: error("Enter a password for the manager account.")
            createManagerAccount(managerEmail.trim(), password)
        }

        val id = uniqueRestaurantId(cleanName)
        val now = System.currentTimeMillis()

        val updates = HashMap<String, Any>()
        updates["restaurants/$id/info"] = mapOf("name" to cleanName)
        updates["restaurants/$id/managers/$managerUid"] = true
        val labels = tableLabels.map { it.trim() }.filter { it.isNotEmpty() }
            .ifEmpty { listOf("Table_1", "Table_2") }
        labels.forEach { label ->
            val tableId = TableQrCode.normalize(label)
            updates["restaurants/$id/tables/$tableId"] = mapOf(
                "id" to tableId,
                "label" to label.trim(),
                "createdAt" to now,
            )
        }
        updates["managers/$managerUid/restaurantId"] = id

        db.getReference().updateChildren(updates).await()
        return RestaurantInfo(id = id, name = cleanName)
    }

    private suspend fun uniqueRestaurantId(name: String): String {
        val base = slugify(name).ifBlank { "restaurant" }
        val existing = listRestaurants().map { it.id }.toSet()
        var candidate = base
        var n = 2
        while (existing.contains(candidate)) {
            candidate = "$base-$n"
            n++
        }
        return candidate
    }

    private fun slugify(name: String): String =
        name.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(48)

    /** Creates an email/password account via the Identity Toolkit REST API. */
    private suspend fun createManagerAccount(email: String, password: String): String {
        val apiKey = FirebaseApp.getInstance().options.apiKey
        return withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("email", email)
                .put("password", password)
                .put("returnSecureToken", true)

            val signUp = identityToolkitCall("accounts:signUp", apiKey, body)
            if (signUp.has("localId")) return@withContext signUp.getString("localId")

            // The email already exists (e.g. an earlier creation failed after the
            // Auth account was created). Recover the uid by signing in with the
            // same credentials and link that account to the new restaurant.
            val signIn = identityToolkitCall("accounts:signInWithPassword", apiKey, body)
            if (signIn.has("localId")) {
                return@withContext signIn.getString("localId")
            }
            error(
                "A manager account with $email already exists and its password " +
                    "does not match. Use the password you originally set, or a new email.",
            )
        }
    }

    private fun identityToolkitCall(endpoint: String, apiKey: String, body: JSONObject): JSONObject {
        val url = URL("https://identitytoolkit.googleapis.com/v1/$endpoint?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code !in 200..299) {
                error("$endpoint failed: ${json.optString("error", text)}")
            }
            return json
        } finally {
            conn.disconnect()
        }
    }
}
