package com.example.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.data.model.AppNotification

/**
 * Thin wrapper around the system NotificationManager. AppNotification rows are always written
 * to Room first (see ProjectRepository) — that's the durable "notification state" the spec asks
 * for (section 20: "Store notification state in Room if necessary"). This class only handles
 * showing a system-tray alert for a row that was just created; a missing/denied permission or a
 * pre-Android-13 device never loses the notification, since the in-app list (Room) still has it.
 */
object NotificationHelper {
    private const val CHANNEL_ID = "cfw_materialflow_alerts"
    private const val CHANNEL_NAME = "CFW MaterialFlow Alerts"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Project approvals, material requests, dispatches, and low/finished material alerts"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Shows one system-tray notification for [notification]. The title is prefixed with the
     * target role (e.g. "[TM] Materials Approved") since, without per-role login, a single
     * device/install may be used to view alerts meant for more than one role (see the note in
     * ProjectRepository about this being a single local database, not a synced multi-device
     * system).
     */
    fun show(context: Context, notification: AppNotification) {
        ensureChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("[${notification.targetRole}] ${notification.title}")
            .setContentText(notification.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notification.id.toInt(), builder.build())
        } catch (e: SecurityException) {
            // Permission was revoked between the check above and notify() — the Room row (and
            // the in-app notification list) still has this alert, so nothing is lost.
        }
    }
}
