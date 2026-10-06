package com.supermarket.inventory.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.supermarket.inventory.MainActivity
import com.supermarket.inventory.R
import com.supermarket.inventory.data.isUnknownDevice
import com.supermarket.inventory.data.remote.dto.NewDeviceDto
import com.supermarket.inventory.ui.common.formatIsoDateTime

object NotificationHelper {
    const val CHANNEL_ID = "inventory_reminders"
    private const val LOW_STOCK_NOTIFICATION_ID = 1001
    private const val INVOICES_NOTIFICATION_ID = 1002
    private const val BACKUP_NOTIFICATION_ID = 1003
    private const val SALES_SYNC_NOTIFICATION_ID = 1004
    // Sign-in alerts take ids from here up, one per device; clear of the
    // fixed ids above and of the invoice reminders' 2001/2002.
    private const val SIGN_IN_NOTIFICATION_BASE = 10_000

    // Separate from the reminders channel: a sign-in from an unfamiliar device
    // is a security event, so it interrupts (heads-up) where a low-stock
    // reminder shouldn't, and the owner can silence one without the other.
    const val SIGN_IN_CHANNEL_ID = "sign_in_alerts"

    fun ensureSignInChannel(context: Context) {
        val channel = NotificationChannel(
            SIGN_IN_CHANNEL_ID,
            context.getString(R.string.notif_signin_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_signin_channel_description)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    // One notification per device rather than one replaced in place, so two
    // new devices in the same window can't hide each other.
    fun showNewSignIn(context: Context, device: NewDeviceDto) {
        ensureSignInChannel(context)
        val label = when {
            isUnknownDevice(device.deviceName, device.deviceModel) -> context.getString(R.string.devices_unknown)
            device.deviceModel != null && device.deviceModel != device.deviceName ->
                "${device.deviceName} (${device.deviceModel})"
            else -> device.deviceName
        }
        val time = formatIsoDateTime(device.signedInAt)
        val body = device.ip
            ?.let { context.getString(R.string.notif_signin_body_with_ip, label, time, it) }
            ?: context.getString(R.string.notif_signin_body, label, time)

        // Opens straight onto the device list, where it can be signed out.
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_DEVICES)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            device.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, SIGN_IN_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(context.getString(R.string.notif_signin_title))
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    body + "\n\n" + context.getString(R.string.notif_signin_advice)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notifySafely(SIGN_IN_NOTIFICATION_BASE + (device.id.hashCode() and 0xFFFF), notification)
    }

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

    fun showSyncIssue(context: Context, title: String, body: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notifySafely(SALES_SYNC_NOTIFICATION_ID, notification)
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
