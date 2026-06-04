package com.typetype.droid.input

interface EditableInputConnection {
    fun commitText(text: String): Boolean
    fun deleteBeforeCursor(length: Int): Boolean
    fun getTextBeforeCursor(length: Int): CharSequence?
    fun getTextBeforeCursorCompat(length: Int): CharSequence? = getTextBeforeCursor(length)
    fun beginBatchEdit(): Boolean
    fun endBatchEdit(): Boolean
}
