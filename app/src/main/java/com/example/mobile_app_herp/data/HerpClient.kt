package com.example.mobile_app_herp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** A failed call carrying the HTTP status, so callers can branch on 401/403. */
class HerpHttpException(val status: Int, message: String) : IOException(message)

/** The only module raising tasks today; the queue itself is module-agnostic. */
const val MODULE_INVENTORY = "inventory"

/** DELETE still needs a body object to hand OkHttp, even an empty one. */
private val EMPTY_BODY = ByteArray(0).toRequestBody(null, 0, 0)

/**
 * Everything the app needs from the new-herp backend.
 *
 * Auth is the OAuth code flow with PKCE, driven natively rather than through a
 * browser: `POST {auth}/login` validates the credentials and answers with the
 * redirect target as JSON (the web login SPA needs that because a 302 to a
 * cross-origin callback is unreadable by fetch — we need it because there is no
 * browser in the loop at all). We pull the `code` out of that URL and exchange
 * it at `POST {auth}/token`. The verifier never leaves the device and the code
 * never touches a redirect, so no browser hand-off is required.
 */
class HerpClient(private val prefs: Prefs) {

    private val cookieJar = PrefsCookieJar(prefs)

    private val http = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // The IdP answers /login with a 302-shaped JSON body, but /token and the
        // API can redirect; following them would drop the Authorization header
        // across hosts, so keep it off and treat a redirect as an error.
        .followRedirects(false)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /**
     * Reading a bill is one upload plus two AI passes server-side, so it
     * routinely runs past the 30s the ordinary client allows. Its own client
     * rather than a longer timeout everywhere: a stuck ordinary request should
     * still give up quickly.
     */
    private val billHttp = http.newBuilder()
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    // ── Auth ──────────────────────────────────────────────────────────────────

    /**
     * Signs in and stores the session. Throws [HerpHttpException] with status
     * 401 for bad credentials, 400 when the workspace slug is not a real tenant.
     */
    suspend fun login(slug: String, email: String, password: String) = withContext(Dispatchers.IO) {
        val verifier = Pkce.newVerifier()
        val redirectUri = HerpConfig.redirectUri(slug)

        val loginUrl = "${HerpConfig.authOrigin(slug)}/login".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", HerpConfig.CLIENT_ID)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("code_challenge", Pkce.challenge(verifier))
            .addQueryParameter("code_challenge_method", "S256")
            .build()

        val loginBody = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()

        val loginJson = execute(
            Request.Builder().url(loginUrl).post(loginBody.toRequestBody(jsonType)).build()
        )

        // { "redirect": "https://…/callback?code=…" }
        val redirect = JSONObject(loginJson).getString("redirect")
        val code = redirect.toHttpUrl().queryParameter("code")
            ?: throw HerpHttpException(500, "Sign-in response carried no authorization code")

        val tokenBody = JSONObject()
            .put("grant_type", "authorization_code")
            .put("client_id", HerpConfig.CLIENT_ID)
            .put("code", code)
            .put("code_verifier", verifier)
            .put("redirect_uri", redirectUri)
            .toString()

        val tokenJson = execute(
            Request.Builder()
                .url("${HerpConfig.authOrigin(slug)}/token")
                .post(tokenBody.toRequestBody(jsonType))
                .build()
        )

        val token = JSONObject(tokenJson)
        prefs.slug = slug
        prefs.email = email
        prefs.saveTokens(
            accessToken = token.getString("access_token"),
            expiresInSeconds = token.optLong("expires_in", 900L),
        )
    }

    /**
     * Swaps the stored refresh cookie for a new access token. Returns false when
     * the session is gone for good (revoked, expired, or replay-detected), which
     * is the signal to send the user back to the login screen.
     */
    private suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val slug = prefs.slug ?: return@withContext false
        val body = JSONObject()
            .put("grant_type", "refresh_token")
            .put("client_id", HerpConfig.CLIENT_ID)
            .toString()
        try {
            val json = execute(
                Request.Builder()
                    .url("${HerpConfig.authOrigin(slug)}/token")
                    .post(body.toRequestBody(jsonType))
                    .build()
            )
            val token = JSONObject(json)
            prefs.saveTokens(
                accessToken = token.getString("access_token"),
                expiresInSeconds = token.optLong("expires_in", 900L),
            )
            true
        } catch (e: HerpHttpException) {
            if (e.status == 401) logout()
            false
        }
    }

    /** Best-effort server-side revocation, then wipe the local session. */
    suspend fun logoutRemote() {
        val slug = prefs.slug
        if (slug != null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    execute(
                        Request.Builder()
                            .url("${HerpConfig.authOrigin(slug)}/logout")
                            .post("".toRequestBody(jsonType))
                            .build()
                    )
                }
            }
        }
        logout()
    }

    fun logout() {
        cookieJar.clear()
        prefs.clearSession()
    }

    // ── API ───────────────────────────────────────────────────────────────────

    private suspend fun apiGet(path: String): String = apiCall(path) { it.get() }

    private suspend fun apiSend(path: String, method: String, body: RequestBody): String =
        apiCall(path) { it.method(method, body) }

    /**
     * Authenticated call against `/v1{path}`. Refreshes proactively when the
     * access token is at or past its renewal point, and once reactively on a
     * 401 — the token could have been revoked server-side before it expired.
     * The request is rebuilt for the retry so the fresh token is picked up.
     */
    private suspend fun apiCall(
        path: String,
        build: (Request.Builder) -> Request.Builder,
    ): String {
        val slug = prefs.slug ?: throw HerpHttpException(401, "Not signed in")

        if (prefs.accessToken == null || System.currentTimeMillis() >= prefs.accessExpiresAt) {
            if (!refresh()) throw HerpHttpException(401, "Session expired — please sign in again")
        }

        return try {
            send(slug, path, build)
        } catch (e: HerpHttpException) {
            if (e.status != 401) throw e
            if (!refresh()) throw HerpHttpException(401, "Session expired — please sign in again")
            send(slug, path, build)
        }
    }

    private suspend fun send(
        slug: String,
        path: String,
        build: (Request.Builder) -> Request.Builder,
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url("${HerpConfig.apiOrigin(slug)}/v1$path")
            .header("Authorization", "Bearer ${prefs.accessToken}")
        execute(build(builder).build())
    }

    suspend fun workspace(): Workspace = parseWorkspace(apiGet("/tenants/current"))

    suspend fun me(): CurrentUser = parseCurrentUser(apiGet("/users/me"))

    suspend fun properties(): List<Property> = parseProperties(apiGet("/properties"))

    /**
     * Module titles for the workspace. Needs `access.tenant.read`, which a
     * property-scoped user may not hold — callers fall back to prettified keys
     * rather than failing the whole screen, so an empty list is a valid answer.
     */
    suspend fun tenantModules(): List<ModuleInfo> =
        try {
            parseModules(apiGet("/tenants/modules"))
        } catch (e: HerpHttpException) {
            if (e.status == 403) emptyList() else throw e
        }

    // ── Tasks / requests ──────────────────────────────────────────────────────

    suspend fun tasks(propertySlug: String, module: String = MODULE_INVENTORY): List<Task> =
        parseTasks(apiGet("/tasks/$propertySlug?module=$module"))

    /** Who this property's requests can be assigned to. */
    suspend fun assignees(propertySlug: String, module: String = MODULE_INVENTORY): List<Assignee> =
        parseAssignees(apiGet("/tasks/$propertySlug/assignees?module=$module"))

    suspend fun createTask(
        propertySlug: String,
        task: String,
        voiceNoteKey: String? = null,
        assignedTo: String? = null,
        module: String = MODULE_INVENTORY,
    ): Task {
        val body = JSONObject().put("module", module).put("task", task)
        if (!voiceNoteKey.isNullOrEmpty()) body.put("voiceNoteKey", voiceNoteKey)
        if (!assignedTo.isNullOrEmpty()) body.put("assignedTo", assignedTo)
        val json = apiSend("/tasks/$propertySlug", "POST", body.toString().toRequestBody(jsonType))
        return parseTask(JSONObject(json))
    }

    /** Needs `tasks.task.update` — manager and up, so a 403 here is expected for staff. */
    suspend fun updateTaskStatus(
        propertySlug: String,
        id: String,
        status: String,
        module: String = MODULE_INVENTORY,
    ): Task {
        val body = JSONObject().put("status", status)
        val json = apiSend(
            "/tasks/$propertySlug/$id?module=$module",
            "PATCH",
            body.toString().toRequestBody(jsonType),
        )
        return parseTask(JSONObject(json))
    }

    /**
     * Removes a request. The server allows your own unconditionally, and someone
     * else's only for manager and above — so a 403 here is a real answer, not a
     * misconfiguration.
     */
    suspend fun deleteTask(
        propertySlug: String,
        id: String,
        module: String = MODULE_INVENTORY,
    ) {
        apiSend("/tasks/$propertySlug/$id?module=$module", "DELETE", EMPTY_BODY)
    }

    /**
     * Reads back an existing request's voice note. The server caches the result,
     * so the second person to tap this on the same request gets it instantly.
     * An empty string means the recording held no speech.
     */
    suspend fun transcribeTask(
        propertySlug: String,
        id: String,
        module: String = MODULE_INVENTORY,
    ): Transcription = parseTranscription(
        apiSend("/tasks/$propertySlug/$id/transcribe?module=$module", "POST", EMPTY_BODY)
    )

    /**
     * Speech → text via Gemini, server-side. Returns an empty string when there
     * was no speech to make out, which is a distinct outcome from a failure and
     * the UI reports it differently.
     */
    suspend fun transcribeVoice(
        propertySlug: String,
        file: File,
        mimeType: String,
        module: String = MODULE_INVENTORY,
    ): Transcription {
        val part = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(mimeType.toMediaType()))
            .build()
        return parseTranscription(
            apiSend("/tasks/$propertySlug/transcribe?module=$module", "POST", part)
        )
    }

    /**
     * Uploads the recording and returns its OSS key, which is then attached to
     * the task. Two steps rather than one multipart create: the phone can send
     * the audio while the user is still typing, so saving stays instant.
     */
    suspend fun uploadVoiceNote(
        propertySlug: String,
        file: File,
        mimeType: String,
        module: String = MODULE_INVENTORY,
    ): String {
        val part = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(mimeType.toMediaType()))
            .build()
        val json = apiSend("/tasks/$propertySlug/voice-note?module=$module", "POST", part)
        return JSONObject(json).getString("key")
    }

    /** Object keys are useless on their own — the bucket is private. */
    suspend fun signVoiceNote(propertySlug: String, key: String): String {
        val encoded = URLEncoder.encode(key, "UTF-8")
        return JSONObject(apiGet("/tasks/$propertySlug/voice-note/sign?key=$encoded"))
            .getString("url")
    }

    // ── GRN bill OCR ──────────────────────────────────────────────────────────

    /**
     * Sends a photo of a supplier bill to be read.
     *
     * Slow by nature — the server uploads to storage, runs a Gemini vision pass
     * and then matches the lines against inventory items, all inside the one
     * request. [billHttp] gives it a longer read timeout than the shared client,
     * which would otherwise abandon a perfectly healthy call at 30 seconds.
     */
    suspend fun readBill(
        propertySlug: String,
        file: File,
        mimeType: String = ImagePrep.MIME_TYPE,
    ): BillReading {
        val slug = prefs.slug ?: throw HerpHttpException(401, "Not signed in")
        if (prefs.accessToken == null || System.currentTimeMillis() >= prefs.accessExpiresAt) {
            if (!refresh()) throw HerpHttpException(401, "Session expired — please sign in again")
        }

        suspend fun attempt(): String = withContext(Dispatchers.IO) {
            val part = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody(mimeType.toMediaType()))
                .build()
            val request = Request.Builder()
                .url("${HerpConfig.apiOrigin(slug)}/v1/inventory/$propertySlug/grns/bill-ocr")
                .header("Authorization", "Bearer ${prefs.accessToken}")
                .post(part)
                .build()
            billHttp.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) return@withContext body
                throw HerpHttpException(response.code, errorMessage(response.code, body))
            }
        }

        return parseBillReading(
            try {
                attempt()
            } catch (e: HerpHttpException) {
                if (e.status != 401) throw e
                if (!refresh()) throw HerpHttpException(401, "Session expired — please sign in again")
                attempt()
            }
        )
    }

    // ── Push registration ─────────────────────────────────────────────────────

    /**
     * Claims this device for the signed-in user. Returns whether the server can
     * actually send anything — no point prompting for notification permission
     * against a deployment with no Firebase credentials.
     */
    suspend fun registerPushToken(token: String): Boolean {
        val body = JSONObject().put("token", token).put("platform", "android")
        val json = apiSend("/push/register", "POST", body.toString().toRequestBody(jsonType))
        return JSONObject(json).optBoolean("pushEnabled", false)
    }

    suspend fun unregisterPushToken(token: String) {
        val body = JSONObject().put("token", token)
        apiSend("/push/unregister", "POST", body.toString().toRequestBody(jsonType))
    }

    // ── Plumbing ──────────────────────────────────────────────────────────────

    /** Runs a call and returns the body, mapping any non-2xx to an exception. */
    private fun execute(request: Request): String {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) return body
            throw HerpHttpException(response.code, errorMessage(response.code, body))
        }
    }

    /** The API's error shape is `{ "error": "…" }` (apps/api error-handler). */
    private fun errorMessage(status: Int, body: String): String {
        val fromBody = runCatching { JSONObject(body).optString("error", "") }
            .getOrDefault("")
            .takeIf { it.isNotEmpty() }
        return fromBody ?: when (status) {
            401 -> "Invalid credentials"
            403 -> "You do not have access to this"
            404 -> "Not found"
            else -> "Request failed ($status)"
        }
    }
}
