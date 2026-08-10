package com.digimenu.core.firebase

import com.google.firebase.database.FirebaseDatabase

/**
 * Canonical Firebase Realtime Database paths for a restaurant.
 *
 * The database is tenant-scoped under `restaurants/{restaurantId}` so one
 * Firebase project can serve many restaurants and both apps share the exact
 * same schema. `DEFAULT_RESTAURANT` is the demo tenant used by the boilerplate;
 * a production build should derive the id from the signed-in manager or from
 * the scanned QR payload instead.
 *
 * Schema:
 *   managers/{uid}/restaurantId  -> tenant id the account manages
 *   restaurants/{id}/
 *     info/name                  -> RestaurantInfo
 *     menu/{itemId}/             -> MenuItem
 *     tables/{tableId}/          -> TableSeat
 *     sessions/{tableId}/        -> Session (occupancy + bill)
 *     orders/{orderId}/          -> Order
 *     managers/{uid}/true        -> uid of an authorised manager
 */
object FirebaseRefs {

    const val DEFAULT_RESTAURANT = "demo-restaurant"

    /**
     * Bootstrap admin account that can create/manage restaurants from the app
     * and via the database rules. Production setups should move this to a
     * server-side admin list.
     */
    const val ADMIN_EMAIL = "haris.sdq@gmail.com"

    /**
     * Realtime Database URL for this project.
     *
     * Realtime Databases created outside the default (us-central1) region get a
     * region-specific URL such as
     * `https://<project>-default-rtdb.<region>.firebasedatabase.app`. The app
     * must connect to that exact URL. Find it in the Firebase Console under
     * Build → Realtime Database (the URL shown at the top of the Data tab), and
     * update this constant if it differs from the value below.
     */
    const val DATABASE_URL =
        "https://com-digimenu-manager-default-rtdb.asia-southeast1.firebasedatabase.app"

    /** Root lookup from an authenticated user to the tenant they manage. */
    fun managersRoot(db: FirebaseDatabase) = db.getReference("managers")

    /** Root of all restaurant tenants (admin-only reads/writes). */
    fun restaurantsRoot(db: FirebaseDatabase) = db.getReference("restaurants")

    fun restaurant(db: FirebaseDatabase, restaurantId: String = DEFAULT_RESTAURANT) =
        db.getReference("restaurants").child(restaurantId)

    fun info(db: FirebaseDatabase, restaurantId: String = DEFAULT_RESTAURANT) =
        restaurant(db, restaurantId).child("info")

    fun menu(db: FirebaseDatabase, restaurantId: String = DEFAULT_RESTAURANT) =
        restaurant(db, restaurantId).child("menu")

    fun tables(db: FirebaseDatabase, restaurantId: String = DEFAULT_RESTAURANT) =
        restaurant(db, restaurantId).child("tables")

    /** Occupancy + bill per table (manager-only read/write). */
    fun sessions(db: FirebaseDatabase, restaurantId: String = DEFAULT_RESTAURANT) =
        restaurant(db, restaurantId).child("sessions")

    fun orders(db: FirebaseDatabase, restaurantId: String = DEFAULT_RESTAURANT) =
        restaurant(db, restaurantId).child("orders")

    fun managers(db: FirebaseDatabase, restaurantId: String = DEFAULT_RESTAURANT) =
        restaurant(db, restaurantId).child("managers")
}
