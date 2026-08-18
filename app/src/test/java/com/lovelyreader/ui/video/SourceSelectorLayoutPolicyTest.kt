package com.lovelyreader.ui.video

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceSelectorLayoutPolicyTest {
    @Test
    fun six_sources_use_a_horizontally_scrollable_selector() {
        assertEquals(
            SourceSelectorLayout.HORIZONTALLY_SCROLLABLE,
            sourceSelectorLayoutFor(sourceCount = 6)
        )
    }
}
