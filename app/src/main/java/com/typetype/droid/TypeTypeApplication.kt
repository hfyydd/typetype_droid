package com.typetype.droid

import android.app.Application
import com.typetype.droid.asr.SherpaAsrEngineFactory
import com.typetype.droid.session.DictationMode
import com.typetype.droid.settings.VoiceImePreferences
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TypeTypeApplication : Application() {
    lateinit var asrEngineFactory: SherpaAsrEngineFactory
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
        warmUpAsr(VoiceImePreferences(this).loadMode())
    }

    fun warmUpAsr(mode: DictationMode) {
        preloadExecutor.execute {
            runCatching {
                asrEngineFactory.warmUp(mode)
            }
        }
    }
}
