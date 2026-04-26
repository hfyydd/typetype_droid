package com.typetype.droid.input

data class StreamingEdit(
    val deleteChars: Int,
    val insertText: String,
    val expectedDeletedText: String,
)

class StreamingDiffWriter(
    private val rewriteWindowChars: Int = DEFAULT_REWRITE_WINDOW_CHARS,
) {
    private var previousText: String = ""

    fun hasActiveText(): Boolean = previousText.isNotEmpty()

    fun fieldStillHasActiveText(connection: EditableInputConnection): Boolean {
        if (previousText.isEmpty()) return true
        val beforeCursor = connection.getTextBeforeCursor(previousText.length)?.toString() ?: return false
        return beforeCursor == previousText
    }

    fun nextEdit(nextText: String): StreamingEdit {
        val boundedPrefix = stablePrefix(previousText, nextText)
        val earliestRewriteIndex = (previousText.length - rewriteWindowChars).coerceAtLeast(0)
        val rewriteIndex = boundedPrefix.coerceAtLeast(earliestRewriteIndex)
        val deleteCount = previousText.length - rewriteIndex
        val expectedDeletedText = previousText.substring(rewriteIndex)
        val insert = nextText.substring(rewriteIndex)

        previousText = nextText
        return StreamingEdit(
            deleteChars = deleteCount,
            insertText = insert,
            expectedDeletedText = expectedDeletedText,
        )
    }

    fun reset() {
        previousText = ""
    }

    fun canApplyToField(connection: EditableInputConnection, edit: StreamingEdit): Boolean {
        if (edit.deleteChars <= 0) return true
        val beforeCursor = connection.getTextBeforeCursor(edit.deleteChars)?.toString() ?: return false
        return beforeCursor == edit.expectedDeletedText
    }

    private fun stablePrefix(left: String, right: String): Int {
        val max = minOf(left.length, right.length)
        var index = 0
        while (index < max && left[index] == right[index]) {
            index += 1
        }
        return index
    }

    private companion object {
        const val DEFAULT_REWRITE_WINDOW_CHARS = 24
    }
}
