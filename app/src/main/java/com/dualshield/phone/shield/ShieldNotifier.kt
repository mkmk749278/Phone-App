package com.dualshield.phone.shield

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dualshield.phone.R
import com.dualshield.phone.ui.MainActivity

/**
 * Optional, quiet confirmation that Shield did something.
 *
 * Off by default: the whole point of blocking is that the user is not interrupted, so a
 * notification for every blocked call would undo the feature. When enabled it is a single
 * low-priority summary, never one per call.
 */
class ShieldNotifier(private val context: Context) {

    @Volatile
    var enabled: Boolean = false

    private val manager get() = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Shield activity",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "A quiet summary when Shield blocks a call."
            setShowBadge(false)
        }
        runCatching { manager.createNotificationChannel(channel) }
    }

    // Guarded by canPost() immediately below, and wrapped in runCatching besides.
    @SuppressLint("MissingPermission")
    fun onCallBlocked() {
        if (!enabled || !canPost()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_VAULT, true)
        }
        val pending = android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle("Shield blocked a call")
            .setContentText("Open blocked call logs to see the details.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    /**
     * POST_NOTIFICATIONS only exists from API 33. Below that, notifications need no runtime
     * grant — and asking for an unknown permission comes back DENIED, which would have
     * silently disabled this feature on every Android 10 to 12 device.
     */
    private fun canPost(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private companion object {
        const val CHANNEL_ID = "shield_activity"
        const val NOTIFICATION_ID = 4201
    }
}
