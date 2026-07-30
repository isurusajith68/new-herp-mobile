package com.example.mobile_app_herp.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Push plumbing that does not belong to any one screen.
 *
 * The channel id here is the same string the server puts in every message
 * (`android.notification.channel_id` in lib/fcm.ts). Android 8+ silently drops a
 * notification whose channel does not exist, so if these two ever drift the
 * symptom is nothing at all — no error, no tray entry.
 */
const val REQUESTS_CHANNEL_ID = "herp-requests"

/** Survives individual screens: registration must finish even if the UI moves on. */
private val pushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

object Push {
    private const val TAG = "HerpPush"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            REQUESTS_CHANNEL_ID,
            "Work requests",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Requests assigned to you"
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    /**
     * Hands the current FCM token to the API. Safe to call on every launch — the
     * server upserts on the token, and a device whose token has not changed just
     * refreshes its last_seen_at.
     *
     * Silent on failure: push is an enhancement, and a user who cannot register
     * a token can still do every part of their job.
     */
    fun syncToken() {
        if (Herp.prefs.slug == null) return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> register(token) }
            .addOnFailureListener { e -> Log.w(TAG, "no FCM token: ${e.message}") }
    }

    fun register(token: String) {
        pushScope.launch {
            runCatching { Herp.client.registerPushToken(token) }
                .onSuccess { enabled -> Herp.prefs.pushToken = token; Herp.pushEnabled = enabled }
                .onFailure { Log.w(TAG, "token registration failed: ${it.message}") }
        }
    }

    /**
     * Drops this device server-side, then deletes the token locally.
     *
     * Order matters: the unregister call needs a valid session, so it has to run
     * BEFORE the session is cleared. Deleting the FCM token too means the next
     * user of a shared handset gets a fresh one rather than inheriting a
     * registration the server has already reassigned.
     */
    suspend fun unregister() {
        val token = Herp.prefs.pushToken ?: return
        runCatching { Herp.client.unregisterPushToken(token) }
            .onFailure { Log.w(TAG, "token unregister failed: ${it.message}") }
        Herp.prefs.pushToken = null
        runCatching { FirebaseMessaging.getInstance().deleteToken() }
    }
}
