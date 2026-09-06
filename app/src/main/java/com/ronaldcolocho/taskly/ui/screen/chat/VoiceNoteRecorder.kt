package com.ronaldcolocho.taskly.ui.screen.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

data class RecordedVoiceNote(val file: File, val durationSeconds: Int)

class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    fun start(): Result<Unit> = runCatching {
        check(recorder == null) { "Ya hay una grabacion activa." }
        val directory = File(context.cacheDir, "voice_notes").apply { mkdirs() }
        val file = File(directory, "voice_${System.currentTimeMillis()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        output = file
        recorder = mediaRecorder
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setAudioEncodingBitRate(96_000)
        mediaRecorder.setAudioSamplingRate(44_100)
        mediaRecorder.setOutputFile(file.absolutePath)
        mediaRecorder.prepare()
        mediaRecorder.start()
        startedAt = System.currentTimeMillis()
    }.onFailure { cancel() }

    fun stop(): Result<RecordedVoiceNote> = runCatching {
        val active = checkNotNull(recorder) { "No hay una grabacion activa." }
        val file = checkNotNull(output)
        active.stop()
        val duration = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(1L).toInt()
        release()
        check(file.exists() && file.length() > 0L) { "La nota de voz esta vacia." }
        RecordedVoiceNote(file, duration)
    }.onFailure { cancel() }

    fun cancel() {
        runCatching { recorder?.stop() }
        release()
        output?.delete()
        output = null
    }

    private fun release() {
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
    }

}
