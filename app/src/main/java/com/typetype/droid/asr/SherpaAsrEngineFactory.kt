package com.typetype.droid.asr

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.getEndpointConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.k2fsa.sherpa.onnx.getModelConfig
import com.k2fsa.sherpa.onnx.getOfflineModelConfig
import com.k2fsa.sherpa.onnx.getVadModelConfig
import com.typetype.droid.audio.AudioCaptureEngine
import com.typetype.droid.session.DictationMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SherpaAsrEngineFactory(
    private val assetManager: AssetManager,
) : AsrEngineFactory {
    private val lock = Any()
    private var streamingRecognizer: OnlineRecognizer? = null
    private var offlineRecognizer: OfflineRecognizer? = null
    private var vad: Vad? = null
    private var closed = false

    override fun create(mode: DictationMode, onEvent: (AsrEvent) -> Unit): AsrEngine {
        check(!closed) { "ASR engine factory is closed" }
        return when (mode) {
            DictationMode.STREAMING -> SherpaStreamingAsrEngine(
                recognizer = streamingRecognizer(),
                onEvent = onEvent,
            )

            DictationMode.OFFLINE -> SherpaOfflineAsrEngine(
                recognizer = offlineRecognizer(),
                vad = vad(),
                vadLock = lock,
                onEvent = onEvent,
            )
        }
    }

    fun warmUp(mode: DictationMode) {
        check(!closed) { "ASR engine factory is closed" }
        when (mode) {
            DictationMode.STREAMING -> streamingRecognizer()
            DictationMode.OFFLINE -> {
                offlineRecognizer()
                vad()
            }
        }
    }

    fun close() {
        synchronized(lock) {
            closed = true
            streamingRecognizer?.release()
            streamingRecognizer = null
            offlineRecognizer?.release()
            offlineRecognizer = null
            vad?.release()
            vad = null
        }
    }

    private fun streamingRecognizer(): OnlineRecognizer {
        synchronized(lock) {
            return streamingRecognizer ?: OnlineRecognizer(
                assetManager = assetManager,
                config = OnlineRecognizerConfig(
                    featConfig = getFeatureConfig(AudioCaptureEngine.SAMPLE_RATE, featureDim = 80),
                    modelConfig = getModelConfig(STREAMING_ZH_MODEL_TYPE)
                        ?: error("Missing streaming sherpa-onnx model config"),
                    endpointConfig = getEndpointConfig(),
                    enableEndpoint = true,
                ),
            ).also { streamingRecognizer = it }
        }
    }

    private fun offlineRecognizer(): OfflineRecognizer {
        synchronized(lock) {
            return offlineRecognizer ?: OfflineRecognizer(
                assetManager = assetManager,
                config = OfflineRecognizerConfig(
                    featConfig = getFeatureConfig(AudioCaptureEngine.SAMPLE_RATE, featureDim = 80),
                    modelConfig = getOfflineModelConfig(OFFLINE_ZH_MODEL_TYPE)
                        ?: error("Missing offline sherpa-onnx model config"),
                ),
            ).also { offlineRecognizer = it }
        }
    }

    private fun vad(): Vad {
        synchronized(lock) {
            return vad ?: Vad(
                assetManager = assetManager,
                config = getVadModelConfig(VAD_MODEL_TYPE) ?: error("Missing VAD model config"),
            ).also { vad = it }
        }
    }
}

private class SherpaStreamingAsrEngine(
    private val recognizer: OnlineRecognizer,
    private val onEvent: (AsrEvent) -> Unit,
) : AsrEngine {
    private var stream = recognizer.createStream()
    private val closed = AtomicBoolean(false)

    override fun acceptSamples(samples: FloatArray) {
        if (closed.get()) return
        stream.acceptWaveform(samples, AudioCaptureEngine.SAMPLE_RATE)
        while (recognizer.isReady(stream)) {
            recognizer.decode(stream)
        }
        val result = recognizer.getResult(stream).text
        if (result.isNotBlank()) {
            onEvent(AsrEvent.Text(result))
        }
        if (recognizer.isEndpoint(stream)) {
            recognizer.reset(stream)
            onEvent(AsrEvent.SegmentFinished)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        stream.release()
    }
}

private class SherpaOfflineAsrEngine(
    private val recognizer: OfflineRecognizer,
    private val vad: Vad,
    private val vadLock: Any,
    private val onEvent: (AsrEvent) -> Unit,
) : AsrEngine {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(false)

    override fun acceptSamples(samples: FloatArray) {
        if (closed.get()) return
        val segments = synchronized(vadLock) {
            vad.acceptWaveform(samples)
            buildList {
                while (!vad.empty()) {
                    add(vad.front())
                    vad.pop()
                }
            }
        }
        segments.forEach { segment ->
            executor.execute {
                if (!closed.get()) {
                    decodeSegment(segment.samples)
                }
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.shutdownNow()
        synchronized(vadLock) {
            vad.clear()
            vad.reset()
        }
    }

    private fun decodeSegment(samples: FloatArray) {
        val stream = recognizer.createStream()
        stream.acceptWaveform(samples, AudioCaptureEngine.SAMPLE_RATE)
        recognizer.decode(stream)
        val text = recognizer.getResult(stream).text
        stream.release()
        if (!closed.get() && text.isNotBlank()) {
            onEvent(AsrEvent.Text(text))
            onEvent(AsrEvent.SegmentFinished)
        }
    }
}

private const val STREAMING_ZH_MODEL_TYPE = 9
private const val OFFLINE_ZH_MODEL_TYPE = 0
private const val VAD_MODEL_TYPE = 0
