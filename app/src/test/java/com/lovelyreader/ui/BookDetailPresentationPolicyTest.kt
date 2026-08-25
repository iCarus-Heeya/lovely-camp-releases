package com.lovelyreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDetailPresentationPolicyTest {
    @Test
    fun authorSearchQueryIsTrimmedAndKeepsChineseName() {
        assertEquals("顾漫", normalizedAuthorSearchQuery("  顾漫  "))
        assertEquals("", normalizedAuthorSearchQuery("   "))
    }

    @Test
    fun negativeOfflineCapabilityIsNotPresentedAsAnActionCard() {
        assertFalse(shouldShowOfflineCapabilityPanel("暂不支持站内下载"))
        assertFalse(shouldShowOfflineCapabilityPanel("暂不可离线"))
        assertFalse(shouldShowOfflineCapabilityPanel(null))
        assertTrue(shouldShowOfflineCapabilityPanel("可下载TXT并在书架阅读"))
    }
}
