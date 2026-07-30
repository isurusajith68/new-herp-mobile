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
    val assignedTo: String?,
    val assignedToName: String?,
    val createdByName: String?,
    val createdAt: String,
) {
    val hasVoiceNote: Boolean get() = !voiceNoteKey.isNullOrEmpty()
}

/** GET /v1/tasks/{slug}/assignees — someone who works at this property. */
data class Assignee(val id: String, val fullName: String, val email: String)

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
