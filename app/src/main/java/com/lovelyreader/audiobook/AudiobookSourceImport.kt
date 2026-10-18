package com.lovelyreader.audiobook

import com.lovelyreader.domain.Book
import com.lovelyreader.domain.ChapterContent
import com.lovelyreader.source.SourceContentGuard
import java.security.MessageDigest

/** The validated, complete source that will be submitted to the audiobook pipeline. */
data class DownloadedNovelImport(
    val title: String,
    val author: String,
    val sourceUri: String,
    val sourceText: String
)

data class AudiobookSourceIdentity(
    val bookId: String,
    val clientRequestId: String
)

enum class AudiobookSourceUploadAction {
    UPLOAD_CHUNKS,
    REUSE_COMMITTED
}

fun audiobookSourceUploadAction(sourceStatus: String): AudiobookSourceUploadAction =
    if (sourceStatus.trim().equals("complete", ignoreCase = true)) {
        AudiobookSourceUploadAction.REUSE_COMMITTED
    } else {
        AudiobookSourceUploadAction.UPLOAD_CHUNKS
    }

/**
 * Turns the app's completed offline novel cache into an audiobook source.
 *
 * A partial chapter cache is intentionally not accepted here: the local model
 * must see the same complete text that the reader can open offline.
 */
fun prepareDownloadedNovelImport(book: Book?, offline: ChapterContent?): Result<DownloadedNovelImport> {
    if (book == null) {
        return Result.failure(IllegalArgumentException("书架中找不到这本小说"))
    }
    if (offline == null) {
        return Result.failure(IllegalStateException("这本小说尚未完成全文下载，请先在书架下载完成"))
    }
    if (offline.content.length < MIN_AUDIOBOOK_SOURCE_CHARS) {
        return Result.failure(IllegalStateException("下载正文不完整，请先完成全文下载"))
    }
    if (!SourceContentGuard.isReadableNovelText(offline.content)) {
        return Result.failure(IllegalStateException("下载正文不可读，请重新下载"))
    }

    return Result.success(
        DownloadedNovelImport(
            title = book.title.trim().ifBlank { offline.title.trim().ifBlank { "未命名有声书" } },
            author = book.author.trim(),
            sourceUri = "library-offline://${book.id}",
            sourceText = offline.content
        )
    )
}

/**
 * Legacy source-only identity retained for compatibility with old callers.
 * New uploads must use [audiobookImportIdentity], because the server's
 * request fingerprint also contains the user-visible title and author.
 */
fun audiobookSourceIdentity(sourceSha256: String): AudiobookSourceIdentity {
    val normalized = sourceSha256.trim().lowercase()
    require(SHA256_PATTERN.matches(normalized)) { "源文 SHA-256 无效" }
    return AudiobookSourceIdentity(
        bookId = "android-${normalized.take(24)}",
        clientRequestId = "android-source-$normalized"
    )
}

/**
 * Creates a deterministic identity for one import intent.
 *
 * A retry with the same source metadata replays safely, while the same bytes
 * intentionally submitted under a different title do not collide with the
 * server's fingerprint and get reported as a false service outage.
 */
fun audiobookImportIdentity(
    sourceSha256: String,
    title: String,
    author: String,
    generationScope: String = AudiobookPipelineContract.DEFAULT_GENERATION_SCOPE
): AudiobookSourceIdentity {
    val normalizedSourceSha256 = sourceSha256.trim().lowercase()
    require(SHA256_PATTERN.matches(normalizedSourceSha256)) { "源文 SHA-256 无效" }
    val intentParts = listOf(
        "audiobook-import-v2",
        normalizedSourceSha256,
        title.trim(),
        author.trim(),
        generationScope.trim().lowercase(),
        AudiobookPipelineContract.SOURCE_CHUNK_BYTES.toString(),
        AudiobookPipelineContract.POLICY_ID,
        AudiobookPipelineContract.POLICY_SHA256,
        AudiobookPipelineContract.CONTRACT_SCHEMA,
        AudiobookPipelineContract.PIPELINE_FINGERPRINT,
    )
    val intentHash = MessageDigest.getInstance("SHA-256")
        .digest(intentParts.joinToString("\u0000") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return AudiobookSourceIdentity(
        bookId = "android-book-${intentHash.take(32)}",
        clientRequestId = "android-import-$intentHash"
    )
}

private const val MIN_AUDIOBOOK_SOURCE_CHARS = 500
private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
