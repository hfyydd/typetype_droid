package com.typetype.droid.input

class InputCommitController(
    private val diffWriter: StreamingDiffWriter = StreamingDiffWriter(),
) {
    private var connection: EditableInputConnection? = null

    fun attach(connection: EditableInputConnection?) {
        this.connection = connection
        resetSession()
    }

    fun detach() {
        connection = null
        resetSession()
    }

    fun writeStreaming(text: String): Boolean {
        val target = connection ?: return false
        val edit = diffWriter.nextEdit(text)
        if (!diffWriter.canApplyToField(target, edit)) {
            return false
        }
        if (!target.deleteBeforeCursor(edit.deleteChars)) {
            return false
        }
        return edit.insertText.isEmpty() || target.commitText(edit.insertText)
    }

    fun commitFinal(text: String): Boolean {
        val target = connection ?: return false
        if (text.isBlank()) return true
        resetSession()
        return target.commitText(text)
    }

    fun resetSession() {
        diffWriter.reset()
    }
}
