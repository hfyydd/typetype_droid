package com.typetype.droid.settings

import android.content.Context
import com.typetype.droid.session.DictationMode
import com.typetype.droid.translation.TranslationBackend
import com.typetype.droid.translation.TranslationOutputMode
import com.typetype.droid.translation.TranslationSettings
import com.typetype.droid.translation.TranslationTargetLanguage

class VoiceImePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("voice_ime_preferences", Context.MODE_PRIVATE)

    fun loadMode(): DictationMode {
        return runCatching {
            DictationMode.valueOf(preferences.getString(KEY_MODE, DictationMode.STREAMING.name)!!)
        }.getOrDefault(DictationMode.STREAMING)
    }

    fun loadEffectiveMode(): DictationMode {
        return if (loadTranslationSettings().outputMode == TranslationOutputMode.TRANSLATION) {
            DictationMode.OFFLINE
        } else {
            loadMode()
        }
    }

    fun saveMode(mode: DictationMode) {
        preferences.edit().putString(KEY_MODE, mode.name).apply()
    }

    fun loadTranslationSettings(): TranslationSettings {
        return normalizeTranslationSettings(
            TranslationSettings(
            outputMode = runCatching {
                TranslationOutputMode.valueOf(
                    preferences.getString(KEY_OUTPUT_MODE, TranslationOutputMode.DICTATION.name)!!,
                )
            }.getOrDefault(TranslationOutputMode.DICTATION),
            backend = runCatching {
                TranslationBackend.valueOf(
                    preferences.getString(KEY_BACKEND, TranslationBackend.ML_KIT.name)!!,
                )
            }.getOrDefault(TranslationBackend.ML_KIT),
            targetLanguage = runCatching {
                TranslationTargetLanguage.valueOf(
                    preferences.getString(KEY_TARGET_LANGUAGE, TranslationTargetLanguage.ENGLISH.name)!!,
                )
            }.getOrDefault(TranslationTargetLanguage.ENGLISH),
            ),
        )
    }

    fun loadAndroid031Settings(): Android031Settings {
        val llmProviderKey = preferences.getString(KEY_LLM_PROVIDER_KEY, LlmProviderPresets.all.first().key)
            ?: LlmProviderPresets.all.first().key
        val preset = LlmProviderPresets.presetFor(llmProviderKey)
        return Android031Settings(
            streamingModel = enumValue(KEY_STREAMING_MODEL, StreamingModelPreference.MULTILINGUAL_REALTIME),
            voicePackage = enumValue(KEY_VOICE_PACKAGE, VoicePackagePreference.FAST_OFFLINE),
            streamingEnhancementMode = enumValue(KEY_STREAMING_ENHANCEMENT_MODE, StreamingEnhancementMode.OFFLINE_PRIVATE),
            streamingAiPanelEnabled = preferences.getBoolean(KEY_STREAMING_AI_PANEL_ENABLED, true),
            rewriteBackend = enumValue(KEY_REWRITE_BACKEND, RewriteBackendPreference.LOCAL),
            autoLearningEnabled = preferences.getBoolean(KEY_AUTO_LEARNING_ENABLED, true),
            voiceFormattingEnabled = preferences.getBoolean(KEY_VOICE_FORMATTING_ENABLED, true),
            systemLexiconEnabled = preferences.getBoolean(KEY_SYSTEM_LEXICON_ENABLED, true),
            rewriteScenario = enumValue(KEY_REWRITE_SCENARIO, RewriteScenario.GENERAL),
            llmRewrite = LlmRewriteConfig(
                enabled = preferences.getBoolean(KEY_LLM_ENABLED, false),
                providerKey = llmProviderKey,
                provider = preset.provider,
                apiKey = preferences.getString(KEY_LLM_API_KEY, "") ?: "",
                baseUrl = preferences.getString(KEY_LLM_BASE_URL, preset.baseUrl)?.ifBlank { preset.baseUrl } ?: preset.baseUrl,
                model = preferences.getString(KEY_LLM_MODEL, preset.model)?.ifBlank { preset.model } ?: preset.model,
                temperature = Double.fromBits(preferences.getLong(KEY_LLM_TEMPERATURE, preset.temperature.toBits())),
                maxTokens = preferences.getInt(KEY_LLM_MAX_TOKENS, 4096),
            ),
        )
    }

    fun saveTranslationOutputMode(mode: TranslationOutputMode) {
        preferences.edit().putString(KEY_OUTPUT_MODE, mode.name).apply()
    }

    fun saveTranslationBackend(backend: TranslationBackend) {
        preferences.edit().putString(KEY_BACKEND, backend.name).apply()
    }

    fun saveTranslationTargetLanguage(targetLanguage: TranslationTargetLanguage) {
        val editor = preferences.edit().putString(KEY_TARGET_LANGUAGE, targetLanguage.name)
        if (!targetLanguage.isMlKitSupported) {
            editor.putString(KEY_BACKEND, TranslationBackend.HY_MT.name)
        }
        editor.apply()
    }

    fun saveStreamingModel(value: StreamingModelPreference) {
        preferences.edit().putString(KEY_STREAMING_MODEL, value.name).apply()
    }

    fun saveVoicePackage(value: VoicePackagePreference) {
        preferences.edit().putString(KEY_VOICE_PACKAGE, value.name).apply()
    }

    fun saveStreamingEnhancementMode(value: StreamingEnhancementMode) {
        preferences.edit().putString(KEY_STREAMING_ENHANCEMENT_MODE, value.name).apply()
    }

    fun saveStreamingAiPanelEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_STREAMING_AI_PANEL_ENABLED, enabled).apply()
    }

    fun saveRewriteBackend(value: RewriteBackendPreference) {
        preferences.edit()
            .putString(KEY_REWRITE_BACKEND, value.name)
            .putBoolean(KEY_LLM_ENABLED, value == RewriteBackendPreference.AI || preferences.getBoolean(KEY_LLM_ENABLED, false))
            .apply()
    }

    fun saveAutoLearningEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_LEARNING_ENABLED, enabled).apply()
    }

    fun saveVoiceFormattingEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_VOICE_FORMATTING_ENABLED, enabled).apply()
    }

    fun saveSystemLexiconEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SYSTEM_LEXICON_ENABLED, enabled).apply()
    }

    fun saveRewriteScenario(value: RewriteScenario) {
        preferences.edit().putString(KEY_REWRITE_SCENARIO, value.name).apply()
    }

    fun saveLlmProvider(providerKey: String) {
        val preset = LlmProviderPresets.presetFor(providerKey)
        preferences.edit()
            .putString(KEY_LLM_PROVIDER_KEY, preset.key)
            .putString(KEY_LLM_BASE_URL, preset.baseUrl)
            .putString(KEY_LLM_MODEL, preset.model)
            .putLong(KEY_LLM_TEMPERATURE, preset.temperature.toBits())
            .apply()
    }

    fun saveLlmEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_LLM_ENABLED, enabled).apply()
    }

    fun saveLlmApiKey(value: String) {
        preferences.edit().putString(KEY_LLM_API_KEY, value.trim()).apply()
    }

    fun saveLlmBaseUrl(value: String) {
        preferences.edit().putString(KEY_LLM_BASE_URL, value.trim()).apply()
    }

    fun saveLlmModel(value: String) {
        preferences.edit().putString(KEY_LLM_MODEL, value.trim()).apply()
    }

    private inline fun <reified T : Enum<T>> enumValue(key: String, defaultValue: T): T {
        return runCatching {
            enumValueOf<T>(preferences.getString(key, defaultValue.name)!!)
        }.getOrDefault(defaultValue)
    }

    private fun normalizeTranslationSettings(settings: TranslationSettings): TranslationSettings {
        return if (settings.backend == TranslationBackend.ML_KIT && !settings.targetLanguage.isMlKitSupported) {
            settings.copy(backend = TranslationBackend.HY_MT)
        } else {
            settings
        }
    }

    private companion object {
        const val KEY_MODE = "dictation_mode"
        const val KEY_OUTPUT_MODE = "translation_output_mode"
        const val KEY_BACKEND = "translation_backend"
        const val KEY_TARGET_LANGUAGE = "translation_target_language"
        const val KEY_STREAMING_MODEL = "streaming_model"
        const val KEY_VOICE_PACKAGE = "voice_package"
        const val KEY_STREAMING_ENHANCEMENT_MODE = "streaming_enhancement_mode"
        const val KEY_STREAMING_AI_PANEL_ENABLED = "streaming_ai_panel_enabled"
        const val KEY_REWRITE_BACKEND = "rewrite_backend"
        const val KEY_AUTO_LEARNING_ENABLED = "auto_learning_enabled"
        const val KEY_VOICE_FORMATTING_ENABLED = "voice_formatting_enabled"
        const val KEY_SYSTEM_LEXICON_ENABLED = "system_lexicon_enabled"
        const val KEY_REWRITE_SCENARIO = "rewrite_scenario"
        const val KEY_LLM_ENABLED = "llm_enabled"
        const val KEY_LLM_PROVIDER_KEY = "llm_provider_key"
        const val KEY_LLM_API_KEY = "llm_api_key"
        const val KEY_LLM_BASE_URL = "llm_base_url"
        const val KEY_LLM_MODEL = "llm_model"
        const val KEY_LLM_TEMPERATURE = "llm_temperature"
        const val KEY_LLM_MAX_TOKENS = "llm_max_tokens"
    }
}
