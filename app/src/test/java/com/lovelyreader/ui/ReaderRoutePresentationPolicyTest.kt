package com.lovelyreader.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderRoutePresentationPolicyTest {
    @Test
    fun `reader route hides shared application chrome`() {
        assertFalse(shouldShowSharedAppChrome(Screen.Reader("book")))
    }

    @Test
    fun `browse routes retain shared application chrome`() {
        assertTrue(shouldShowSharedAppChrome(Screen.Search))
    }
}
