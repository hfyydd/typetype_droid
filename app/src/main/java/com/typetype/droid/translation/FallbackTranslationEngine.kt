package com.typetype.droid.translation

class FallbackTranslationEngine(
    private val primary: TranslationEngine,
    private val fallback: TranslationEngine,
) : TranslationEngine {
    override fun warmUp(targetLanguage: TranslationTargetLanguage) {
        runCatching {
            primary.warmUp(targetLanguage)
        }.getOrElse {
            fallback.warmUp(targetLanguage)
        }
    }

    override fun translate(text: String, targetLanguage: TranslationTargetLanguage): String {
        return runCatching {
            primary.translate(text, targetLanguage)
        }.getOrElse { primaryError ->
            runCatching {
                fallback.translate(text, targetLanguage)
            }.getOrElse { fallbackError ->
                fallbackError.addSuppressed(primaryError)
                throw fallbackError
            }
        }
    }

    override fun close() {
        runCatching { primary.close() }
        runCatching { fallback.close() }
    }
}
