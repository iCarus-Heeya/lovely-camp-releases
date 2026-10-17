package com.lovelyreader.audiobook

import android.content.Context
import com.lovelyreader.data.AtomicFileStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Local metadata and verified chapter audio cache for the audiobook feature. */
internal fun JSONObject.optNullableAudiobookString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).trim().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
}

class AudiobookRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "lovely_reader_audiobooks",
        Context.MODE_PRIVATE
    )
    private val audioRoot = File(context.applicationContext.filesDir, "audiobooks")
    /** The audiobook snapshot has to be reloadable across processes and atomically replaced. */
    private val snapshotStore = AtomicFileStore(File(audioRoot, SNAPSHOT_FILE))
    private var current = decode(loadSnapshotText())

    init {
        reconcileAudioCache()
    }

    @Synchronized
    fun snapshot(): AudiobookLibrarySnapshot = current

    @Synchronized
    fun replaceBook(book: AudiobookBook) = withRepositoryTransaction {
        val existingIndex = current.books.indexOfFirst { it.id == book.id }
        val existing = current.books.getOrNull(existingIndex)
        val books = current.books.toMutableList()
        val merged = mergeAudiobookBookLocalState(book, existing)
        if (existingIndex >= 0) books[existingIndex] = merged else books += merged
        current = current.copy(books = books)
        persist()
    }

    @Synchronized
    fun replaceBooks(books: List<AudiobookBook>) = withRepositoryTransaction {
        current = current.copy(books = mergeAudiobookBooks(books, current.books))
        persist()
    }

    @Synchronized
    fun replaceBookAndChapters(book: AudiobookBook, chapters: List<AudiobookChapter>) = withRepositoryTransaction {
        val existing = current.books.indexOfFirst { it.id == book.id }
        val mergedSnapshot = mergeAudiobookChapterSnapshot(
            incomingBook = book,
            incomingChapters = chapters,
            localBook = current.books.getOrNull(existing),
            localChapters = current.chapters
        )
        val storedBook = mergeAudiobookBookLocalState(book, current.books.getOrNull(existing))
            .copy(chaptersSnapshotComplete = mergedSnapshot.snapshotComplete)
        val books = current.books.toMutableList()
        if (existing >= 0) books[existing] = storedBook else books += storedBook
        val otherChapters = current.chapters.filterNot { it.bookId == book.id }
        val localDownloadErrors = current.chapters
            .filter { it.bookId == book.id }
            .associate { it.id to it.downloadErrorMessage }
        val incomingIds = chapters.mapTo(mutableSetOf()) { it.id }
        val mergedChapters = mergedSnapshot.chapters.map { chapter ->
            if (chapter.id !in incomingIds) {
                chapter
            } else {
                chapter.copy(
                    downloadErrorMessage = if (chapter.status == AudiobookChapterStatus.READY) {
                        localDownloadErrors[chapter.id]
                    } else {
                        null
                    }
                )
            }
        }
        current = current.copy(books = books, chapters = otherChapters + mergedChapters.sortedBy { it.ordinal })
        persist()
    }

    @Synchronized
    fun book(bookId: String): AudiobookBook? = current.books.firstOrNull { it.id == bookId }

    @Synchronized
    fun chapters(bookId: String): List<AudiobookChapter> = current.chapters
        .filter { it.bookId == bookId }
        .sortedBy { it.ordinal }

    @Synchronized
    fun updateChapter(chapter: AudiobookChapter) = withRepositoryTransaction {
        val chapters = current.chapters.toMutableList()
        val index = chapters.indexOfFirst { it.id == chapter.id }
        if (index >= 0) chapters[index] = chapter else chapters += chapter
        current = current.copy(chapters = chapters)
        persist()
    }

    @Synchronized
    fun markChapterDownloadSucceeded(downloaded: AudiobookChapter): Boolean = withRepositoryTransaction {
        val index = current.chapters.indexOfFirst {
            it.id == downloaded.id && it.bookId == downloaded.bookId
        }
        if (index < 0) return@withRepositoryTransaction false
        val merged = mergeDownloadedAudiobookChapter(current.chapters[index], downloaded)
            ?: return@withRepositoryTransaction false
        val chapters = current.chapters.toMutableList()
        chapters[index] = merged
        current = current.copy(chapters = chapters)
        persist()
        true
    }

    /**
     * Publish a verified isolated artifact only if the local row still refers
     * to the exact remote revision that produced it. This closes the race in
     * which an older download finishes after a newer refresh/download.
     */
    @Synchronized
    fun commitChapterAudio(downloaded: AudiobookChapter, artifact: File): Boolean =
        withRepositoryTransaction {
            val index = current.chapters.indexOfFirst {
                it.id == downloaded.id && it.bookId == downloaded.bookId
            }
            val merged = mergeDownloadedAudiobookChapter(
                current.chapters.getOrNull(index),
                downloaded
            )
            if (merged == null || !artifact.isFile) return@withRepositoryTransaction false
            val target = audioFile(
                downloaded.bookId,
                downloaded.id,
                downloaded.audioContentType
            )
            target.parentFile?.mkdirs()
            try {
                atomicMove(artifact, target)
                writeAudioCacheMarker(target, audiobookChapterAudioIdentity(downloaded))
            } catch (error: Exception) {
                target.delete()
                audioCacheMarker(target).delete()
                throw error
            }
            val chapters = current.chapters.toMutableList()
            chapters[index] = merged
            current = current.copy(chapters = chapters)
            persist()
            true
        }

    @Synchronized
    fun markChapterDownloadFailed(downloaded: AudiobookChapter, errorMessage: String): Boolean = withRepositoryTransaction {
        val index = current.chapters.indexOfFirst {
            it.id == downloaded.id && it.bookId == downloaded.bookId
        }
        if (index < 0 || mergeDownloadedAudiobookChapter(current.chapters[index], downloaded) == null) {
            return@withRepositoryTransaction false
        }
        val chapters = current.chapters.toMutableList()
        chapters[index] = current.chapters[index].copy(downloadErrorMessage = errorMessage)
        current = current.copy(chapters = chapters)
        persist()
        true
    }

    @Synchronized
    fun hasAudio(chapter: AudiobookChapter): Boolean =
        withAudiobookCacheTransactionLock(audioRoot) {
            val file = audioFile(chapter.bookId, chapter.id, chapter.audioContentType)
            val cachedIdentity = audioCacheMarker(file).takeIf { it.isFile }?.let {
                runCatching { it.readText() }.getOrNull()
            }
            audiobookAudioCacheIsValid(file.isFile, file.length(), chapter, cachedIdentity)
        }

    @Synchronized
    fun reconcileAudioCache(): Int = withAudiobookCacheTransactionLock(audioRoot) {
        current = decode(loadSnapshotText())
        val safeOrphanDirectories = current.books
            .filter { book -> audiobookCanReconcileOrphanCanonicalFiles(book, current.chapters) }
            .map { File(audioRoot, safePart(it.id)).absoluteFile.path }
            .toSet()
        reconcileAudiobookAudioCache(
            chapters = current.chapters,
            fileFor = { chapter -> audioFile(chapter.bookId, chapter.id, chapter.audioContentType) },
            markerFor = ::audioCacheMarker,
            readMarker = { marker -> runCatching { marker.readText() }.getOrNull() },
            managedFiles = audioRoot.walkTopDown()
                .filter {
                    it.isFile && (
                        it.name.endsWith(".wav") ||
                            it.name.endsWith(".wav.meta") ||
                            it.name.endsWith(".mp3") ||
                            it.name.endsWith(".mp3.meta")
                        )
                }
                .toList(),
            canDeleteManagedFile = { file ->
                file.parentFile?.absoluteFile?.path?.let { it in safeOrphanDirectories } == true
            }
        )
    }

    fun audioFile(bookId: String, chapterId: String, audioContentType: String? = null): File = File(
        File(audioRoot, safePart(bookId)),
        "${safePart(chapterId)}${audiobookAudioFileExtension(audioContentType)}"
    )

    @Synchronized
    fun saveHistory(entry: AudiobookHistoryEntry) = withRepositoryTransaction {
        val remaining = current.history.filterNot {
            it.bookId == entry.bookId && it.chapterId == entry.chapterId
        }
        current = current.copy(history = (listOf(entry) + remaining).take(MAX_HISTORY))
        persist()
    }

    @Synchronized
    fun history(): List<AudiobookHistoryEntry> = current.history

    @Synchronized
    fun deleteBook(bookId: String) {
        withRepositoryTransaction {
            current = current.copy(
                books = current.books.filterNot { it.id == bookId },
                chapters = current.chapters.filterNot { it.bookId == bookId },
                history = current.history.filterNot { it.bookId == bookId }
            )
            File(audioRoot, safePart(bookId)).deleteRecursively()
            persist()
        }
    }

    private fun persist() {
        // AtomicFileStore writes synchronously; callers do not need a second
        // apply/commit path that could expose a stale cross-process snapshot.
        snapshotStore.writeText(encode(current).toString())
    }

    /** Reload inside the shared OS lock so another app process cannot update from a stale snapshot. */
    private fun <T> withRepositoryTransaction(block: () -> T): T =
        withAudiobookCacheTransactionLock(audioRoot) {
            current = decode(loadSnapshotText())
            block()
        }

    private fun loadSnapshotText(): String? =
        snapshotStore.readTextOrNull() ?: preferences.getString(KEY_SNAPSHOT, null)

    private fun safePart(value: String): String = value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(128).ifBlank { "unknown" }

    private fun audioCacheMarker(target: File): File = File(
        target.parentFile,
        audiobookAudioCacheMarkerName(target.name)
    )

    private fun writeAudioCacheMarker(target: File, identity: String) {
        val marker = audioCacheMarker(target)
        val temporary = File(
            marker.parentFile,
            "${marker.name}.tmp-${Thread.currentThread().id}-${System.nanoTime()}"
        )
        try {
            temporary.writeText(identity)
            atomicMove(temporary, marker)
        } finally {
            temporary.delete()
        }
    }

    private fun atomicMove(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: UnsupportedOperationException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun encode(snapshot: AudiobookLibrarySnapshot): JSONObject = JSONObject().apply {
        put("books", JSONArray().also { array -> snapshot.books.forEach { array.put(encodeBook(it)) } })
        put("chapters", JSONArray().also { array -> snapshot.chapters.forEach { array.put(encodeChapter(it)) } })
        put("history", JSONArray().also { array -> snapshot.history.forEach { array.put(encodeHistory(it)) } })
    }

    private fun encodeBook(book: AudiobookBook): JSONObject = JSONObject().apply {
        put("id", book.id); put("clientBookId", book.clientBookId); put("title", book.title); put("author", book.author)
        put("status", book.status.name); put("phase", book.phase); put("usable", book.usable); put("progress", book.progress)
        put("sourceSha256", book.sourceSha256); put("sourceBytes", book.sourceBytes); put("sourceChunkCount", book.sourceChunkCount)
        put("sourceStatus", book.sourceStatus); put("manifestRevision", book.manifestRevision); put("chaptersTotal", book.chaptersTotal); put("chaptersReady", book.chaptersReady)
        put("chaptersFailed", book.chaptersFailed); put("generationScope", book.generationScope); put("generationTargetCount", book.generationTargetCount)
        put("chaptersSnapshotComplete", book.chaptersSnapshotComplete)
        put("policyId", book.policyId); put("policySha256", book.policySha256)
        put("contractSchema", book.contractSchema); put("pipelineFingerprint", book.pipelineFingerprint)
        put("errorCode", book.errorCode ?: JSONObject.NULL); put("errorMessage", book.errorMessage ?: JSONObject.NULL)
        put("sourceUri", book.sourceUri ?: JSONObject.NULL); put("createdAt", book.createdAt); put("updatedAt", book.updatedAt)
    }

    private fun encodeChapter(chapter: AudiobookChapter): JSONObject = JSONObject().apply {
        put("id", chapter.id); put("bookId", chapter.bookId); put("ordinal", chapter.ordinal); put("title", chapter.title)
        put("sourceStart", chapter.sourceStart); put("sourceEnd", chapter.sourceEnd); put("sourceSpanSha256", chapter.sourceSpanSha256); put("manifestRevision", chapter.manifestRevision)
        put("status", chapter.status.name); put("usable", chapter.usable); put("progress", chapter.progress); put("partCount", chapter.partCount)
        put("audioArtifactId", chapter.audioArtifactId ?: JSONObject.NULL); put("audioSha256", chapter.audioSha256 ?: JSONObject.NULL)
        put("audioBytes", chapter.audioBytes ?: JSONObject.NULL); put("audioContentType", chapter.audioContentType ?: JSONObject.NULL)
        put("durationMs", chapter.durationMs ?: JSONObject.NULL); put("errorCode", chapter.errorCode ?: JSONObject.NULL)
        put("errorMessage", chapter.errorMessage ?: JSONObject.NULL); put("downloadErrorMessage", chapter.downloadErrorMessage ?: JSONObject.NULL)
        put("attempt", chapter.attempt); put("updatedAt", chapter.updatedAt)
    }

    private fun encodeHistory(entry: AudiobookHistoryEntry): JSONObject = JSONObject().apply {
        put("bookId", entry.bookId); put("bookTitle", entry.bookTitle); put("chapterId", entry.chapterId)
        put("chapterTitle", entry.chapterTitle); put("positionMs", entry.positionMs); put("durationMs", entry.durationMs)
        put("updatedAt", entry.updatedAt)
    }

    private fun decode(raw: String?): AudiobookLibrarySnapshot {
        if (raw.isNullOrBlank()) return AudiobookLibrarySnapshot()
        return runCatching {
            val root = JSONObject(raw)
            val books = root.optJSONArray("books").toListObjects().map(::decodeBook)
            val chapters = root.optJSONArray("chapters").toListObjects().map(::decodeChapter)
            val history = root.optJSONArray("history").toListObjects().map(::decodeHistory)
            AudiobookLibrarySnapshot(books, chapters, history)
        }.getOrDefault(AudiobookLibrarySnapshot())
    }

    private fun decodeBook(value: JSONObject) = AudiobookBook(
        id = value.optString("id"), clientBookId = value.optString("clientBookId"), title = value.optString("title"), author = value.optString("author"),
        status = AudiobookBookStatus.fromWire(value.optString("status")), phase = value.optString("phase"), usable = value.optBoolean("usable"), progress = value.optInt("progress"),
        sourceSha256 = value.optString("sourceSha256"), sourceBytes = value.optLong("sourceBytes"), sourceChunkCount = value.optInt("sourceChunkCount"), sourceStatus = value.optString("sourceStatus"), manifestRevision = value.optLong("manifestRevision"),
        chaptersTotal = value.optInt("chaptersTotal"), chaptersReady = value.optInt("chaptersReady"), chaptersFailed = value.optInt("chaptersFailed"),
        generationScope = value.optString("generationScope").trim().ifBlank { AudiobookPipelineContract.FULL_GENERATION_SCOPE },
        generationTargetCount = value.optInt("generationTargetCount"),
        chaptersSnapshotComplete = value.optBoolean("chaptersSnapshotComplete", false),
        policyId = value.optString("policyId"), policySha256 = value.optString("policySha256"), contractSchema = value.optString("contractSchema"), pipelineFingerprint = value.optString("pipelineFingerprint"),
        errorCode = value.optNullableAudiobookString("errorCode"), errorMessage = value.optNullableAudiobookString("errorMessage"),
        sourceUri = value.optNullableAudiobookString("sourceUri"), createdAt = value.optString("createdAt"), updatedAt = value.optString("updatedAt")
    )

    private fun decodeChapter(value: JSONObject) = AudiobookChapter(
        id = value.optString("id"), bookId = value.optString("bookId"), ordinal = value.optInt("ordinal"), title = value.optString("title"),
        sourceStart = value.optInt("sourceStart"), sourceEnd = value.optInt("sourceEnd"), sourceSpanSha256 = value.optString("sourceSpanSha256"), manifestRevision = value.optLong("manifestRevision"),
        status = AudiobookChapterStatus.fromWire(value.optString("status")), usable = value.optBoolean("usable"), progress = value.optInt("progress"), partCount = value.optInt("partCount", 1),
        audioArtifactId = value.optNullableAudiobookString("audioArtifactId"), audioSha256 = value.optNullableAudiobookString("audioSha256"),
        audioBytes = value.optLong("audioBytes").takeIf { value.has("audioBytes") && !value.isNull("audioBytes") }, audioContentType = value.optNullableAudiobookString("audioContentType"),
        durationMs = value.optLong("durationMs").takeIf { value.has("durationMs") && !value.isNull("durationMs") }, errorCode = value.optNullableAudiobookString("errorCode"),
        errorMessage = value.optNullableAudiobookString("errorMessage"), downloadErrorMessage = value.optNullableAudiobookString("downloadErrorMessage"),
        attempt = value.optInt("attempt"), updatedAt = value.optString("updatedAt")
    )

    private fun decodeHistory(value: JSONObject) = AudiobookHistoryEntry(
        bookId = value.optString("bookId"), bookTitle = value.optString("bookTitle"), chapterId = value.optString("chapterId"), chapterTitle = value.optString("chapterTitle"),
        positionMs = value.optLong("positionMs"), durationMs = value.optLong("durationMs"), updatedAt = value.optLong("updatedAt")
    )

    private fun JSONArray?.toListObjects(): List<JSONObject> = buildList {
        if (this@toListObjects == null) return@buildList
        for (index in 0 until this@toListObjects.length()) {
            this@toListObjects.optJSONObject(index)?.let(::add)
        }
    }

    companion object {
        private const val KEY_SNAPSHOT = "snapshot"
        private const val SNAPSHOT_FILE = "library-snapshot.json"
        private const val MAX_HISTORY = 50
    }
}
