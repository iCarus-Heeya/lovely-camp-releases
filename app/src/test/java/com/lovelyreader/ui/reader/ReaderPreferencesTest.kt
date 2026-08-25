package com.lovelyreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPreferencesTest {
    @Test
    fun `normalizes font and line spacing to readable bounds`() {
        assertEquals(ReaderPreferences(14, 12, false), ReaderPreferences(fontSize = 8, lineSpacing = 4).normalized())
        assertEquals(ReaderPreferences(24, 32, true), ReaderPreferences(fontSize = 40, lineSpacing = 40, nightMode = true).normalized())
    }
}
