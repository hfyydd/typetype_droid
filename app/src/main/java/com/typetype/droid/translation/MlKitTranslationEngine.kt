package com.typetype.droid.translation

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

class MlKitTranslationEngine : TranslationEngine {
    private val lock = Any()
    private val translators = mutableMapOf<TranslationTargetLanguage, Translator>()

    override fun warmUp(targetLanguage: TranslationTargetLanguage) {
        val translator = translatorFor(targetLanguage)
        Tasks.await(translator.downloadModelIfNeeded())
    }

    override fun translate(text: String, targetLanguage: TranslationTargetLanguage): String {
        val normalized = text.trim()
        if (normalized.isEmpty()) return ""

        val translator = translatorFor(targetLanguage)
        Tasks.await(translator.downloadModelIfNeeded())
        return Tasks.await(translator.translate(normalized)).trim()
    }

    override fun close() {
        synchronized(lock) {
            translators.values.forEach { it.close() }
            translators.clear()
        }
    }

    private fun translatorFor(targetLanguage: TranslationTargetLanguage): Translator {
        synchronized(lock) {
            return translators.getOrPut(targetLanguage) {
                Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(TranslateLanguage.CHINESE)
                        .setTargetLanguage(targetLanguage.mlKitCode)
                        .build(),
                )
            }
        }
    }
}
