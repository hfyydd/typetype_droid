package com.typetype.droid.translation

fun interface TranslationEngineResolver {
    fun resolve(backend: TranslationBackend): TranslationEngine
}
