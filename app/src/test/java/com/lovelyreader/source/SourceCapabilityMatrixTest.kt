package com.lovelyreader.source

import com.lovelyreader.domain.SourceCapability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCapabilityMatrixTest {
    @Test
    fun `only verified sources expose offline capabilities`() {
        assertTrue(SourceCapabilityMatrix.forSource("qinkan").can(SourceCapability.TXT_IMPORT))
        assertTrue(SourceCapabilityMatrix.forSource("zxcs").can(SourceCapability.READ_CHAPTER))
        assertFalse(SourceCapabilityMatrix.forSource("yqxz").can(SourceCapability.TXT_IMPORT))
        assertFalse(SourceCapabilityMatrix.forSource("ijjxs").can(SourceCapability.READ_CHAPTER))
    }
}
