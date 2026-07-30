package com.example.mobile_app_herp.data

import org.json.JSONArray
import org.json.JSONObject

/** GET /v1/tenants/current */
data class Workspace(val slug: String, val name: String)

/** GET /v1/users/me */
data class CurrentUser(val id: String, val fullName: String, val email: String)

/**
 * GET /v1/properties — PropertyWithModules. `modules` holds the module KEYS the
 * calling user may use at this property, already intersected server-side with
 * the tenant's entitlements.
 */
data class Property(
    val id: String,
    val slug: String,
    val name: String,
    val timezone: String,
    val propertyType: String?,
    val modules: List<String>,
    val role: String?,
)

/** GET /v1/tenants/modules — the key → human title mapping. */
data class ModuleInfo(val key: String, val title: String, val category: String)

/**
 * GET /v1/tasks/{propertySlug} — a row of the shared task queue, tagged with
 * the module that raised it. `voiceNoteKey` is an OSS object key, not a URL:
 * the bucket is private, so playback needs a signed URL from
 * /tasks/{slug}/voice-note/sign.
 */
data class Task(
    val id: String,
    val module: String,
    val task: String,
    val status: String,
    val voiceNoteKey: String?,
    /** Null = never transcribed; empty = transcribed, no speech found. */
    val voiceTranscript: String?,
    /** Verbatim, in the language actually spoken. */
    val voiceTranscriptOriginal: String?,
    val assignedTo: String?,
    val assignedToName: String?,
    val createdByName: String?,
    val createdAt: String,
) {
    val hasVoiceNote: Boolean get() = !voiceNoteKey.isNullOrEmpty()
    val canTranscribe: Boolean get() = hasVoiceNote && voiceTranscript == null
}

/** GET /v1/tasks/{slug}/assignees — someone who works at this property. */
data class Assignee(val id: String, val fullName: String, val email: String)

/**
 * A voice note turned into text. Two readings plus an honest confidence, so the
 * app can decline to insert a doubtful sentence rather than passing off a guess
 * as what someone said.
 */
data class Transcription(
    /** Verbatim, in the language spoken. */
    val original: String,
    /** The meaning in English. */
    val english: String,
    val confidence: Double,
    /** True when the model marked part of the audio [unclear]. */
    val hasGaps: Boolean,
) {
    val isEmpty: Boolean get() = original.isBlank() && english.isBlank()

    /**
     * Below this the text is shown for a human to judge, never auto-inserted.
     * Mirrors CONFIDENT_ENOUGH in apps/api/src/lib/voice-to-text.ts.
     */
    val isTrustworthy: Boolean get() = !hasGaps && confidence >= 0.6
}

internal fun parseTranscription(json: String): Transcription = JSONObject(json).let {
    Transcription(
        original = it.optString("original", ""),
        english = it.optString("english", ""),
        confidence = it.optDouble("confidence", 0.0),
        hasGaps = it.optBoolean("hasGaps", false),
    )
}

internal fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name, "").takeIf { it.isNotEmpty() }

internal fun JSONArray.toStringList(): List<String> =
    (0 until length()).map { getString(it) }

internal fun parseWorkspace(json: String) = JSONObject(json).let {
    Workspace(slug = it.getString("slug"), name = it.getString("name"))
}

internal fun parseCurrentUser(json: String) = JSONObject(json).let {
    CurrentUser(
        id = it.getString("id"),
        fullName = it.optString("fullName", ""),
        email = it.optString("email", ""),
    )
}

internal fun parseProperties(json: String): List<Property> {
    val arr = JSONArray(json)
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Property(
            id = o.getString("id"),
            slug = o.getString("slug"),
            name = o.getString("name"),
            timezone = o.optString("timezone", ""),
            propertyType = o.optStringOrNull("propertyType"),
            modules = o.optJSONArray("modules")?.toStringList() ?: emptyList(),
            role = o.optStringOrNull("role"),
        )
    }
}

internal fun parseTask(o: JSONObject) = Task(
    id = o.getString("id"),
    module = o.optString("module", ""),
    task = o.optString("task", ""),
    status = o.optString("status", "pending"),
    voiceNoteKey = o.optStringOrNull("voiceNoteKey"),
    // Distinguishes absent from empty, which optStringOrNull cannot: an empty
    // transcript is a real answer ("nothing said") and must not read as "unasked".
    voiceTranscript = if (o.isNull("voiceTranscript")) null else o.optString("voiceTranscript", ""),
    voiceTranscriptOriginal =
        if (o.isNull("voiceTranscriptOriginal")) null else o.optString("voiceTranscriptOriginal", ""),
    assignedTo = o.optStringOrNull("assignedTo"),
    assignedToName = o.optStringOrNull("assignedToName"),
    createdByName = o.optStringOrNull("createdByName"),
    createdAt = o.optString("createdAt", ""),
)

internal fun parseTasks(json: String): List<Task> {
    val arr = JSONArray(json)
    return (0 until arr.length()).map { parseTask(arr.getJSONObject(it)) }
}

internal fun parseAssignees(json: String): List<Assignee> {
    val arr = JSONArray(json)
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Assignee(
            id = o.getString("id"),
            fullName = o.optString("fullName", ""),
            email = o.optString("email", ""),
        )
    }
}

internal fun parseModules(json: String): List<ModuleInfo> {
    val arr = JSONArray(json)
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        ModuleInfo(
            key = o.getString("key"),
            title = o.optString("title", o.getString("key")),
            category = o.optString("category", ""),
        )
    }
}
