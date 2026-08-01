package com.supermarket.inventory.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.supermarket.inventory.R

object NotificationHelper {
    const val CHANNEL_ID = "inventory_reminders"
    private const val LOW_STOCK_NOTIFICATION_ID = 1001
    private const val INVOICES_NOTIFICATION_ID = 1002
    private const val BACKUP_NOTIFICATION_ID = 1003

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_description)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    fun showLowStock(context: Context, count: Int) {
        if (count <= 0) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.notif_low_stock_title))
            .setContentText(context.getString(R.string.notif_low_stock_body, count))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notifySafely(LOW_STOCK_NOTIFICATION_ID, notification)
    }

    fun showInvoiceReminder(context: Context, title: String, body: String, notificationId: Int = INVOICES_NOTIFICATION_ID) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notifySafely(notificationId, notification)
    }

    fun showBackupSaved(context: Context, title: String, body: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notifySafely(BACKUP_NOTIFICATION_ID, notification)
    }

    private fun NotificationManagerCompat.notifySafely(id: Int, notification: android.app.Notification) {
        try {
            notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted (Android 13+); the
            // in-app alert banners on Dashboard remain the fallback.
        }
    }
}
