package com.typetype.droid.asr

sealed interface AsrEvent {
    data class Text(val value: String) : AsrEvent
    data object SegmentFinished : AsrEvent
}
