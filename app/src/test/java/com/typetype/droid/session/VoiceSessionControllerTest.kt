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

    @Test
    fun externalClearKeepsSessionListeningAndDropsOldStreamingText() {
        val connection = FakeEditableInputConnection()
        val asrEngineFactory = FakeAsrEngineFactory()
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = asrEngineFactory,
            commitController = InputCommitController(),
        )

        controller.handle(SessionEvent.InputStarted(connection))
        controller.installEngineForTest(asrEngineFactory.engine)
        controller.setPhaseForTest(VoiceSessionState.Phase.LISTENING)
        controller.handle(SessionEvent.StreamingText("今天天气"))
        connection.clear()
        controller.handle(
            SessionEvent.EditorSelectionChanged(
                oldSelectionStart = 4,
                newSelectionStart = 0,
            ),
        )

        assertEquals("", connection.text)
        assertEquals(VoiceSessionState.Phase.LISTENING, controller.state.phase)
        assertEquals(1, asrEngineFactory.engine.resetCount)
    }

    @Test
    fun externalSelectionShorteningDropsOldTextEvenAfterStreamingSessionReset() {
        val connection = FakeEditableInputConnection()
        val asrEngineFactory = FakeAsrEngineFactory()
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = asrEngineFactory,
            commitController = InputCommitController(),
        )

        controller.handle(SessionEvent.InputStarted(connection))
        controller.installEngineForTest(asrEngineFactory.engine)
        controller.setPhaseForTest(VoiceSessionState.Phase.LISTENING)
        controller.handle(SessionEvent.StreamingText("今天天气"))
        controller.handle(SessionEvent.StreamingSegmentFinished)
        connection.clear()
        controller.handle(
            SessionEvent.EditorSelectionChanged(
                oldSelectionStart = 4,
                newSelectionStart = 0,
            ),
        )

        assertEquals("", connection.text)
        assertEquals(VoiceSessionState.Phase.LISTENING, controller.state.phase)
        assertEquals(1, asrEngineFactory.engine.resetCount)
    }

    @Test
    fun staleStreamingTextIsDroppedWhenFieldWasClearedWithoutSelectionEvent() {
        val connection = FakeEditableInputConnection()
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = FakeAsrEngineFactory(),
            commitController = InputCommitController(),
        )

        controller.handle(SessionEvent.InputStarted(connection))
        controller.handle(SessionEvent.StreamingText("今天天气"))
        controller.handle(SessionEvent.StreamingSegmentFinished)
        connection.clear()
        controller.handle(SessionEvent.StreamingText("今天天气不错"))

        assertEquals("", connection.text)
        assertEquals(VoiceSessionState.Phase.IDLE, controller.state.phase)
    }

    @Test
    fun freshSpeechAfterExternalClearContinuesWritingWithoutManualRestart() {
        val connection = FakeEditableInputConnection()
        val asrEngineFactory = FakeAsrEngineFactory()
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = asrEngineFactory,
            commitController = InputCommitController(),
        )

        controller.handle(SessionEvent.InputStarted(connection))
        controller.installEngineForTest(asrEngineFactory.engine)
        controller.handle(SessionEvent.StreamingText("今天天气"))
        connection.clear()
        controller.handle(SessionEvent.StreamingText("今天天气不错"))
        controller.handle(SessionEvent.StreamingText("下一句"))

        assertEquals("下一句", connection.text)
        assertEquals(1, asrEngineFactory.engine.resetCount)
    }
}

private class FakeAsrEngineFactory : AsrEngineFactory {
    val engine = FakeAsrEngine()

    override fun create(mode: DictationMode, onEvent: (AsrEvent) -> Unit): AsrEngine {
        return engine
    }
}

private class FakeAsrEngine : AsrEngine {
    var resetCount = 0
        private set

    override fun acceptSamples(samples: FloatArray) = Unit

    override fun reset() {
        resetCount += 1
    }

    override fun close() = Unit
}

private fun VoiceSessionController.installEngineForTest(engine: AsrEngine) {
    val field = VoiceSessionController::class.java.getDeclaredField("engine")
    field.isAccessible = true
    field.set(this, engine)
}

private fun VoiceSessionController.setPhaseForTest(phase: VoiceSessionState.Phase) {
    val field = VoiceSessionController::class.java.getDeclaredField("state")
    field.isAccessible = true
    field.set(this, state.copy(phase = phase))
}
