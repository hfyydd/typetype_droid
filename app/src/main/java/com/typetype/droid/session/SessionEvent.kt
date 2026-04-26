package com.typetype.droid.session

import com.typetype.droid.input.EditableInputConnection

sealed interface SessionEvent {
    data class InputStarted(val connection: EditableInputConnection?) : SessionEvent
    data object InputFinished : SessionEvent
    data object PrepareRequested : SessionEvent
    data object StartRequested : SessionEvent
    data object StopRequested : SessionEvent
    data class EditorSelectionChanged(
        val oldSelectionStart: Int,
        val newSelectionStart: Int,
    ) : SessionEvent
    data class StreamingText(val text: String) : SessionEvent
    data object StreamingSegmentFinished : SessionEvent
    data class OfflineText(val text: String) : SessionEvent
    data class Error(val message: String) : SessionEvent
}
