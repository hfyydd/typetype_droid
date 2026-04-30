package com.typetype.droid

import android.app.Application
import com.typetype.droid.asr.SherpaAsrEngineFactory
import com.typetype.droid.session.DictationMode
import com.typetype.droid.settings.VoiceImePreferences
import com.typetype.droid.translation.FallbackTranslationEngine
import com.typetype.droid.translation.HyMtTranslationEngine
import com.typetype.droid.translation.TranslationEngine
import com.typetype.droid.translation.MlKitTranslationEngine
import com.typetype.droid.translation.TranslationOutputMode
import com.typetype.droid.translation.TranslationTargetLanguage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TypeTypeApplication : Application() {
    lateinit var asrEngineFactory: SherpaAsrEngineFactory
        private set
    lateinit var translationEngine: TranslationEngine
        private set

    private val preloadExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "TypeTypeModelPreload").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    override fun onCreate() {
        super.onCreate()
        asrEngineFactory = SherpaAsrEngineFactory(assets)
        translationEngine = FallbackTranslationEngine(
            primary = HyMtTranslationEngine(this),
            fallback = MlKitTranslationEngine(),
        )
        val preferences = VoiceImePreferences(this)
        warmUpAsr(preferences.loadEffectiveMode())
        val translationSettings = preferences.loadTranslationSettings()
        if (translationSettings.outputMode == TranslationOutputMode.TRANSLATION) {
            warmUpTranslation(translationSettings.targetLanguage)
        }
    }

    fun warmUpAsr(mode: DictationMode) {
        preloadExecutor.execute {
            runCatching {
                asrEngineFactory.warmUp(mode)
            }
        }
    }

    fun warmUpTranslation(targetLanguage: TranslationTargetLanguage) {
        preloadExecutor.execute {
            runCatching {
                translationEngine.warmUp(targetLanguage)
            }
        }
    }

    fun close() {
        preloadExecutor.shutdownNow()
        translationEngine.close()
        asrEngineFactory.close()
    }
}
