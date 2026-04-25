package com.typetype.droid.input

import android.view.inputmethod.InputConnection

class AndroidInputConnectionAdapter(
    private val inputConnection: InputConnection,
) : EditableInputConnection {
    override fun commitText(text: String): Boolean = inputConnection.commitText(text, 1)

    override fun deleteBeforeCursor(length: Int): Boolean {
        if (length <= 0) return true
        return inputConnection.deleteSurroundingText(length, 0)
    }

    override fun getTextBeforeCursor(length: Int): CharSequence? {
        return inputConnection.getTextBeforeCursor(length, 0)
    }
}
