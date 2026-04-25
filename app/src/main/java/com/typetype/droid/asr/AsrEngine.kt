package com.typetype.droid.asr

interface AsrEngine {
    fun acceptSamples(samples: FloatArray)
    fun close()
}
