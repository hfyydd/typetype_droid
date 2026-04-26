package com.typetype.droid.ui

internal object VoiceInputInsets {
    fun bottomPadding(baseBottomPadding: Int, navigationBarInset: Int): Int {
        return baseBottomPadding + navigationBarInset
    }
}
