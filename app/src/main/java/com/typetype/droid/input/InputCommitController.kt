package com.typetype.droid.input

class InputCommitController(
    private val diffWriter: StreamingDiffWriter = StreamingDiffWriter(),
) {
    private var connection: EditableInputConnection? = null
    private var streamingSuspendedUntilReset = false
    private var hasWrittenOutput = false

    fun attach(connection: EditableInputConnection?) {
        this.connection = connection
        hasWrittenOutput = false
        resetSession()
    }

    fun detach() {
        connection = null
        hasWrittenOutput = false
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
        if (!diffWriter.canApplyToField(target, edit)) {
            streamingSuspendedUntilReset = true
            diffWriter.reset()
            return false
        }
        if (!target.deleteBeforeCursor(edit.deleteChars)) {
            streamingSuspendedUntilReset = true
            diffWriter.reset()
            return false
        }
        val committed = edit.insertText.isEmpty() || target.commitText(edit.insertText)
        if (committed && (edit.insertText.isNotEmpty() || edit.deleteChars > 0)) {
            hasWrittenOutput = true
        }
        return committed
    }

    fun hasExternalChangeToStreamingText(): Boolean {
        val target = connection ?: return false
        return diffWriter.hasActiveText() && !diffWriter.fieldStillHasActiveText(target)
    }

    fun hasWrittenOutput(): Boolean = hasWrittenOutput

    fun cursorIsAtStart(): Boolean {
        val target = connection ?: return false
        return target.getTextBeforeCursor(1)?.isEmpty() != false
    }

    fun commitFinal(text: String): Boolean {
        val target = connection ?: return false
        if (text.isBlank()) return true
        resetSession()
        val committed = target.commitText(text)
        if (committed) {
            hasWrittenOutput = true
        }
        return committed
    }

    fun resetSession() {
        diffWriter.reset()
        streamingSuspendedUntilReset = false
    }

    fun resetAfterExternalCommit() {
        resetSession()
        hasWrittenOutput = false
    }
}
