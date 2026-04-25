package com.typetype.droid.settings

import android.content.Context
import com.typetype.droid.session.DictationMode

class VoiceImePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("voice_ime_preferences", Context.MODE_PRIVATE)

    fun loadMode(): DictationMode {
        return runCatching {
            DictationMode.valueOf(preferences.getString(KEY_MODE, DictationMode.STREAMING.name)!!)
        }.getOrDefault(DictationMode.STREAMING)
    }

    fun saveMode(mode: DictationMode) {
        preferences.edit().putString(KEY_MODE, mode.name).apply()
    }

    private companion object {
        const val KEY_MODE = "dictation_mode"
    }
}
