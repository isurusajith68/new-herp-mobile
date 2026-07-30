package com.example.mobile_app_herp.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** What came back from a recording attempt. */
sealed interface RecordingResult {
    data class Saved(val file: File, val durationMs: Long) : RecordingResult
    /** Released too fast for MediaRecorder to finalise a playable file. */
    data object TooShort : RecordingResult
    data class Failed(val reason: String) : RecordingResult
}

/**
 * Records a short voice note to the app cache.
 *
 * AAC in an MP4 container (`.m4a`, `audio/mp4`) — the one combination every
 * Android since API 16 can both record and play, and it is on the API's audio
 * allowlist. Files land in cacheDir: they exist only until the task is saved and
 * uploaded, and the OS may reclaim them afterwards, which is exactly right.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt = 0L

    val mimeType: String get() = MIME_TYPE

    /** Milliseconds captured so far — the UI shows this so the mic is visibly live. */
    fun elapsedMs(): Long =
        if (recorder == null || startedAt == 0L) 0L else System.currentTimeMillis() - startedAt

    /** Throws if the mic can't be opened, so the caller can say why. */
    fun start() {
        discard()
        val file = File(context.cacheDir, "voice-note-${System.currentTimeMillis()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        // Speech, not music: 44.1 kHz mono at 64 kbps keeps a minute under 500 KB
        // on a hotel's uplink while staying perfectly intelligible.
        rec.setAudioChannels(1)
        rec.setAudioSamplingRate(44_100)
        rec.setAudioEncodingBitRate(64_000)
        rec.setOutputFile(file.absolutePath)
        try {
            rec.prepare()
            rec.start()
        } catch (e: Exception) {
            runCatching { rec.release() }
            file.delete()
            throw e
        }
        recorder = rec
        outputFile = file
        startedAt = System.currentTimeMillis()
    }

    /**
     * Stops and finalises the recording.
     *
     * The minimum is measured in TIME, not file size: an MP4 container writes a
     * header of a kilobyte or so before any audio, so a mis-tap can produce a
     * file that looks big enough and plays as silence. Below the floor we don't
     * even call stop() — MediaRecorder throws on a near-instant stop and leaves
     * a corrupt file behind.
     */
    fun stop(): RecordingResult {
        val rec = recorder ?: return RecordingResult.Failed("Nothing was recording")
        val file = outputFile
        val duration = elapsedMs()
        recorder = null
        outputFile = null
        startedAt = 0L

        if (duration < MIN_MS) {
            runCatching { rec.stop() }
            runCatching { rec.release() }
            file?.delete()
            return RecordingResult.TooShort
        }

        val stopped = runCatching { rec.stop() }.isSuccess
        runCatching { rec.release() }

        if (!stopped || file == null || !file.exists() || file.length() < MIN_BYTES) {
            file?.delete()
            return RecordingResult.TooShort
        }
        return RecordingResult.Saved(file, duration)
    }

    /** Abandons an in-flight recording and deletes the partial file. */
    fun cancel() = discard()

    private fun discard() {
        val rec = recorder
        recorder = null
        startedAt = 0L
        if (rec != null) {
            runCatching { rec.stop() }
            runCatching { rec.release() }
        }
        outputFile?.delete()
        outputFile = null
    }

    private companion object {
        const val MIME_TYPE = "audio/mp4"
        /** Under ~0.7s MediaRecorder cannot reliably finalise an MP4. */
        const val MIN_MS = 700L
        const val MIN_BYTES = 1_024L
    }
}

/**
 * One-at-a-time playback for both the local preview and remote (signed URL)
 * notes. A single player instance means starting one note always stops the
 * previous one, which is the behaviour a list of recordings needs.
 */
class VoicePlayer {
    private var player: MediaPlayer? = null

    /**
     * @param onDone called once, whether playback finished or failed.
     * @param onError called before [onDone] when there was nothing to hear, so
     *   the UI can say so. Silence with no message is the worst outcome here.
     */
    fun play(source: String, onError: (String) -> Unit = {}, onDone: () -> Unit) {
        stop()
        val mp = MediaPlayer()
        player = mp

        var settled = false
        fun finish(error: String? = null) {
            if (settled) return
            settled = true
            stop()
            if (error != null) onError(error)
            onDone()
        }

        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        mp.setOnCompletionListener { finish() }
        mp.setOnErrorListener { _, what, extra ->
            finish("Couldn't play that recording (code $what/$extra)")
            true
        }
        // ORDER MATTERS: the prepared listener must be attached BEFORE
        // prepareAsync. A local file prepares almost instantly, so registering
        // afterwards loses the callback and the note never starts — silence with
        // no error, which is exactly what it looks like from the outside.
        mp.setOnPreparedListener { it.start() }

        runCatching {
            mp.setDataSource(source)
            mp.prepareAsync()
        }.onFailure {
            finish("Couldn't open that recording")
        }
    }

    fun stop() {
        val mp = player ?: return
        player = null
        runCatching { if (mp.isPlaying) mp.stop() }
        runCatching { mp.reset() }
        runCatching { mp.release() }
    }
}
