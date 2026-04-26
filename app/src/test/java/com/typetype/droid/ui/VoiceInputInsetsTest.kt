package com.typetype.droid.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceInputInsetsTest {
    @Test
    fun addsNavigationBarInsetToBaseBottomPadding() {
        assertEquals(44, VoiceInputInsets.bottomPadding(baseBottomPadding = 12, navigationBarInset = 32))
    }

    @Test
    fun keepsBaseBottomPaddingWhenNavigationBarInsetIsZero() {
        assertEquals(12, VoiceInputInsets.bottomPadding(baseBottomPadding = 12, navigationBarInset = 0))
    }
}
