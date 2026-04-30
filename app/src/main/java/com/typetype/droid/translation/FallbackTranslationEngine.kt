package com.typetype.droid.translation

class FallbackTranslationEngine(
    private val primary: TranslationEngine,
    private val fallback: TranslationEngine,
) : TranslationEngine {
    override fun warmUp(targetLanguage: TranslationTargetLanguage) = primary.warmUp(targetLanguage)

    override fun translate(text: String, targetLanguage: TranslationTargetLanguage): String {
        return primary.translate(text, targetLanguage)
    }

    override fun close() {
        runCatching { primary.close() }
        runCatching { fallback.close() }
    }
}
