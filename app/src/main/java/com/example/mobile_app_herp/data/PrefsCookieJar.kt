package com.example.mobile_app_herp.data

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject

/**
 * The IdP never returns the refresh token in a response body — it is an
 * HttpOnly cookie host-scoped to `{slug}-auth.{base}` (see the note on
 * TokenResponse in packages/types/src/auth.ts). A native client therefore needs
 * a real cookie jar, and it must survive process death or every cold start
 * would mean a fresh password prompt.
 *
 * Only persistent cookies are written to disk; session cookies stay in memory,
 * which is exactly the lifetime the server asked for.
 */
class PrefsCookieJar(private val prefs: Prefs) : CookieJar {

    private val store = LinkedHashMap<String, Cookie>()
    private var loaded = false

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = synchronized(this) {
        ensureLoaded()
        for (cookie in cookies) {
            val key = keyOf(cookie)
            if (cookie.expiresAt <= System.currentTimeMillis()) store.remove(key)
            else store[key] = cookie
        }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(this) {
        ensureLoaded()
        val now = System.currentTimeMillis()
        val expired = store.filterValues { it.expiresAt <= now }.keys.toList()
        if (expired.isNotEmpty()) {
            expired.forEach { store.remove(it) }
            persist()
        }
        return store.values.filter { it.matches(url) }
    }

    fun clear() = synchronized(this) {
        store.clear()
        loaded = true
        prefs.cookies = null
    }

    private fun keyOf(cookie: Cookie) = "${cookie.domain}|${cookie.path}|${cookie.name}"

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val raw = prefs.cookies ?: return
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val builder = Cookie.Builder()
                    .name(o.getString("name"))
                    .value(o.getString("value"))
                    .path(o.getString("path"))
                    .expiresAt(o.getLong("expiresAt"))
                if (o.getBoolean("hostOnly")) builder.hostOnlyDomain(o.getString("domain"))
                else builder.domain(o.getString("domain"))
                if (o.getBoolean("secure")) builder.secure()
                if (o.getBoolean("httpOnly")) builder.httpOnly()
                val cookie = builder.build()
                if (cookie.expiresAt > System.currentTimeMillis()) store[keyOf(cookie)] = cookie
            }
        }
    }

    private fun persist() {
        val arr = JSONArray()
        for (cookie in store.values) {
            if (!cookie.persistent) continue
            arr.put(
                JSONObject()
                    .put("name", cookie.name)
                    .put("value", cookie.value)
                    .put("domain", cookie.domain)
                    .put("path", cookie.path)
                    .put("expiresAt", cookie.expiresAt)
                    .put("secure", cookie.secure)
                    .put("httpOnly", cookie.httpOnly)
                    .put("hostOnly", cookie.hostOnly)
            )
        }
        prefs.cookies = if (arr.length() == 0) null else arr.toString()
    }
}
