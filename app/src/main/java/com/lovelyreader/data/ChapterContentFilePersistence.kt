package com.lovelyreader.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 把大段章节文本从 SharedPreferences 里移出来，按 bookId 分文件存储。
 *
 * SharedPreferences 不适合存几 MB 的字符串，全量序列化时极易 OOM；
 * 文件系统可以按需读写单本书的内容，避免一次性把所有大书全部加载进内存再拼成一个超大字符串。
 */
class ChapterContentFilePersistence(context: Context) {

    private val offlineDir = File(context.filesDir, "library/offline_chapters").apply { mkdirs() }
    private val partialDir = File(context.filesDir, "library/partial_chapters").apply { mkdirs() }

    private val offlineFile = { bookId: String -> File(offlineDir, "$bookId.txt") }
    private val partialFile = { bookId: String -> File(partialDir, "$bookId.txt") }

    suspend fun saveOfflineChapters(chapters: List<OfflineChapter>) = withContext(Dispatchers.IO) {
        // 先清理已不在列表中的旧文件
        val activeIds = chapters.map { it.bookId }.toSet()
        offlineDir.listFiles()?.forEach { file ->
            if (file.nameWithoutExtension !in activeIds) {
                file.delete()
            }
        }
        // 每本书独立写一个文件
        chapters.forEach { chapter ->
            AtomicFileStore(offlineFile(chapter.bookId)).writeText(chapter.content)
        }
    }

    suspend fun loadOfflineChapters(metaChapters: List<OfflineChapter>): List<OfflineChapter> = withContext(Dispatchers.IO) {
        metaChapters.mapNotNull { meta ->
            val file = offlineFile(meta.bookId)
            val content = AtomicFileStore(file).readTextOrNull() ?: return@mapNotNull null
            meta.copy(content = content)
        }
    }

    suspend fun savePartialChapters(chapters: List<OfflineChapter>) = withContext(Dispatchers.IO) {
        // 按 bookId 分组，每本书一个文件
        val byBook = chapters.groupBy { it.bookId }
        val activeIds = byBook.keys
        partialDir.listFiles()?.forEach { file ->
            if (file.nameWithoutExtension !in activeIds) {
                file.delete()
            }
        }
        byBook.forEach { (bookId, list) ->
            val text = buildString {
                list.forEachIndexed { index, chapter ->
                    if (index > 0) append(CHAPTER_SEPARATOR)
                    append("TITLE:").appendLine(chapter.title)
                    append("URL:").appendLine(chapter.url)
                    append(chapter.content)
                }
            }
            AtomicFileStore(partialFile(bookId)).writeText(text)
        }
    }

    suspend fun loadPartialChapters(metaChapters: List<OfflineChapter>): List<OfflineChapter> = withContext(Dispatchers.IO) {
        metaChapters.groupBy { it.bookId }.flatMap { (bookId, metas) ->
            val file = partialFile(bookId)
            val text = AtomicFileStore(file).readTextOrNull() ?: return@flatMap emptyList()
            val chapters = parsePartialFile(bookId, text)
            // 用文件里的内容覆盖元数据里的空内容
            val metaByUrl = metas.associateBy { it.url }
            chapters.mapNotNull { chapter ->
                metaByUrl[chapter.url]?.copy(content = chapter.content)
                    ?: OfflineChapter(bookId, chapter.title, chapter.url, chapter.content)
            }
        }
    }

    private fun parsePartialFile(bookId: String, text: String): List<OfflineChapter> {
        return text.split(CHAPTER_SEPARATOR).mapNotNull { block ->
            val lines = block.lines()
            if (lines.size < 3) return@mapNotNull null
            val titleLine = lines[0]
            val urlLine = lines[1]
            if (!titleLine.startsWith("TITLE:") || !urlLine.startsWith("URL:")) return@mapNotNull null
            val title = titleLine.removePrefix("TITLE:")
            val url = urlLine.removePrefix("URL:")
            val content = lines.drop(2).joinToString("\n")
            OfflineChapter(bookId, title, url, content)
        }
    }

    companion object {
        private const val CHAPTER_SEPARATOR = "\n====CHAPTER====\n"
    }
}
