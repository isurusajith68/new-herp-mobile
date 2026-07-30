package com.example.mobile_app_herp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    var attempt by remember { mutableStateOf(0) }

    DisposableEffect(Unit) { onDispose { player.stop() } }

    LaunchedEffect(attempt, key, property.slug) {
        error = null
        runCatching { Herp.client.tasks(property.slug) }
            .onSuccess { tasks = it }
            .onFailure { error = it.message ?: "Could not load requests" }
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
                error != null -> ErrorState(error!!, onRetry = { attempt++ })

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
                            onPlay = { play(task) },
                            onMarkDone = { markDone(task) },
                        )
                    }
                    // Clears the button so the last ticket is never trapped.
                    item { Spacer(Modifier.height(92.dp)) }
                }
            }
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
    onPlay: () -> Unit,
    onMarkDone: () -> Unit,
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

            if (task.hasVoiceNote || !closed) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (task.hasVoiceNote) {
                        TextButton(onClick = onPlay) {
                            Text(
                                if (isPlaying) "■ STOP" else "▶ VOICE NOTE",
                                style = HerpType.Action,
                                color = MaterialTheme.colorScheme.primary,
                            )
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
