package com.typetype.droid.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class AudioCaptureEngine {
    private val isRecording = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(onSamples: (FloatArray) -> Unit): Boolean {
        if (!isRecording.compareAndSet(false, true)) return false

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) {
            isRecording.set(false)
            return false
        }

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2,
            )
        } catch (_: SecurityException) {
            isRecording.set(false)
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            isRecording.set(false)
            return false
        }

        audioRecord = recorder
        recorder.startRecording()
        recordingThread = thread(name = "TypeTypeAudioCapture", isDaemon = true) {
            readLoop(recorder, onSamples)
        }
        return true
    }

    fun stop() {
        if (!isRecording.getAndSet(false)) return
        runCatching { audioRecord?.stop() }
        recordingThread?.join(500)
        recordingThread = null
        audioRecord?.release()
        audioRecord = null
    }

    private fun readLoop(recorder: AudioRecord, onSamples: (FloatArray) -> Unit) {
        val buffer = ShortArray(BUFFER_SAMPLES)
        while (isRecording.get()) {
            val read = recorder.read(buffer, 0, buffer.size)
            if (read > 0) {
                val samples = FloatArray(read) { index -> buffer[index] / 32768.0f }
                onSamples(samples)
            }
        }
    }

    companion object {
        const val SAMPLE_RATE = 16000
        private const val BUFFER_SAMPLES = 512
    }
}
