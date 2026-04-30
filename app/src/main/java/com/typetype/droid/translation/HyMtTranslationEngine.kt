package com.typetype.droid.translation

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class HyMtTranslationEngine(
    context: Context,
) : TranslationEngine {
    private val appContext = context.applicationContext
    private val engine: InferenceEngine = AiChat.getInferenceEngine(appContext)
    private val lock = Any()
    private var loadedModelPath: String? = null
    private var systemPromptConfigured = false
    private val modelCopied = AtomicBoolean(false)

    override fun warmUp(targetLanguage: TranslationTargetLanguage) {
        ensureModelLoaded()
    }

    override fun translate(text: String, targetLanguage: TranslationTargetLanguage): String {
        val normalized = text.trim()
        if (normalized.isEmpty()) return ""

        ensureModelLoaded()
        return runBlocking {
            val output = StringBuilder()
            engine.sendUserPrompt(buildUserPrompt(normalized, targetLanguage), predictLength = PREDICT_LENGTH)
                .collect { token ->
                    output.append(token)
                }
            output.toString().trim()
        }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { engine.destroy() }
            loadedModelPath = null
            systemPromptConfigured = false
            modelCopied.set(false)
        }
    }

    private fun ensureModelLoaded() {
        synchronized(lock) {
            if (loadedModelPath != null && systemPromptConfigured) return

            val modelFile = ensureBundledModelCopied()
            runBlocking {
                if (loadedModelPath != modelFile.absolutePath) {
                    runCatching { engine.cleanUp() }
                    engine.loadModel(modelFile.absolutePath)
                    loadedModelPath = modelFile.absolutePath
                    systemPromptConfigured = false
                }
                if (!systemPromptConfigured) {
                    engine.setSystemPrompt(SYSTEM_PROMPT)
                    systemPromptConfigured = true
                }
            }
        }
    }

    private fun ensureBundledModelCopied(): File {
        val targetDir = File(appContext.filesDir, MODEL_DIR_NAME).apply { mkdirs() }
        val targetFile = File(targetDir, MODEL_FILE_NAME)
        if (modelCopied.get() && targetFile.exists() && targetFile.length() > 0L) {
            return targetFile
        }
        synchronized(lock) {
            if (modelCopied.get() && targetFile.exists() && targetFile.length() > 0L) {
                return targetFile
            }

            if (targetFile.exists() && targetFile.length() > 0L) {
                modelCopied.set(true)
                return targetFile
            }

            appContext.assets.open(MODEL_ASSET_PATH).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            modelCopied.set(true)
            return targetFile
        }
    }

    private fun buildUserPrompt(
        text: String,
        targetLanguage: TranslationTargetLanguage,
    ): String {
        return buildString {
            append("Translate the following Simplified Chinese text into ")
            append(targetLanguage.promptLabel)
            append(". Output only the translated text with no explanation.\n\n")
            append(text)
        }
    }

    private companion object {
        const val MODEL_DIR_NAME = "translation-models"
        const val MODEL_FILE_NAME = "Hy-MT1.5-1.8B-2bit.gguf"
        const val MODEL_ASSET_PATH = "translation-models/Hy-MT1.5-1.8B-2bit.gguf"
        const val PREDICT_LENGTH = 256
        const val SYSTEM_PROMPT =
            "You are a professional translation engine. Translate faithfully and naturally. Return only the translation text."
    }
}
