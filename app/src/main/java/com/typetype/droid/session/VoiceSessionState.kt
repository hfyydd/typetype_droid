package com.typetype.droid.session

data class VoiceSessionState(
    val mode: DictationMode = DictationMode.STREAMING,
    val phase: Phase = Phase.IDLE,
    val error: String? = null,
) {
    val isActive: Boolean
        get() = phase == Phase.STARTING ||
            phase == Phase.LISTENING ||
            phase == Phase.DECODING ||
            phase == Phase.TRANSLATING

    val isPreparing: Boolean
        get() = phase == Phase.PREPARING || phase == Phase.STARTING

    val isDecoding: Boolean
        get() = phase == Phase.DECODING || phase == Phase.TRANSLATING

    enum class Phase {
        IDLE,
        PREPARING,
        READY,
        STARTING,
        LISTENING,
        DECODING,
        TRANSLATING,
        STOPPING,
        ERROR,
    }
}
