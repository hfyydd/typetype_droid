package com.typetype.droid.translation

import com.google.mlkit.nl.translate.TranslateLanguage

enum class TranslationTargetLanguage(
    val label: String,
    val hyMtTargetLabel: String,
    val mlKitCode: String?,
) {
    ENGLISH(
        label = "英语",
        hyMtTargetLabel = "英语",
        mlKitCode = TranslateLanguage.ENGLISH,
    ),
    JAPANESE(
        label = "日语",
        hyMtTargetLabel = "日语",
        mlKitCode = TranslateLanguage.JAPANESE,
    ),
    GERMAN(
        label = "德语",
        hyMtTargetLabel = "德语",
        mlKitCode = TranslateLanguage.GERMAN,
    ),
    CANTONESE(
        label = "粤语（实验性）",
        hyMtTargetLabel = "粤语",
        mlKitCode = null,
    ),
}
