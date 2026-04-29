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

    private val shortBufferPool = object : ThreadLocal<ShortArray>() {
        override fun initialValue(): ShortArray = ShortArray(BUFFER_SAMPLES)
    }
    private val floatBufferPool = object : ThreadLocal<FloatArray>() {
        override fun initialValue(): FloatArray = FloatArray(BUFFER_SAMPLES)
    }

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
        val shortBuffer = shortBufferPool.get()!!
        val floatBuffer = floatBufferPool.get()!!
        while (isRecording.get()) {
            val read = recorder.read(shortBuffer, 0, shortBuffer.size)
            if (read > 0) {
                for (i in 0 until read) {
                    floatBuffer[i] = shortBuffer[i] / 32768.0f
                }
                onSamples(floatBuffer)
            }
        }
    }

    companion object {
        const val SAMPLE_RATE = 16000
        private const val BUFFER_SAMPLES = 512
    }
}
