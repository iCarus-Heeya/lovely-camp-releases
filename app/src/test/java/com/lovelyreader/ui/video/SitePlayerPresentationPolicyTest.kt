package com.lovelyreader.ui.video

import org.junit.Assert.assertEquals
import org.junit.Test

class SitePlayerPresentationPolicyTest {
    @Test
    fun `finished episode page is visible even before provider callback arrives`() {
        assertEquals(
            SitePlayerContentVisibility.Visible,
            sitePlayerContentVisibility(entryPageFinished = true, providerReady = false, mainFrameFailed = false)
        )
    }

    @Test
    fun `main frame failure is reported instead of leaving a transparent player`() {
        assertEquals(
            SitePlayerContentVisibility.Failed,
            sitePlayerContentVisibility(entryPageFinished = false, providerReady = false, mainFrameFailed = true)
        )
    }
}
