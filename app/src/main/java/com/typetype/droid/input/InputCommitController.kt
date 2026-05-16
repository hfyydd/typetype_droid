package com.typetype.droid.input

class InputCommitController(
    private val diffWriter: StreamingDiffWriter = StreamingDiffWriter(),
) {
    private var connection: EditableInputConnection? = null
    private var streamingSuspendedUntilReset = false
    private var hasWrittenOutput = false
    private var committedStreamingText = ""

    fun attach(connection: EditableInputConnection?) {
        this.connection = connection
        hasWrittenOutput = false
        committedStreamingText = ""
        resetSession()
    }

    fun detach() {
        connection = null
        hasWrittenOutput = false
        committedStreamingText = ""
        resetSession()
    }

    fun writeStreaming(text: String): Boolean {
        val target = connection ?: return false
        if (streamingSuspendedUntilReset) return false
        if (diffWriter.hasActiveText() && !diffWriter.fieldStillHasActiveText(target)) {
            streamingSuspendedUntilReset = true
            diffWriter.reset()
            return false
        }
        val edit = diffWriter.nextEdit(text)

        if (diffWriter.hasActiveText()) {
            val cachedBeforeCursor = target.getTextBeforeCursor(edit.deleteChars)?.toString()
            if (cachedBeforeCursor == null) {
                streamingSuspendedUntilReset = true
                diffWriter.reset()
                return false
            }
            if (!textStartsWith(cachedBeforeCursor, edit.expectedDeletedText)) {
                streamingSuspendedUntilReset = true
                diffWriter.reset()
                return false
            }
        }

        if (!target.beginBatchEdit()) {
            return false
        }
        try {
            if (edit.deleteChars > 0 && !target.deleteBeforeCursor(edit.deleteChars)) {
                streamingSuspendedUntilReset = true
                diffWriter.reset()
                return false
            }
            val committed = edit.insertText.isEmpty() || target.commitText(edit.insertText)
            if (committed && (edit.insertText.isNotEmpty() || edit.deleteChars > 0)) {
                hasWrittenOutput = true
            }
            return committed
        } finally {
            target.endBatchEdit()
        }
    }

    private fun textStartsWith(text: String, prefix: String): Boolean {
        return text.length >= prefix.length && text.substring(0, prefix.length) == prefix
    }

    fun hasExternalChangeToStreamingText(): Boolean {
        val target = connection ?: return false
        return diffWriter.hasActiveText() && !diffWriter.fieldStillHasActiveText(target)
    }

    fun hasWrittenOutput(): Boolean = hasWrittenOutput

    fun currentStreamingText(): String = committedStreamingText + diffWriter.currentText()

    fun cursorIsAtStart(): Boolean {
        val target = connection ?: return false
        return target.getTextBeforeCursor(1)?.isEmpty() != false
    }

    fun commitFinal(text: String): Boolean {
        val target = connection ?: return false
        if (text.isBlank()) return true
        resetSession()
        committedStreamingText = ""
        val committed = target.commitText(text)
        if (committed) {
            hasWrittenOutput = true
        }
        return committed
    }

    fun finishStreamingSegment() {
        committedStreamingText += diffWriter.currentText()
        resetSession()
    }

    fun replaceStreamingText(text: String): Boolean {
        val target = connection ?: return false
        val previous = currentStreamingText()
        if (previous.isBlank()) {
            return commitFinal(text)
        }
        val cachedBeforeCursor = target.getTextBeforeCursor(previous.length)?.toString()
        if (cachedBeforeCursor != previous) {
            streamingSuspendedUntilReset = true
            diffWriter.reset()
            committedStreamingText = ""
            return false
        }

        if (!target.beginBatchEdit()) {
            return false
        }
        return try {
            val deleted = target.deleteBeforeCursor(previous.length)
            val committed = deleted && (text.isBlank() || target.commitText(text))
            if (committed) {
                hasWrittenOutput = text.isNotBlank()
                committedStreamingText = ""
                diffWriter.reset()
            }
            committed
        } finally {
            target.endBatchEdit()
        }
    }

    fun resetSession() {
        diffWriter.reset()
        streamingSuspendedUntilReset = false
    }

    fun resetAfterExternalCommit() {
        resetSession()
        hasWrittenOutput = false
        committedStreamingText = ""
    }
}
