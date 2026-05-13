package com.typetype.droid.asr

interface AsrEngine {
    fun acceptSamples(samples: FloatArray)
    fun finish() = Unit
    fun reset()
    fun close()
}
