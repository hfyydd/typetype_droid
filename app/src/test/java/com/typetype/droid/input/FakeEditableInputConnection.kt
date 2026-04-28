package com.typetype.droid.input

class FakeEditableInputConnection(initialText: String = "") : EditableInputConnection {
    private val buffer = StringBuilder(initialText)

    val text: String
        get() = buffer.toString()

    override fun commitText(text: String): Boolean {
        buffer.append(text)
        return true
    }

    override fun deleteBeforeCursor(length: Int): Boolean {
        if (length < 0 || length > buffer.length) return false
        buffer.delete(buffer.length - length, buffer.length)
        return true
    }

    override fun getTextBeforeCursor(length: Int): CharSequence? {
        if (length < 0 || length > buffer.length) return null
        return buffer.substring(buffer.length - length, buffer.length)
    }

    override fun beginBatchEdit(): Boolean = true

    override fun endBatchEdit(): Boolean = true

    fun clear() {
        buffer.clear()
    }
}
