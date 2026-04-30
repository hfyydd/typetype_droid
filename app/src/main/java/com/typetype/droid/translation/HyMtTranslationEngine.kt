package com.typetype.droid.translation

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.ModelLoadException
import com.arm.aichat.UnsupportedArchitectureException
import com.arm.aichat.gguf.GgufMetadataReader
import com.arm.aichat.gguf.InvalidFileFormatException
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class HyMtTranslationEngine(
    context: Context,
) : TranslationEngine {
    private val appContext = context.applicationContext
    private val engine: InferenceEngine = AiChat.getInferenceEngine(appContext)
    private val ggufReader = GgufMetadataReader.create()
    private val lock = Any()
    private var loadedModelPath: String? = null
    private val modelCopied = AtomicBoolean(false)

    override fun warmUp(targetLanguage: TranslationTargetLanguage) {
        try {
            ensureModelLoaded()
        } catch (error: Throwable) {
            throw wrapHyMtError("warm-up", error)
        }
    }

    override fun translate(text: String, targetLanguage: TranslationTargetLanguage): String {
        val normalized = text.trim()
        if (normalized.isEmpty()) return ""

        try {
            ensureModelLoaded()
            return runBlocking {
                val output = StringBuilder()
                engine.sendUserPrompt(buildUserPrompt(normalized, targetLanguage), predictLength = PREDICT_LENGTH)
                    .collect { token ->
                        output.append(token)
                    }
                output.toString().trim().ifEmpty {
                    throw IOException("HY-MT generated empty output")
                }
            }
        } catch (error: Throwable) {
            throw wrapHyMtError("translation", error)
        }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { engine.destroy() }
            loadedModelPath = null
            modelCopied.set(false)
        }
    }

    private fun ensureModelLoaded() {
        synchronized(lock) {
            if (loadedModelPath != null) return

            val modelFile = ensureBundledModelCopied()
            validateModelFile(modelFile)
            runBlocking {
                awaitEngineInitialized()
                if (loadedModelPath != modelFile.absolutePath) {
                    runCatching { engine.cleanUp() }
                    engine.loadModel(modelFile.absolutePath)
                    loadedModelPath = modelFile.absolutePath
                }
            }
        }
    }

    private fun ensureBundledModelCopied(): File {
        val targetDir = File(appContext.filesDir, MODEL_DIR_NAME).apply { mkdirs() }
        val targetFile = File(targetDir, MODEL_FILE_NAME)
        if (modelCopied.get() && isUsableModelFile(targetFile)) {
            return targetFile
        }
        synchronized(lock) {
            if (modelCopied.get() && isUsableModelFile(targetFile)) {
                return targetFile
            }

            if (isUsableModelFile(targetFile)) {
                modelCopied.set(true)
                return targetFile
            }

            val tempFile = File(targetDir, "$MODEL_FILE_NAME.tmp")
            tempFile.delete()
            appContext.assets.open(MODEL_ASSET_PATH).use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (targetFile.exists() && !targetFile.delete()) {
                tempFile.delete()
                throw IOException("Failed to replace stale HY-MT model file")
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.delete()
                throw IOException("Failed to finalize HY-MT model copy")
            }
            modelCopied.set(true)
            return targetFile
        }
    }

    private suspend fun awaitEngineInitialized() {
        val state = engine.state
            .filter {
                it is InferenceEngine.State.Initialized ||
                    it is InferenceEngine.State.ModelReady ||
                    it is InferenceEngine.State.Error
            }
            .first()

        if (state is InferenceEngine.State.Error) {
            throw state.exception
        }
    }

    private fun isUsableModelFile(file: File): Boolean {
        return file.exists() && file.isFile && file.length() == MODEL_EXPECTED_SIZE_BYTES
    }

    private fun validateModelFile(file: File) {
        if (file.length() != MODEL_EXPECTED_SIZE_BYTES) {
            throw IOException(
                "HY-MT model size mismatch: expected=$MODEL_EXPECTED_SIZE_BYTES actual=${file.length()}",
            )
        }
        runBlocking {
            try {
                ggufReader.ensureSourceFileFormat(file)
            } catch (_: InvalidFileFormatException) {
                throw IOException("HY-MT model is not a valid GGUF file")
            }
        }
    }

    private fun buildUserPrompt(
        text: String,
        targetLanguage: TranslationTargetLanguage,
    ): String {
        return buildString {
            append("将以下文本翻译为")
            append(targetLanguage.hyMtTargetLabel)
            append("，注意只需要输出翻译后的结果，不要额外解释：\n\n")
            append(text)
        }
    }

    private fun wrapHyMtError(
        phase: String,
        error: Throwable,
    ): RuntimeException {
        val detail = when (error) {
            is UnsupportedArchitectureException -> "device architecture is unsupported"
            is ModelLoadException -> "native model load returned code=${error.code}"
            else -> error.message ?: error.javaClass.simpleName
        }
        return RuntimeException("HY-MT $phase failed: $detail", error)
    }

    private companion object {
        const val MODEL_DIR_NAME = "translation-models"
        const val MODEL_FILE_NAME = "Hy-MT1.5-1.8B-2bit.gguf"
        const val MODEL_ASSET_PATH = "translation-models/Hy-MT1.5-1.8B-2bit.gguf"
        const val MODEL_EXPECTED_SIZE_BYTES = 600_534_880L
        const val PREDICT_LENGTH = 512
    }
}
