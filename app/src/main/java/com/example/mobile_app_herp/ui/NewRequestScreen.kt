package com.example.mobile_app_herp.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mobile_app_herp.data.Assignee
import com.example.mobile_app_herp.data.Herp
import com.example.mobile_app_herp.data.Property
import com.example.mobile_app_herp.data.RecordingResult
import com.example.mobile_app_herp.data.VoicePlayer
import com.example.mobile_app_herp.data.VoiceRecorder
import com.example.mobile_app_herp.ui.theme.HerpType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** `0:07` — seconds are the only unit that matters for a spoken note. */
private fun clock(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

private fun discardRecordingFile(file: File?) {
    file?.delete()
}

/**
 * Raising a request. The voice note is why this screen exists on a phone: a
 * manager crossing the yard says a sentence faster than they type it, and the
 * store keeper hears the emphasis that plain text loses.
 */
@Composable
fun NewRequestScreen(
    property: Property,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(context) }
    val player = remember { VoicePlayer() }

    var text by rememberSaveable { mutableStateOf("") }
    var assignees by remember { mutableStateOf<List<Assignee>>(emptyList()) }
    var assignee by remember { mutableStateOf<Assignee?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var recorded by remember { mutableStateOf<File?>(null) }
    var recordedMs by remember { mutableStateOf(0L) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    // A transcription the model wasn't sure of, waiting to be read and accepted
    // or rejected. Never written into the request box behind the user's back.
    var review by remember { mutableStateOf<Transcription?>(null) }

    // A live counter is the only proof the mic is actually capturing. Without it,
    // a failed recording and a working one look identical until playback.
    LaunchedEffect(recording) {
        while (recording) {
            elapsedMs = recorder.elapsedMs()
            delay(100)
        }
    }

    // Loaded quietly: an empty list means the request stays unassigned, which is
    // valid — a failed lookup must never block filing work.
    LaunchedEffect(property.slug) {
        runCatching { Herp.client.assignees(property.slug) }.onSuccess { assignees = it }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder.cancel()
            player.stop()
        }
    }

    fun beginRecording() {
        error = null
        discardRecordingFile(recorded)
        recorded = null
        recordedMs = 0L
        elapsedMs = 0L
        runCatching { recorder.start() }
            .onSuccess { recording = true }
            .onFailure { error = "The microphone is busy. Close other apps using it and try again." }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) beginRecording()
        else error = "Recording needs microphone access. Grant it in Settings, or type the request instead."
    }

    fun toggleRecording() {
        if (recording) {
            recording = false
            when (val result = recorder.stop()) {
                is RecordingResult.Saved -> {
                    recorded = result.file
                    recordedMs = result.durationMs
                }
                RecordingResult.TooShort ->
                    error = "Too short to keep. Tap record, speak, then tap stop."
                is RecordingResult.Failed -> error = result.reason
            }
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) beginRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun togglePlayback() {
        val file = recorded ?: return
        if (playing) {
            player.stop()
            playing = false
        } else {
            playing = true
            error = null
            player.play(
                source = file.absolutePath,
                onError = { error = it },
                onDone = { playing = false },
            )
        }
    }

    /**
     * Sends the recording for transcription and writes the result into the text
     * box. It APPENDS rather than replaces: someone who typed a line and then
     * spoke the rest should not lose the typed part, and the box stays editable
     * so a mis-heard word can be fixed before saving.
     */
    fun append(spoken: String) {
        text = if (text.isBlank()) spoken else "${text.trimEnd()} $spoken"
    }

    /**
     * Sends the recording for transcription.
     *
     * A CONFIDENT result goes straight into the box — that is the whole point of
     * dictating. Anything doubtful does not: it is held in [review] for the person
     * to read first. Silently inserting a half-heard sentence is how someone ends
     * up acting on a request that names the wrong item.
     */
    fun transcribe() {
        val file = recorded ?: return
        if (transcribing) return
        transcribing = true
        error = null
        hint = null
        review = null
        scope.launch {
            runCatching { Herp.client.transcribeVoice(property.slug, file, recorder.mimeType) }
                .onSuccess { spoken ->
                    transcribing = false
                    when {
                        spoken.isEmpty ->
                            hint = "No speech in that recording. Try again, closer to the mic."
                        spoken.isTrustworthy -> append(spoken.english)
                        else -> review = spoken
                    }
                }
                .onFailure {
                    transcribing = false
                    error = it.message ?: "Couldn't turn that recording into text"
                }
        }
    }

    fun discardRecording() {
        player.stop()
        playing = false
        discardRecordingFile(recorded)
        recorded = null
        recordedMs = 0L
        elapsedMs = 0L
    }

    fun save() {
        if (text.isBlank() || saving) return
        saving = true
        error = null
        scope.launch {
            runCatching {
                val noteKey = recorded?.let {
                    Herp.client.uploadVoiceNote(property.slug, it, recorder.mimeType)
                }
                Herp.client.createTask(property.slug, text.trim(), noteKey, assignee?.id)
            }.onSuccess {
                saving = false
                recorded?.delete()
                onSaved()
            }.onFailure {
                saving = false
                error = it.message ?: "The request didn't save. Check your connection and try again."
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = Gutter),
    ) {
        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = onCancel,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text("← REQUESTS", style = HerpType.Action, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
        ScreenHeader(
            eyebrow = property.name,
            title = "New request",
        )

        Spacer(Modifier.height(24.dp))

        Field(
            value = text,
            onValueChange = { text = it; error = null },
            label = "What needs doing",
            placeholder = "Count the cold room stock before the delivery",
            enabled = !saving,
            keyboardType = KeyboardType.Text,
            minLines = 3,
        )

        Spacer(Modifier.height(16.dp))

        // ── Assign ────────────────────────────────────────────────────────────
        SpineCard(spine = MaterialTheme.colorScheme.primary) {
            Column(Modifier.fillMaxWidth()) {
                Stamp("Assign to", MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Box {
                    OutlinedButton(
                        onClick = { pickerOpen = true },
                        enabled = !saving && assignees.isNotEmpty(),
                        shape = CardShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (assignee != null) {
                                KeyTag(assignee!!.fullName, size = 24)
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(
                                assignee?.fullName ?: "Anyone",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text("▾", style = HerpType.Title, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Anyone", style = MaterialTheme.typography.bodyLarge) },
                            onClick = { assignee = null; pickerOpen = false },
                        )
                        assignees.forEach { person ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(person.fullName, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            person.email,
                                            style = HerpType.Record,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = { assignee = person; pickerOpen = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        assignees.isEmpty() -> "Nobody else has inventory access here yet."
                        assignee == null -> "Leave it open and anyone on the floor can pick it up."
                        else -> assignee!!.email
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Voice ─────────────────────────────────────────────────────────────
        SpineCard(
            spine = if (recording) MaterialTheme.colorScheme.error
            else if (recorded != null) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Stamp(
                        if (recording) "Recording" else "Voice message",
                        if (recording) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    // The clock is the feedback that was missing: you can see the
                    // mic running, and you can see how long you captured.
                    val shown = if (recording) elapsedMs else recordedMs
                    if (shown > 0L) {
                        Text(
                            clock(shown),
                            style = HerpType.Record,
                            color = if (recording) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = ::toggleRecording,
                        enabled = !saving,
                        shape = CardShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (recording) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            when {
                                recording -> "■ STOP"
                                recorded != null -> "● RECORD AGAIN"
                                else -> "● RECORD"
                            },
                            style = HerpType.Action,
                        )
                    }
                    if (recorded != null && !recording) {
                        OutlinedButton(onClick = ::togglePlayback, enabled = !saving, shape = CardShape) {
                            Text(
                                if (playing) "■ STOP" else "▶ PLAY",
                                style = HerpType.Action,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        TextButton(onClick = ::discardRecording, enabled = !saving) {
                            Text(
                                "REMOVE",
                                style = HerpType.Action,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Turning speech into text is the reason to record at all for
                // someone who cannot type quickly, so it gets its own full-width
                // action rather than hiding among the playback controls.
                if (recorded != null && !recording) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = ::transcribe,
                        enabled = !saving && !transcribing,
                        shape = CardShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (transcribing) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "LISTENING…",
                                style = HerpType.Action,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                "WRITE IT OUT FOR ME",
                                style = HerpType.Action,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        recording -> "Speak now. Tap stop when you're finished."
                        hint != null -> hint!!
                        recorded != null ->
                            "Attached. Play it back, or have it written into the box above."
                        else -> "Optional. Say the detail instead of typing it."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hint != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Notice(error!!, label = "Not saved")
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = ::save,
            enabled = text.isNotBlank() && !saving && !recording,
            shape = CardShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (saving) {
                CircularProgressIndicator(
                    Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("RAISE REQUEST", style = HerpType.Action)
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}
