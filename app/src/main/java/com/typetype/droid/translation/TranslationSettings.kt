package com.typetype.droid.translation

data class TranslationSettings(
    val outputMode: TranslationOutputMode = TranslationOutputMode.DICTATION,
    val targetLanguage: TranslationTargetLanguage = TranslationTargetLanguage.ENGLISH,
)
