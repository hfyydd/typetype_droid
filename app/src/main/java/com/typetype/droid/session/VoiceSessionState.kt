package com.typetype.droid.session

data class VoiceSessionState(
    val mode: DictationMode = DictationMode.STREAMING,
    val phase: Phase = Phase.IDLE,
    val error: String? = null,
) {
    val isActive: Boolean
        get() = phase == Phase.PREPARING || phase == Phase.LISTENING || phase == Phase.DECODING

    val isDecoding: Boolean
        get() = phase == Phase.DECODING

    enum class Phase {
        IDLE,
        PREPARING,
        LISTENING,
        DECODING,
        STOPPING,
        ERROR,
    }
}
