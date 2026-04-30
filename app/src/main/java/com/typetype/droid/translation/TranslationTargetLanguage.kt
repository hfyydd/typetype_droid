package com.typetype.droid.translation

import com.google.mlkit.nl.translate.TranslateLanguage

enum class TranslationTargetLanguage(
    val label: String,
    val promptLabel: String,
    val mlKitCode: String?,
) {
    ENGLISH(
        label = "英语",
        promptLabel = "English",
        mlKitCode = TranslateLanguage.ENGLISH,
    ),
    JAPANESE(
        label = "日语",
        promptLabel = "Japanese",
        mlKitCode = TranslateLanguage.JAPANESE,
    ),
    GERMAN(
        label = "德语",
        promptLabel = "German",
        mlKitCode = TranslateLanguage.GERMAN,
    ),
    CANTONESE(
        label = "粤语（实验性）",
        promptLabel = "Cantonese",
        mlKitCode = null,
    ),
}
