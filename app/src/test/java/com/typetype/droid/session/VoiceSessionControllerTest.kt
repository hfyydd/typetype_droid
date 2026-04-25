package com.typetype.droid.session

import com.typetype.droid.asr.AsrEngine
import com.typetype.droid.asr.AsrEvent
import com.typetype.droid.asr.AsrEngineFactory
import com.typetype.droid.audio.AudioCaptureEngine
import com.typetype.droid.input.FakeEditableInputConnection
import com.typetype.droid.input.InputCommitController
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSessionControllerTest {
    @Test
    fun focusLossStopsSessionAndDropsConnection() {
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = FakeAsrEngineFactory(),
            commitController = InputCommitController(),
        )

        controller.handle(SessionEvent.InputStarted(FakeEditableInputConnection()))
        controller.handle(SessionEvent.InputFinished)

        assertEquals(VoiceSessionState.Phase.IDLE, controller.state.phase)
    }

    @Test
    fun modeDoesNotChangeDuringActiveSession() {
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = FakeAsrEngineFactory(),
            commitController = InputCommitController(),
        )

        controller.setMode(DictationMode.OFFLINE)

        assertEquals(DictationMode.OFFLINE, controller.state.mode)
    }
}

private class FakeAsrEngineFactory : AsrEngineFactory {
    override fun create(mode: DictationMode, onEvent: (AsrEvent) -> Unit): AsrEngine {
        return object : AsrEngine {
            override fun acceptSamples(samples: FloatArray) = Unit
            override fun close() = Unit
        }
    }
}
