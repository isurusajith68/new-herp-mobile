package com.example.mobile_app_herp.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Session storage. App-private SharedPreferences — the access token is short
 * lived (15 min) and the refresh cookie lives here too, so this file is the
 * whole session. Not encrypted at rest; app-private storage is the usual bar
 * for this, but a device-credential-backed store would be the upgrade.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("herp-session", Context.MODE_PRIVATE)

    var slug: String?
        get() = sp.getString(KEY_SLUG, null)
        set(v) = sp.edit().putString(KEY_SLUG, v).apply()

    var email: String?
        get() = sp.getString(KEY_EMAIL, null)
        set(v) = sp.edit().putString(KEY_EMAIL, v).apply()

    var accessToken: String?
        get() = sp.getString(KEY_ACCESS, null)
        set(v) = sp.edit().putString(KEY_ACCESS, v).apply()

    /** Epoch millis at which the access token stops being usable. */
    var accessExpiresAt: Long
        get() = sp.getLong(KEY_ACCESS_EXP, 0L)
        set(v) = sp.edit().putLong(KEY_ACCESS_EXP, v).apply()

    var cookies: String?
        get() = sp.getString(KEY_COOKIES, null)
        set(v) = sp.edit().putString(KEY_COOKIES, v).apply()

    /** The FCM token last registered with the API — needed to unregister it. */
    var pushToken: String?
        get() = sp.getString(KEY_PUSH_TOKEN, null)
        set(v) = sp.edit().putString(KEY_PUSH_TOKEN, v).apply()

    val hasSession: Boolean
        get() = slug != null && (accessToken != null || cookies != null)

    fun saveTokens(accessToken: String, expiresInSeconds: Long) {
        sp.edit()
            .putString(KEY_ACCESS, accessToken)
            // Renew a minute early so a request never leaves with a token that
            // expires mid-flight.
            .putLong(KEY_ACCESS_EXP, System.currentTimeMillis() + (expiresInSeconds - 60) * 1000)
            .apply()
    }

    /** Clears everything except the slug and email, so re-login is one field. */
    fun clearSession() {
        sp.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_ACCESS_EXP)
            .remove(KEY_COOKIES)
            .apply()
    }

    private companion object {
        const val KEY_SLUG = "slug"
        const val KEY_EMAIL = "email"
        const val KEY_ACCESS = "access_token"
        const val KEY_ACCESS_EXP = "access_expires_at"
        const val KEY_COOKIES = "cookies"
        const val KEY_PUSH_TOKEN = "push_token"
    }
}
