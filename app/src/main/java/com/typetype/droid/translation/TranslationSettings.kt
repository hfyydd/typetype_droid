package com.typetype.droid.translation

data class TranslationSettings(
    val outputMode: TranslationOutputMode = TranslationOutputMode.DICTATION,
    val backend: TranslationBackend = TranslationBackend.ML_KIT,
    val targetLanguage: TranslationTargetLanguage = TranslationTargetLanguage.ENGLISH,
)
