package com.typetype.droid.asr

import com.typetype.droid.session.DictationMode

class NoOpAsrEngineFactory : AsrEngineFactory {
    override fun create(mode: DictationMode, onEvent: (AsrEvent) -> Unit): AsrEngine = NoOpAsrEngine
}

private object NoOpAsrEngine : AsrEngine {
    override fun acceptSamples(samples: FloatArray) = Unit
    override fun reset() = Unit
    override fun close() = Unit
}
