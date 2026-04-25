package com.typetype.droid

import android.Manifest
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import com.typetype.droid.asr.SherpaAsrEngineFactory
import com.typetype.droid.audio.AudioCaptureEngine
import com.typetype.droid.input.AndroidInputConnectionAdapter
import com.typetype.droid.input.InputCommitController
import com.typetype.droid.session.SessionEvent
import com.typetype.droid.session.VoiceSessionController
import com.typetype.droid.settings.VoiceImePreferences
import com.typetype.droid.ui.VoiceInputView

class VoiceImeService : InputMethodService() {
    private lateinit var inputView: VoiceInputView
    private lateinit var sessionController: VoiceSessionController
    private lateinit var preferences: VoiceImePreferences

    override fun onCreate() {
        super.onCreate()
        preferences = VoiceImePreferences(this)
        sessionController = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = SherpaAsrEngineFactory(application.assets),
            commitController = InputCommitController(),
            onStateChanged = { state -> inputViewOrNull()?.render(state) },
        )
        sessionController.setMode(preferences.loadMode())
    }

    override fun onCreateInputView(): View {
        inputView = VoiceInputView(this).apply {
            onMicClicked = { toggleListening() }
            onModeChanged = { mode ->
                sessionController.setMode(mode)
                preferences.saveMode(sessionController.state.mode)
            }
        }
        inputView.render(sessionController.state)
        return inputView
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        val adapter = currentInputConnection?.let(::AndroidInputConnectionAdapter)
        sessionController.handle(SessionEvent.InputStarted(adapter))
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        inputViewOrNull()?.render(sessionController.state)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        sessionController.handle(SessionEvent.InputFinished)
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        sessionController.handle(SessionEvent.InputFinished)
        super.onFinishInput()
    }

    private fun toggleListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            sessionController.handle(SessionEvent.Error("Microphone permission is missing"))
            return
        }

        if (sessionController.state.isActive) {
            sessionController.handle(SessionEvent.StopRequested)
        } else {
            val adapter = currentInputConnection?.let(::AndroidInputConnectionAdapter)
            sessionController.handle(SessionEvent.InputStarted(adapter))
            sessionController.handle(SessionEvent.StartRequested)
        }
    }

    private fun inputViewOrNull(): VoiceInputView? = if (::inputView.isInitialized) inputView else null
}
