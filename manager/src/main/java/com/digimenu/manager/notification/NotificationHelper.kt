package com.digimenu.manager.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.digimenu.manager.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires a heads-up notification with a sound whenever a new order arrives, so
 * the restaurant hears it even when the app is in the background.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    init {
        ensureChannel()
    }

    companion object {
        private const val CHANNEL_ID = "digimenu_new_orders"
        private var notificationId = 1001
    }

    private val soundUri: Uri
        get() = Uri.parse("android.resource://${context.packageName}/${R.raw.order_sound}")

    /** Creates (or recreates) the notification channel with sound enabled. */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "New orders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Sound and vibration when a new customer order arrives."
                setSound(
                    soundUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun notifyNewOrder(restaurantName: String?, order: com.digimenu.core.model.Order) {
        val tableLabel = if (order.orderType == com.digimenu.core.model.Order.ORDER_TYPE_TAKEAWAY) {
            com.digimenu.core.model.Order.TAKEAWAY_TABLE_LABEL
        } else {
            "Table ${order.tableLabel.ifBlank { order.tableId }}"
        }
        val totalText = String.format(Locale.US, "Rs. %.0f", order.total)
        val title = restaurantName?.let { "New order — $it" } ?: "New order"
        val text = "${order.customerName.ifBlank { "Customer" }} ($tableLabel) • $totalText"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    text + "\n" +
                        order.items.values.joinToString("\n") { line ->
                            "${line.qty} × ${line.name}"
                        },
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setSound(soundUri)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId++, notification)
        } catch (ignored: SecurityException) {
            // POST_NOTIFICATIONS was denied; nothing to show.
        }
    }
}
