package com.example.mobile_app_herp.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.mobile_app_herp.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives pushes from FCM.
 *
 * Android renders the tray entry itself when a message carries a `notification`
 * block and the app is backgrounded — this class exists for the two cases it
 * does not cover: a token rotating, and a message arriving while the app is in
 * the foreground (where the system delivers it here and shows nothing).
 */
class HerpMessagingService : FirebaseMessagingService() {

    /**
     * FCM rotates tokens on reinstall, restore, and occasionally on its own. A
     * rotated token that is never re-registered means the handset goes quiet
     * with nothing to show for it, so this path is not optional.
     */
    override fun onNewToken(token: String) {
        Herp.init(applicationContext)
        if (Herp.prefs.hasSession) Push.register(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Herp.init(applicationContext)
        Push.ensureChannel(applicationContext)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "New request"
        val body = message.notification?.body ?: message.data["body"] ?: return

        // Android 13+ needs the runtime permission before anything can be posted;
        // without this check notify() is a no-op that looks like a delivery bug.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("taskId", message.data["taskId"])
            putExtra("propertySlug", message.data["propertySlug"])
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, REQUESTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            // Requests are often a sentence or two — collapsing them to one line
            // hides the only useful part.
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        // Tag by task id so a re-sent notification for the same request replaces
        // the old one instead of stacking.
        val id = message.data["taskId"]?.hashCode() ?: System.currentTimeMillis().toInt()
        getSystemService(Context.NOTIFICATION_SERVICE).let { it as NotificationManager }
            .notify(id, notification)
    }
}
