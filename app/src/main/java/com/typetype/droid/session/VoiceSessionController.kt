package com.typetype.droid.session

import android.annotation.SuppressLint
import android.os.SystemClock
import android.util.Log
import com.typetype.droid.asr.AsrEvent
import com.typetype.droid.asr.AsrEngine
import com.typetype.droid.asr.AsrEngineFactory
import com.typetype.droid.audio.AudioCaptureEngine
import com.typetype.droid.input.EditableInputConnection
import com.typetype.droid.input.InputCommitController
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

class VoiceSessionController(
    private val audioCaptureEngine: AudioCaptureEngine,
    private val asrEngineFactory: AsrEngineFactory,
    private val commitController: InputCommitController,
    private val onStateChanged: (VoiceSessionState) -> Unit = {},
    private val backgroundExecutor: Executor = Executor { it.run() },
    private val stateExecutor: Executor = Executor { it.run() },
) {
    var state: VoiceSessionState = VoiceSessionState()
        private set

    private var engine: AsrEngine? = null
    private var connection: EditableInputConnection? = null
    private var preparedMode: DictationMode? = null
    private var preparingMode: DictationMode? = null
    private var asrEventGeneration = 0

    fun setMode(mode: DictationMode) {
        if (state.isActive || state.phase == VoiceSessionState.Phase.PREPARING) return
        update(state.copy(mode = mode, error = null))
    }

    fun handle(event: SessionEvent) {
        when (event) {
            is SessionEvent.InputStarted -> {
                connection = event.connection
                commitController.attach(event.connection)
                if (event.connection == null) {
                    stopSession()
                }
            }

            SessionEvent.InputFinished -> stopSession()
            SessionEvent.PrepareRequested -> prepareSession()
            SessionEvent.StartRequested -> startSession()
            SessionEvent.StopRequested -> stopSession()
            is SessionEvent.EditorSelectionChanged -> handleEditorSelectionChanged(event)
            is SessionEvent.StreamingText -> writeStreamingText(event.text)
            SessionEvent.StreamingSegmentFinished -> commitController.resetSession()
            is SessionEvent.OfflineText -> writeOfflineText(event.text)
            is SessionEvent.Error -> fail(event.message)
        }
    }

    private fun writeStreamingText(text: String) {
        if (shouldTreatAsExternalCommitBoundary()) {
            resetCurrentDictationSegment()
            return
        }
        commitController.writeStreaming(text)
    }

    private fun writeOfflineText(text: String) {
        if (shouldTreatAsExternalCommitBoundary()) {
            resetCurrentDictationSegment()
            return
        }
        commitController.commitFinal(text)
    }

    private fun shouldTreatAsExternalCommitBoundary(): Boolean {
        return commitController.hasWrittenOutput() && commitController.cursorIsAtStart()
    }

    private fun resetCurrentDictationSegment() {
        asrEventGeneration += 1
        commitController.resetAfterExternalCommit()
        engine?.reset()
    }

    private fun handleEditorSelectionChanged(event: SessionEvent.EditorSelectionChanged) {
        if (!state.isActive) return
        if (!commitController.hasWrittenOutput()) return
        val selectionMovedBackward = event.oldSelectionStart > 0 && event.newSelectionStart < event.oldSelectionStart
        val fieldLikelyClearedAfterSend = event.newSelectionStart == 0
        if (fieldLikelyClearedAfterSend || selectionMovedBackward || commitController.hasExternalChangeToStreamingText()) {
            resetCurrentDictationSegment()
        }
    }

    private fun prepareSession() {
        if (connection == null || engine != null || state.phase == VoiceSessionState.Phase.PREPARING) return
        val requestedMode = state.mode
        preparingMode = requestedMode
        update(state.copy(phase = VoiceSessionState.Phase.PREPARING, error = null))

        backgroundExecutor.execute prepareWork@{
            val startMs = SystemClock.elapsedRealtime()
            val nextEngine = try {
                asrEngineFactory.create(requestedMode, asrEventHandler(requestedMode))
            } catch (error: Throwable) {
                stateExecutor.execute {
                    if (preparingMode == requestedMode) {
                        preparingMode = null
                        fail(error.message ?: "Unable to initialize ASR engine")
                    }
                }
                return@prepareWork
            }
            val engineReadyMs = SystemClock.elapsedRealtime()
            Log.d(TAG, "prepareSession mode=$requestedMode engine=${engineReadyMs - startMs}ms")

            stateExecutor.execute {
                val canKeepPreparedEngine = state.phase == VoiceSessionState.Phase.PREPARING ||
                    state.phase == VoiceSessionState.Phase.STARTING ||
                    state.phase == VoiceSessionState.Phase.LISTENING
                if (connection == null || state.mode != requestedMode || !canKeepPreparedEngine) {
                    nextEngine.close()
                    if (preparingMode == requestedMode) preparingMode = null
                    return@execute
                }
                engine = nextEngine
                preparedMode = requestedMode
                preparingMode = null
                if (state.phase == VoiceSessionState.Phase.PREPARING) {
                    update(state.copy(phase = VoiceSessionState.Phase.READY, error = null))
                }
            }
        }
    }

    private fun startSession() {
        if (state.isActive || connection == null) return
        update(state.copy(phase = VoiceSessionState.Phase.STARTING, error = null))

        val requestedMode = state.mode
        backgroundExecutor.execute startWork@{
            val startMs = SystemClock.elapsedRealtime()
            val pendingSamples = ArrayDeque<FloatArray>()
            val pendingSamplesLock = Any()
            val liveEngine = AtomicReference<AsrEngine?>()
            engine?.takeIf { preparedMode == requestedMode }?.let(liveEngine::set)

            @SuppressLint("MissingPermission")
            val started = audioCaptureEngine.start { samples ->
                val targetEngine = liveEngine.get()
                if (targetEngine != null) {
                    targetEngine.acceptSamples(samples)
                } else {
                    synchronized(pendingSamplesLock) {
                        if (pendingSamples.size >= MAX_PENDING_AUDIO_CHUNKS) {
                            pendingSamples.removeFirst()
                        }
                        pendingSamples.addLast(samples.copyOf())
                    }
                }
            }
            val audioReadyMs = SystemClock.elapsedRealtime()

            stateExecutor.execute applyAudioStart@{
                if (state.phase != VoiceSessionState.Phase.STARTING) {
                    audioCaptureEngine.stop()
                    return@applyAudioStart
                }

                if (started) {
                    update(state.copy(phase = VoiceSessionState.Phase.LISTENING))
                } else {
                    fail("Unable to start microphone capture")
                }
            }
            if (!started) {
                Log.d(TAG, "startSession mode=$requestedMode audio=${audioReadyMs - startMs}ms started=false")
                return@startWork
            }

            val preparedEngine = liveEngine.get()
            val nextEngine = preparedEngine ?: try {
                asrEngineFactory.create(requestedMode, asrEventHandler(requestedMode))
            } catch (error: Throwable) {
                stateExecutor.execute {
                    if (state.phase == VoiceSessionState.Phase.STARTING || state.phase == VoiceSessionState.Phase.LISTENING) {
                        fail(error.message ?: "Unable to initialize ASR engine")
                    }
                }
                return@startWork
            }
            val engineReadyMs = SystemClock.elapsedRealtime()
            liveEngine.set(nextEngine)
            drainPendingSamples(pendingSamples, pendingSamplesLock, nextEngine)
            Log.d(
                TAG,
                "startSession mode=$requestedMode audio=${audioReadyMs - startMs}ms engine=${engineReadyMs - audioReadyMs}ms",
            )

            stateExecutor.execute applyEngine@{
                if (!state.isActive) {
                    if (preparedEngine == null) {
                        nextEngine.close()
                    }
                    return@applyEngine
                }
                engine = nextEngine
                preparedMode = requestedMode
            }
        }
    }

    private fun stopSession() {
        if (!state.isActive && state.phase != VoiceSessionState.Phase.ERROR) {
            val engineToClose = engine
            engine = null
            preparedMode = null
            asrEventGeneration += 1
            commitController.detach()
            connection = null
            if (state.phase != VoiceSessionState.Phase.IDLE) {
                update(state.copy(phase = VoiceSessionState.Phase.IDLE, error = null))
            }
            backgroundExecutor.execute {
                engineToClose?.close()
            }
            return
        }
        update(state.copy(phase = VoiceSessionState.Phase.STOPPING))
        val engineToClose = engine
        engine = null
        preparedMode = null
        preparingMode = null
        asrEventGeneration += 1
        commitController.resetSession()
        commitController.detach()
        connection = null
        backgroundExecutor.execute {
            audioCaptureEngine.stop()
            engineToClose?.close()
            stateExecutor.execute {
                if (state.phase == VoiceSessionState.Phase.STOPPING) {
                    update(state.copy(phase = VoiceSessionState.Phase.IDLE, error = null))
                }
            }
        }
    }

    private fun fail(message: String) {
        val engineToClose = engine
        engine = null
        preparedMode = null
        preparingMode = null
        asrEventGeneration += 1
        commitController.resetSession()
        backgroundExecutor.execute {
            audioCaptureEngine.stop()
            engineToClose?.close()
        }
        update(state.copy(phase = VoiceSessionState.Phase.ERROR, error = message))
    }

    private fun update(next: VoiceSessionState) {
        state = next
        onStateChanged(next)
    }

    private fun drainPendingSamples(
        pendingSamples: ArrayDeque<FloatArray>,
        pendingSamplesLock: Any,
        targetEngine: AsrEngine,
    ) {
        while (true) {
            val samples = synchronized(pendingSamplesLock) {
                if (pendingSamples.isEmpty()) null else pendingSamples.removeFirst()
            } ?: return
            targetEngine.acceptSamples(samples)
        }
    }

    private fun asrEventHandler(mode: DictationMode): (AsrEvent) -> Unit {
        return { event ->
            val eventGeneration = asrEventGeneration
            stateExecutor.execute {
                if (eventGeneration == asrEventGeneration) {
                    handle(event.toSessionEvent(mode))
                }
            }
        }
    }

    private fun AsrEvent.toSessionEvent(mode: DictationMode): SessionEvent {
        return when (this) {
            is AsrEvent.Text -> {
                if (mode == DictationMode.STREAMING) {
                    SessionEvent.StreamingText(value)
                } else {
                    SessionEvent.OfflineText(value)
                }
            }

            AsrEvent.SegmentFinished -> {
                if (mode == DictationMode.STREAMING) {
                    SessionEvent.StreamingSegmentFinished
                } else {
                    SessionEvent.OfflineText("")
                }
            }
        }
    }

    private companion object {
        const val TAG = "VoiceSessionController"
        const val MAX_PENDING_AUDIO_CHUNKS = 160
    }
}
