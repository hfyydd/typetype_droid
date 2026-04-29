package com.typetype.droid.translation

interface TranslationEngine {
    fun warmUp(targetLanguage: TranslationTargetLanguage)

    fun translate(text: String, targetLanguage: TranslationTargetLanguage): String

    fun close()
}
