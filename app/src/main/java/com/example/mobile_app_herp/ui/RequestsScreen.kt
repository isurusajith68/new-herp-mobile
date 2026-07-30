package com.example.mobile_app_herp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mobile_app_herp.data.Herp
import com.example.mobile_app_herp.data.HerpHttpException
import com.example.mobile_app_herp.data.Property
import com.example.mobile_app_herp.data.Task
import com.example.mobile_app_herp.data.VoicePlayer
import com.example.mobile_app_herp.ui.theme.HerpType
import com.example.mobile_app_herp.ui.theme.statusColors
import kotlinx.coroutines.launch

/**
 * The request queue. Every row is a ticket: a status spine you can read from
 * across a store room, a spoken id, who owns it, and the words as written.
 */
@Composable
fun RequestsScreen(
    property: Property,
    key: Int,
    onBack: () -> Unit,
    onNewRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val player = remember { VoicePlayer() }
    var tasks by remember { mutableStateOf<List<Task>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var playingId by remember { mutableStateOf<String?>(null) }
    var transcribingId by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    // Deleting is irreversible and the button sits next to "Mark done", so it
    // asks first — and quotes the request back so you can see which one.
    var pendingDelete by remember { mutableStateOf<Task?>(null) }

    DisposableEffect(Unit) { onDispose { player.stop() } }

    suspend fun load() {
        error = null
        runCatching { Herp.client.tasks(property.slug) }
            .onSuccess { tasks = it }
            .onFailure { error = it.message ?: "Could not load requests" }
    }

    LaunchedEffect(key, property.slug) { load() }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            load()
            refreshing = false
        }
    }

    fun play(task: Task) {
        val noteKey = task.voiceNoteKey ?: return
        if (playingId == task.id) {
            player.stop()
            playingId = null
            return
        }
        playingId = task.id
        notice = null
        scope.launch {
            runCatching { Herp.client.signVoiceNote(property.slug, noteKey) }
                .onSuccess { url ->
                    player.play(
                        source = url,
                        onError = { notice = it },
                        onDone = { playingId = null },
                    )
                }
                .onFailure {
                    playingId = null
                    notice = it.message ?: "That recording wouldn't play"
                }
        }
    }

    fun transcribe(task: Task) {
        if (transcribingId != null) return
        transcribingId = task.id
        notice = null
        scope.launch {
            runCatching { Herp.client.transcribeTask(property.slug, task.id) }
                .onSuccess { text ->
                    transcribingId = null
                    // Patched in place so the card fills in where you are looking,
                    // instead of the list jumping under your thumb.
                    tasks = tasks?.map {
                        if (it.id == task.id) it.copy(voiceTranscript = text) else it
                    }
                    if (text.isBlank()) notice = "No speech in that recording — play it instead."
                }
                .onFailure {
                    transcribingId = null
                    notice = it.message ?: "Couldn't read that recording"
                }
        }
    }

    fun deleteTask(task: Task) {
        scope.launch {
            runCatching { Herp.client.deleteTask(property.slug, task.id) }
                .onSuccess {
                    notice = null
                    // Dropped locally rather than refetched: the row is gone, and
                    // a full reload would flash the whole list for one removal.
                    tasks = tasks?.filterNot { it.id == task.id }
                }
                .onFailure {
                    notice = if (it is HerpHttpException && it.status == 403) {
                        "Only a manager can delete a request someone else raised"
                    } else {
                        it.message ?: "That request wasn't deleted"
                    }
                }
        }
    }

    fun markDone(task: Task) {
        scope.launch {
            runCatching { Herp.client.updateTaskStatus(property.slug, task.id, "done") }
                .onSuccess { updated ->
                    notice = null
                    tasks = tasks?.map { if (it.id == updated.id) updated else it }
                }
                .onFailure {
                    notice = if (it is HerpHttpException && it.status == 403) {
                        "Only a manager can close a request"
                    } else {
                        it.message ?: "That request didn't update"
                    }
                }
        }
    }

    val open = tasks?.count { it.status == "pending" || it.status == "in_progress" }

    Box(modifier.fillMaxSize()) {
        // The refresh container wraps only the scrolling content — a pull gesture
        // must not drag the button that raises a new request.
        Refreshable(refreshing = refreshing, onRefresh = ::refresh, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = onBack,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(
                    "← ${property.name.uppercase()}",
                    style = HerpType.Action,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(6.dp))
            ScreenHeader(
                eyebrow = "Inventory",
                title = "Requests",
                subtitle = open?.let {
                    if (it == 0) "Nothing open" else "$it still open"
                },
            )

            if (notice != null) {
                Spacer(Modifier.height(16.dp))
                Notice(notice!!, label = "Not done")
            }

            Spacer(Modifier.height(20.dp))

            when {
                error != null -> ErrorState(error!!, onRetry = ::refresh)

                tasks == null -> LoadingState("Loading requests")

                tasks!!.isEmpty() -> EmptyState(
                    title = "Nothing on the board",
                    detail = "Raise the first request — type it, or record it and let the " +
                        "store keeper hear it in your own words.",
                )

                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(tasks!!, key = { it.id }) { task ->
                        Ticket(
                            task = task,
                            isPlaying = playingId == task.id,
                            isTranscribing = transcribingId == task.id,
                            onPlay = { play(task) },
                            onTranscribe = { transcribe(task) },
                            onMarkDone = { markDone(task) },
                            onDelete = { pendingDelete = task },
                        )
                    }
                    // Clears the button so the last ticket is never trapped.
                    item { Spacer(Modifier.height(92.dp)) }
                }
            }
        }
        }

        pendingDelete?.let { doomed ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                shape = CardShape,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                title = {
                    Column {
                        Text(
                            "DELETE ${ticketId(doomed.id)}",
                            style = HerpType.Eyebrow,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Delete this request?",
                            style = HerpType.Title,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            doomed.task,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (doomed.hasVoiceNote) {
                                "The request and its voice note are removed for good."
                            } else {
                                "This cannot be undone."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        deleteTask(doomed)
                        pendingDelete = null
                    }) {
                        Text("DELETE", style = HerpType.Action, color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(
                            "KEEP IT",
                            style = HerpType.Action,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }

        ExtendedFloatingActionButton(
            onClick = onNewRequest,
            shape = CardShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            text = { Text("NEW REQUEST", style = HerpType.Action) },
            icon = { Text("+", style = HerpType.Title) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(Gutter),
        )
    }
}

@Composable
private fun Ticket(
    task: Task,
    isPlaying: Boolean,
    isTranscribing: Boolean,
    onPlay: () -> Unit,
    onTranscribe: () -> Unit,
    onMarkDone: () -> Unit,
    onDelete: () -> Unit,
) {
    val tint = statusColors.of(task.status)
    val closed = task.status == "done" || task.status == "cancelled"

    SpineCard(spine = tint) {
        Column {
            // Header: the spoken id on the left, the stamp on the right.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    ticketId(task.id),
                    style = HerpType.Record,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Stamp(statusLabel(task.status), tint)
            }

            Spacer(Modifier.height(10.dp))

            // The words somebody wrote. Plain Roboto, reading size, never shouted.
            Text(
                task.task,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // What the recording said, once someone asked. Indented behind a rule
            // so it reads as reported speech and never as the request itself.
            if (!task.voiceTranscript.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                // IntrinsicSize.Min lets the rule match the height of the text
                // beside it, however many lines that turns out to be.
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Stamp("Voice note said", MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            task.voiceTranscript,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Who owns it. A name with a tag; unassigned says so plainly.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (task.assignedToName != null) {
                    KeyTag(task.assignedToName, tint = MaterialTheme.colorScheme.primary, size = 26)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        task.assignedToName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    Stamp("Unassigned", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                listOfNotNull(
                    task.createdByName?.let { "raised by $it" },
                    task.createdAt.take(10).ifEmpty { null },
                ).joinToString("  ·  "),
                style = HerpType.Record,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (task.hasVoiceNote) {
                    TextButton(onClick = onPlay) {
                        Text(
                            if (isPlaying) "■ STOP" else "▶ PLAY",
                            style = HerpType.Action,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                // Offered only until it has been read — afterwards the words are
                // on the card and asking again would just cost another call.
                if (task.canTranscribe) {
                    TextButton(onClick = onTranscribe, enabled = !isTranscribing) {
                        if (isTranscribing) {
                            CircularProgressIndicator(
                                Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                "READ IT",
                                style = HerpType.Action,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (!closed) {
                    TextButton(onClick = onMarkDone) {
                        Text(
                            "MARK DONE",
                            style = HerpType.Action,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                // Pushed to the far edge, away from the action you actually mean
                // to press. Destructive work should take a deliberate reach.
                TextButton(onClick = onDelete) {
                    Text(
                        "DELETE",
                        style = HerpType.Action,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "pending" -> "Pending"
    "in_progress" -> "In progress"
    "done" -> "Done"
    else -> "Cancelled"
}
