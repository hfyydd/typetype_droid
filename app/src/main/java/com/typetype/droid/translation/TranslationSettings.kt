package com.typetype.droid.translation

data class TranslationSettings(
    val outputMode: TranslationOutputMode = TranslationOutputMode.DICTATION,
    val backend: TranslationBackend = TranslationBackend.HY_MT,
    val targetLanguage: TranslationTargetLanguage = TranslationTargetLanguage.ENGLISH,
)
