package com.example.mobile_app_herp.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.example.mobile_app_herp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val fileName: String,
)

/**
 * Update check via GitHub Releases. Reads the repo's latest release and compares
 * its semantic-version tag with the installed versionName. When newer, the app
 * points the user at the APK download in a browser — it does NOT install APKs
 * itself. Self-installing needs REQUEST_INSTALL_PACKAGES, which makes Play
 * Protect flag or block sideloaded installs, so the browser is the installer.
 *
 * Every published APK must be signed with the same key (see the release
 * workflow), or Android rejects the update with a signature mismatch.
 *
 * Its own OkHttp client, not [HerpClient]'s: this call is unauthenticated, goes
 * to a different host, and must not share the session cookie jar.
 */
class GithubUpdater(private val context: Context) {

    private val repo = BuildConfig.GITHUB_REPO
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Latest release if it is newer than the installed build, else null. */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (repo.isBlank() || repo == "OWNER/REPO") return@withContext null

        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()

        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            resp.body?.string() ?: return@withContext null
        }

        val json = JSONObject(body)
        // Drafts and pre-releases are not for the floor.
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return@withContext null

        val tag = json.optString("tag_name").ifBlank { return@withContext null }
        val notes = json.optString("body").take(1500)

        val assets = json.optJSONArray("assets") ?: return@withContext null
        var apkUrl: String? = null
        var fileName = "update.apk"
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url")
                fileName = name
                break
            }
        }
        val url = apkUrl ?: return@withContext null

        if (!isNewer(tag, currentVersionName())) return@withContext null
        UpdateInfo(version = tag.removePrefix("v"), notes = notes, apkUrl = url, fileName = fileName)
    }

    /** Opens the APK download in a browser; the browser handles the install. */
    fun openDownload(info: UpdateInfo) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private fun currentVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (_: PackageManager.NameNotFoundException) {
        "0"
    }

    /** True when [remoteTag] (e.g. "v1.2.0") is a higher semantic version than [current]. */
    private fun isNewer(remoteTag: String, current: String): Boolean {
        val remote = parseVersion(remoteTag)
        val local = parseVersion(current)
        val size = maxOf(remote.size, local.size)
        for (i in 0 until size) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    private fun parseVersion(v: String): List<Int> =
        v.trim().removePrefix("v").split("-").first()
            .split(".")
            .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
}
