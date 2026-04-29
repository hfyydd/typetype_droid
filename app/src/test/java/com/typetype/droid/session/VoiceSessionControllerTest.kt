package com.typetype.droid.session

import com.typetype.droid.asr.AsrEngine
import com.typetype.droid.asr.AsrEvent
import com.typetype.droid.asr.AsrEngineFactory
import com.typetype.droid.audio.AudioCaptureEngine
import com.typetype.droid.input.FakeEditableInputConnection
import com.typetype.droid.input.InputCommitController
import com.typetype.droid.translation.TranslationEngine
import com.typetype.droid.translation.TranslationOutputMode
import com.typetype.droid.translation.TranslationSettings
import com.typetype.droid.translation.TranslationTargetLanguage
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

    @Test
    fun offlineTranslationCommitsTranslatedText() {
        val connection = FakeEditableInputConnection()
        val translationEngine = FakeTranslationEngine("hello world")
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = FakeAsrEngineFactory(),
            commitController = InputCommitController(),
            translationSettingsProvider = {
                TranslationSettings(
                    outputMode = TranslationOutputMode.TRANSLATION,
                    targetLanguage = TranslationTargetLanguage.ENGLISH,
                )
            },
            translationEngine = translationEngine,
        )

        controller.setMode(DictationMode.OFFLINE)
        controller.handle(SessionEvent.InputStarted(connection))
        controller.setPhaseForTest(VoiceSessionState.Phase.LISTENING)
        controller.handle(SessionEvent.OfflineText("今天天气不错"))

        assertEquals("hello world", connection.text)
        assertEquals("今天天气不错", translationEngine.lastInput)
        assertEquals(TranslationTargetLanguage.ENGLISH, translationEngine.lastLanguage)
        assertEquals(VoiceSessionState.Phase.LISTENING, controller.state.phase)
    }

    @Test
    fun translationOutputFailsInStreamingMode() {
        val connection = FakeEditableInputConnection()
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = FakeAsrEngineFactory(),
            commitController = InputCommitController(),
            translationSettingsProvider = {
                TranslationSettings(
                    outputMode = TranslationOutputMode.TRANSLATION,
                    targetLanguage = TranslationTargetLanguage.ENGLISH,
                )
            },
            translationEngine = FakeTranslationEngine("ignored"),
        )

        controller.handle(SessionEvent.InputStarted(connection))
        controller.setPhaseForTest(VoiceSessionState.Phase.LISTENING)
        controller.handle(SessionEvent.OfflineText("今天天气不错"))

        assertEquals("", connection.text)
        assertEquals("翻译输出仅支持稳妥模式", controller.state.error)
        assertEquals(VoiceSessionState.Phase.ERROR, controller.state.phase)
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

private class FakeTranslationEngine(
    private val output: String,
) : TranslationEngine {
    var lastInput: String? = null
        private set
    var lastLanguage: TranslationTargetLanguage? = null
        private set

    override fun warmUp(targetLanguage: TranslationTargetLanguage) = Unit

    override fun translate(text: String, targetLanguage: TranslationTargetLanguage): String {
        lastInput = text
        lastLanguage = targetLanguage
        return output
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
