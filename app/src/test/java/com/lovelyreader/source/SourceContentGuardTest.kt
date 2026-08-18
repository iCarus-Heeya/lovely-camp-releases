package com.lovelyreader.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceContentGuardTest {
    @Test
    fun rejectsBrowserVerificationTextAsReadableNovelContent() {
        val text = """
            正在验证浏览器

            请稍等，正在进行安全驗證...
        """.trimIndent()

        assertFalse(SourceContentGuard.isReadableNovelText(text))
    }

    @Test
    fun rejectsMojibakeReplacementTextAsReadableNovelContent() {
        val text = "������Øε����N���\n���и����倚\n������������"

        assertFalse(SourceContentGuard.isReadableNovelText(text))
    }

    @Test
    fun acceptsNormalNovelParagraphsAsReadableNovelContent() {
        val text = """
            第一章

            她把书页轻轻翻过去，窗外的灯慢慢亮了。
            他没有催，只是在旁边安静地陪着。
        """.trimIndent()

        assertTrue(SourceContentGuard.isReadableNovelText(text))
    }
}
