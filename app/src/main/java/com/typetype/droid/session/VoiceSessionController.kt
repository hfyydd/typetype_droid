package com.typetype.droid.session

import android.annotation.SuppressLint
import com.typetype.droid.asr.AsrEvent
import com.typetype.droid.asr.AsrEngine
import com.typetype.droid.asr.AsrEngineFactory
import com.typetype.droid.audio.AudioCaptureEngine
import com.typetype.droid.input.EditableInputConnection
import com.typetype.droid.input.InputCommitController

class VoiceSessionController(
    private val audioCaptureEngine: AudioCaptureEngine,
    private val asrEngineFactory: AsrEngineFactory,
    private val commitController: InputCommitController,
    private val onStateChanged: (VoiceSessionState) -> Unit = {},
) {
    var state: VoiceSessionState = VoiceSessionState()
        private set

    private var engine: AsrEngine? = null
    private var connection: EditableInputConnection? = null

    fun setMode(mode: DictationMode) {
        if (state.isActive) return
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
            SessionEvent.StartRequested -> startSession()
            SessionEvent.StopRequested -> stopSession()
            is SessionEvent.StreamingText -> commitController.writeStreaming(event.text)
            SessionEvent.StreamingSegmentFinished -> commitController.resetSession()
            is SessionEvent.OfflineText -> commitController.commitFinal(event.text)
            is SessionEvent.Error -> fail(event.message)
        }
    }

    private fun startSession() {
        if (state.isActive || connection == null) return
        update(state.copy(phase = VoiceSessionState.Phase.PREPARING, error = null))

        engine = try {
            asrEngineFactory.create(state.mode) { event ->
                handle(event.toSessionEvent(state.mode))
            }
        } catch (error: Throwable) {
            fail(error.message ?: "Unable to initialize ASR engine")
            return
        }

        @SuppressLint("MissingPermission")
        val started = audioCaptureEngine.start { samples ->
            engine?.acceptSamples(samples)
        }

        if (started) {
            update(state.copy(phase = VoiceSessionState.Phase.LISTENING))
        } else {
            fail("Unable to start microphone capture")
        }
    }

    private fun stopSession() {
        if (!state.isActive && state.phase != VoiceSessionState.Phase.ERROR) {
            commitController.detach()
            connection = null
            return
        }
        update(state.copy(phase = VoiceSessionState.Phase.STOPPING))
        audioCaptureEngine.stop()
        engine?.close()
        engine = null
        commitController.resetSession()
        commitController.detach()
        connection = null
        update(state.copy(phase = VoiceSessionState.Phase.IDLE, error = null))
    }

    private fun fail(message: String) {
        audioCaptureEngine.stop()
        engine?.close()
        engine = null
        commitController.resetSession()
        update(state.copy(phase = VoiceSessionState.Phase.ERROR, error = message))
    }

    private fun update(next: VoiceSessionState) {
        state = next
        onStateChanged(next)
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
}
