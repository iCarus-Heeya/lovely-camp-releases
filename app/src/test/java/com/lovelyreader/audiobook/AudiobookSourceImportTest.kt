package com.lovelyreader.audiobook

import com.lovelyreader.domain.Book
import com.lovelyreader.domain.ChapterContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookSourceImportTest {
    @Test
    fun `complete readable offline novel becomes an audiobook source`() {
        val book = Book(id = "book-1", title = "测试书", author = "测试作者")
        val content = readableNovelText()

        val imported = prepareDownloadedNovelImport(
            book,
            ChapterContent(title = "全文", url = "offline://book-1", content = content)
        ).getOrThrow()

        assertEquals("测试书", imported.title)
        assertEquals("测试作者", imported.author)
        assertEquals("library-offline://book-1", imported.sourceUri)
        assertEquals(content, imported.sourceText)
    }

    @Test
    fun `missing offline text is rejected instead of uploading an incomplete book`() {
        val result = prepareDownloadedNovelImport(
            Book(id = "book-1", title = "测试书", author = ""),
            offline = null
        )

        assertTrue(result.isFailure)
        assertEquals("这本小说尚未完成全文下载，请先在书架下载完成", result.exceptionOrNull()?.message)
    }

    @Test
    fun `short offline text is rejected instead of treating a partial cache as a book`() {
        val result = prepareDownloadedNovelImport(
            Book(id = "book-1", title = "测试书", author = ""),
            ChapterContent(title = "全文", url = "offline://book-1", content = "太短了")
        )

        assertTrue(result.isFailure)
        assertEquals("下载正文不完整，请先完成全文下载", result.exceptionOrNull()?.message)
    }

    @Test
    fun `browser verification response is rejected as novel content`() {
        val result = prepareDownloadedNovelImport(
            Book(id = "book-1", title = "测试书", author = ""),
            ChapterContent(
                title = "全文",
                url = "offline://book-1",
                content = "正在验证浏览器\n请稍等，正在进行安全验证".repeat(40)
            )
        )

        assertTrue(result.isFailure)
        assertEquals("下载正文不可读，请重新下载", result.exceptionOrNull()?.message)
    }

    @Test
    fun `source identity is stable and shared by file and downloaded imports`() {
        val hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        val identity = audiobookSourceIdentity(hash)

        assertEquals("android-0123456789abcdef01234567", identity.bookId)
        assertEquals("android-source-$hash", identity.clientRequestId)
    }

    @Test
    fun `import identity is stable for retries but separates renamed book intents`() {
        val hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        val original = audiobookImportIdentity(hash, "雨停之前", "作者")
        val retry = audiobookImportIdentity(hash, " 雨停之前 ", " 作者 ")
        val renamed = audiobookImportIdentity(hash, "雨停之前-修订版", "作者")

        assertEquals(original, retry)
        assertNotEquals(original, renamed)
        assertTrue(original.bookId.startsWith("android-book-"))
        assertTrue(original.clientRequestId.startsWith("android-import-"))
    }

    @Test
    fun `invalid source hash is rejected before an upload plan can be created`() {
        val result = runCatching { audiobookSourceIdentity("not-a-sha") }

        assertFalse(result.isSuccess)
    }

    @Test
    fun `committed source reuses the existing remote task instead of uploading chunks again`() {
        assertEquals(
            AudiobookSourceUploadAction.REUSE_COMMITTED,
            audiobookSourceUploadAction("complete")
        )
        assertEquals(
            AudiobookSourceUploadAction.UPLOAD_CHUNKS,
            audiobookSourceUploadAction("uploading")
        )
        assertEquals(
            AudiobookSourceUploadAction.UPLOAD_CHUNKS,
            audiobookSourceUploadAction("")
        )
    }

    private fun readableNovelText(): String = buildString {
        repeat(80) {
            append("她把书页轻轻翻过去，窗外的灯慢慢亮了。他没有催，只是在旁边安静地陪着。\n")
        }
    }
}
