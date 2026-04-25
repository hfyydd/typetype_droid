package com.typetype.droid.asr

import com.typetype.droid.session.DictationMode

interface AsrEngineFactory {
    fun create(mode: DictationMode, onEvent: (AsrEvent) -> Unit): AsrEngine
}
