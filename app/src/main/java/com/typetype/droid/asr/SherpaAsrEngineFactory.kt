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
    override fun create(mode: DictationMode, onEvent: (AsrEvent) -> Unit): AsrEngine {
        return when (mode) {
            DictationMode.STREAMING -> SherpaStreamingAsrEngine(assetManager, onEvent)
            DictationMode.OFFLINE -> SherpaOfflineAsrEngine(assetManager, onEvent)
        }
    }
}

private class SherpaStreamingAsrEngine(
    assetManager: AssetManager,
    private val onEvent: (AsrEvent) -> Unit,
) : AsrEngine {
    private val recognizer = OnlineRecognizer(
        assetManager = assetManager,
        config = OnlineRecognizerConfig(
            featConfig = getFeatureConfig(AudioCaptureEngine.SAMPLE_RATE, featureDim = 80),
            modelConfig = getModelConfig(STREAMING_ZH_MODEL_TYPE)
                ?: error("Missing streaming sherpa-onnx model config"),
            endpointConfig = getEndpointConfig(),
            enableEndpoint = true,
        ),
    )
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
        recognizer.release()
    }
}

private class SherpaOfflineAsrEngine(
    assetManager: AssetManager,
    private val onEvent: (AsrEvent) -> Unit,
) : AsrEngine {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(false)
    private val vad = Vad(
        assetManager = assetManager,
        config = getVadModelConfig(VAD_MODEL_TYPE) ?: error("Missing VAD model config"),
    )
    private val recognizer = OfflineRecognizer(
        assetManager = assetManager,
        config = OfflineRecognizerConfig(
            featConfig = getFeatureConfig(AudioCaptureEngine.SAMPLE_RATE, featureDim = 80),
            modelConfig = getOfflineModelConfig(OFFLINE_ZH_MODEL_TYPE)
                ?: error("Missing offline sherpa-onnx model config"),
        ),
    )

    override fun acceptSamples(samples: FloatArray) {
        if (closed.get()) return
        vad.acceptWaveform(samples)
        while (!vad.empty()) {
            val segment = vad.front()
            vad.pop()
            executor.execute {
                if (!closed.get()) {
                    decodeSegment(segment.samples)
                }
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        vad.flush()
        executor.shutdownNow()
        vad.release()
        recognizer.release()
    }

    private fun decodeSegment(samples: FloatArray) {
        val stream = recognizer.createStream()
        stream.acceptWaveform(samples, AudioCaptureEngine.SAMPLE_RATE)
        recognizer.decode(stream)
        val text = recognizer.getResult(stream).text
        stream.release()
        if (text.isNotBlank()) {
            onEvent(AsrEvent.Text(text))
            onEvent(AsrEvent.SegmentFinished)
        }
    }
}

private const val STREAMING_ZH_MODEL_TYPE = 9
private const val OFFLINE_ZH_MODEL_TYPE = 0
private const val VAD_MODEL_TYPE = 0
