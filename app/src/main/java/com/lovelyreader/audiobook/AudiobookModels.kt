package com.lovelyreader.audiobook

import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CancellationException

enum class AudiobookBookStatus {
    UNKNOWN,
    ACCEPTED,
    INGESTING,
    ANALYZING,
    SPLITTING,
    WAITING_VOICE_CONFIRMATION,
    GENERATING,
    PARTIAL_READY,
    PREVIEW_READY,
    PREVIEW_PARTIAL_FAILED,
    PREVIEW_FAILED,
    READY,
    FAILED,
    PARTIAL_FAILED,
    CANCELLED,
    EXPIRED;

    companion object {
        fun fromWire(value: String?): AudiobookBookStatus = entries.firstOrNull {
            it.name.equals(value.orEmpty().trim().replace('-', '_'), ignoreCase = true)
        } ?: UNKNOWN
    }
}

enum class AudiobookChapterStatus {
    PENDING,
    ANNOTATING,
    READY_TO_RENDER,
    RENDERING,
    UPLOADING,
    VERIFYING,
    READY,
    WAITING_RESOURCE_WINDOW,
    NEEDS_REVIEW,
    FAILED_RETRYABLE,
    FAILED_PERMANENT,
    CANCELLED,
    NOT_SELECTED,
    UNKNOWN;

    companion object {
        fun fromWire(value: String?): AudiobookChapterStatus = entries.firstOrNull {
            it.name.equals(value.orEmpty().trim().replace('-', '_'), ignoreCase = true)
        } ?: UNKNOWN
    }
}

data class AudiobookBook(
    val id: String,
    val clientBookId: String,
    val title: String,
    val author: String,
    val status: AudiobookBookStatus = AudiobookBookStatus.UNKNOWN,
    val phase: String = "",
    val usable: Boolean = false,
    val progress: Int = 0,
    val sourceSha256: String = "",
    val sourceBytes: Long = 0L,
    val sourceChunkCount: Int = 0,
    val sourceStatus: String = "",
    /** Server-issued analysis manifest revision; zero means legacy/unknown. */
    val manifestRevision: Long = 0L,
    val chaptersTotal: Int = 0,
    val chaptersReady: Int = 0,
    val chaptersFailed: Int = 0,
    /** Whether this book is rendering the whole book or selected representative chapters. */
    val generationScope: String = AudiobookPipelineContract.FULL_GENERATION_SCOPE,
    /** Number of chapters selected for a representative render; zero means not applicable. */
    val generationTargetCount: Int = 0,
    /** True only after a detail response proves that every chapter row is present locally. */
    val chaptersSnapshotComplete: Boolean = false,
    val policyId: String = "",
    val policySha256: String = "",
    val contractSchema: String = "",
    val pipelineFingerprint: String = "",
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val sourceUri: String? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
)

data class AudiobookChapter(
    val id: String,
    val bookId: String,
    val ordinal: Int,
    val title: String,
    val sourceStart: Int = 0,
    val sourceEnd: Int = 0,
    val sourceSpanSha256: String = "",
    /** The book manifest revision that produced this chapter row. */
    val manifestRevision: Long = 0L,
    val status: AudiobookChapterStatus = AudiobookChapterStatus.UNKNOWN,
    val usable: Boolean = false,
    val progress: Int = 0,
    val partCount: Int = 1,
    val audioArtifactId: String? = null,
    val audioSha256: String? = null,
    val audioBytes: Long? = null,
    val audioContentType: String? = null,
    val durationMs: Long? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    /** Local transport error; remote generation status remains in [status]/[errorMessage]. */
    val downloadErrorMessage: String? = null,
    val attempt: Int = 0,
    val updatedAt: String = ""
)

data class AudiobookHistoryEntry(
    val bookId: String,
    val bookTitle: String,
    val chapterId: String,
    val chapterTitle: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)

data class AudiobookLibrarySnapshot(
    val books: List<AudiobookBook> = emptyList(),
    val chapters: List<AudiobookChapter> = emptyList(),
    val history: List<AudiobookHistoryEntry> = emptyList()
)

/**
 * Observable UI state for the audiobook feature.
 *
 * Chapter details are persisted independently from the book list. The
 * revision makes that independent change observable even when the book list
 * itself is structurally equal to its previous value.
 */
data class AudiobookUiSnapshot(
    val library: AudiobookLibrarySnapshot,
    val revision: Long = 0L
)

fun publishAudiobookUiSnapshot(
    current: AudiobookUiSnapshot,
    next: AudiobookLibrarySnapshot
): AudiobookUiSnapshot = AudiobookUiSnapshot(
    library = next,
    revision = current.revision + 1L
)

fun audiobookBookStatusLabel(status: AudiobookBookStatus): String = when (status) {
    AudiobookBookStatus.ACCEPTED -> "正在排队处理"
    AudiobookBookStatus.INGESTING -> "正在接收原文"
    AudiobookBookStatus.ANALYZING -> "正在分析整本书"
    AudiobookBookStatus.SPLITTING -> "正在拆分章节"
    AudiobookBookStatus.WAITING_VOICE_CONFIRMATION -> "正在准备角色声音"
    AudiobookBookStatus.GENERATING -> "正在生成音频"
    AudiobookBookStatus.PARTIAL_READY -> "已有可听章节，后台继续生成"
    AudiobookBookStatus.PREVIEW_READY -> "代表章节已生成，可离线播放"
    AudiobookBookStatus.PREVIEW_PARTIAL_FAILED -> "部分代表章节失败，可重试"
    AudiobookBookStatus.PREVIEW_FAILED -> "代表章节生成失败，可重试"
    AudiobookBookStatus.READY -> "全部章节可听"
    AudiobookBookStatus.PARTIAL_FAILED -> "部分章节失败，可重试"
    AudiobookBookStatus.FAILED -> "生成失败，请重新查询"
    AudiobookBookStatus.CANCELLED -> "任务已取消"
    AudiobookBookStatus.EXPIRED -> "任务已过期"
    AudiobookBookStatus.UNKNOWN -> "等待同步"
}

fun audiobookBookStatusLabel(
    book: AudiobookBook,
    nowMillis: Long = System.currentTimeMillis()
): String {
    if (audiobookResourceWaitMessage(book, nowMillis) == null) {
        return audiobookBookStatusLabel(book.status)
    }
    return when {
        audiobookIsMemoryResourcePause(book) -> "等待设备资源"
        audiobookIsExplicitResourcePause(book) -> "等待空闲时段"
        else -> "低资源处理中"
    }
}

/**
 * Detail screens also have chapter rows. A worker can briefly mark the book
 * as generating while it claims a chapter and then immediately return it to
 * the resource-wait state. Use those chapter rows to avoid showing a stalled
 * task as if audio were actively progressing.
 */
fun audiobookBookStatusLabel(
    book: AudiobookBook,
    chapters: List<AudiobookChapter>,
    nowMillis: Long = System.currentTimeMillis()
): String {
    if (audiobookResourceWaitMessage(book, chapters, nowMillis) == null) {
        return audiobookBookStatusLabel(book, nowMillis)
    }
    return audiobookResourceWaitLabel(book, chapters)
}

fun audiobookServiceCardTitle(
    connectionReady: Boolean,
    transferActive: Boolean,
    transferKind: AudiobookTransferKind = AudiobookTransferKind.QUERY
): String = when {
    transferActive -> when (transferKind) {
        AudiobookTransferKind.QUERY -> "正在查询后台"
        AudiobookTransferKind.DOWNLOAD -> "正在下载章节"
        AudiobookTransferKind.OTHER -> "正在处理听书任务"
    }
    connectionReady -> "听书服务已连接"
    else -> "正在连接听书服务"
}

object AudiobookScreensUserConfigPolicy {
    const val exposesDeploymentCredentials = false
}

fun audiobookTransferActiveMessage(kind: AudiobookTransferKind): String = when (kind) {
    AudiobookTransferKind.QUERY -> "正在查询后台任务和章节进度，请稍候；已同步内容不会丢失。"
    AudiobookTransferKind.DOWNLOAD -> "正在下载已生成章节；后台状态已刷新，已下载章节仍可播放。"
    AudiobookTransferKind.OTHER -> "正在处理听书任务，请稍候；已同步内容不会丢失。"
}

fun audiobookGenerationResourceHint(): String = "工作时段会降低后台资源占用继续生成；夜间恢复完整速度；已完成章节可以直接播放。"

fun audiobookPreviewReadyHint(): String =
    "当前已准备代表章节；其他章节未纳入本次试生成，如需整本音频请重新选择整本生成。"

fun audiobookSyncedAudioHint(): String =
    "已同步的章节可离线播放；请在书籍详情查看本次生成范围与进度。"

private const val AUDIOBOOK_RESOURCE_RESTRICTED_START_MINUTES = 8 * 60 + 30
private const val AUDIOBOOK_RESOURCE_RESTRICTED_END_MINUTES = 18 * 60 + 30
private const val AUDIOBOOK_QUEUE_STALL_MILLIS = 10 * 60 * 1000L

private fun audiobookResourceWindowIsRestricted(nowMillis: Long): Boolean {
    val localTime = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    val minutes = localTime.hour * 60 + localTime.minute
    return minutes >= AUDIOBOOK_RESOURCE_RESTRICTED_START_MINUTES &&
        minutes < AUDIOBOOK_RESOURCE_RESTRICTED_END_MINUTES
}

private fun parseAudiobookTimestampMillis(value: String): Long? = runCatching {
    Instant.parse(value).toEpochMilli()
}.getOrElse {
    runCatching {
        java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }.getOrNull()
}

/** Give users a compact freshness signal for asynchronous backend state. */
fun audiobookLastSyncedLabel(
    book: AudiobookBook,
    nowMillis: Long = System.currentTimeMillis()
): String? {
    val timestamp = sequenceOf(book.updatedAt, book.createdAt)
        .mapNotNull { value ->
            value.trim().takeIf { it.isNotEmpty() }?.let(::parseAudiobookTimestampMillis)
        }
        .firstOrNull() ?: return null
    val ageMillis = (nowMillis - timestamp).coerceAtLeast(0L)
    val freshness = when {
        ageMillis < 60_000L -> "刚刚"
        ageMillis < 60L * 60_000L -> "${ageMillis / 60_000L} 分钟前"
        ageMillis < 24L * 60L * 60_000L -> "${ageMillis / (60L * 60_000L)} 小时前"
        else -> "${ageMillis / (24L * 60L * 60_000L)} 天前"
    }
    return "最近同步：$freshness"
}

private fun audiobookBookStaleMillis(book: AudiobookBook, nowMillis: Long): Long? {
    if (book.status !in setOf(
            AudiobookBookStatus.ACCEPTED,
            AudiobookBookStatus.ANALYZING,
            AudiobookBookStatus.SPLITTING,
            AudiobookBookStatus.GENERATING,
            AudiobookBookStatus.PARTIAL_READY,
        )) return null
    val timestamp = sequenceOf(book.updatedAt, book.createdAt)
        .mapNotNull { value ->
            value.trim().takeIf { it.isNotEmpty() }?.let {
                parseAudiobookTimestampMillis(it)
            }
        }
        .firstOrNull() ?: return null
    return (nowMillis - timestamp).coerceAtLeast(0L)
}

/**
 * Explain an explicit server pause, or conservatively identify a stale active
 * task during the known company daytime resource window. The latter keeps a
 * queued task from looking frozen before the worker has had a chance to report
 * its first safe pause state.
 */
private fun audiobookIsExplicitResourcePause(book: AudiobookBook): Boolean =
    book.phase == "waiting_resource_window" || audiobookIsResourcePauseCode(book.errorCode)

private fun audiobookIsResourcePauseCode(errorCode: String?): Boolean =
    errorCode == "RESOURCE_WINDOW_CLOSED" || errorCode == "RESOURCE_MEMORY_PRESSURE"

private fun audiobookIsMemoryResourcePause(book: AudiobookBook): Boolean =
    book.errorCode == "RESOURCE_MEMORY_PRESSURE"

private fun audiobookHasResourcePausedChapter(chapters: List<AudiobookChapter>): Boolean =
    chapters.any {
        it.status == AudiobookChapterStatus.WAITING_RESOURCE_WINDOW ||
            audiobookIsResourcePauseCode(it.errorCode)
    }

private fun audiobookHasActivelyRenderingChapter(chapters: List<AudiobookChapter>): Boolean =
    chapters.any { it.status == AudiobookChapterStatus.RENDERING }

private fun audiobookHasMemoryResourcePausedChapter(chapters: List<AudiobookChapter>): Boolean =
    chapters.any { it.errorCode == "RESOURCE_MEMORY_PRESSURE" }

private fun audiobookIsMemoryResourcePause(
    book: AudiobookBook,
    chapters: List<AudiobookChapter>
): Boolean = audiobookIsMemoryResourcePause(book) || audiobookHasMemoryResourcePausedChapter(chapters)

private fun audiobookResourceWaitLabel(
    book: AudiobookBook,
    chapters: List<AudiobookChapter>
): String = if (audiobookIsMemoryResourcePause(book, chapters)) "等待设备资源" else "等待空闲时段"

private fun audiobookMemoryResourceWaitMessage(): String =
    "设备当前资源不足，已暂缓生成；资源恢复后会自动继续，已完成章节可以先下载和收听。"

fun audiobookResourceWaitMessage(
    book: AudiobookBook,
    nowMillis: Long = System.currentTimeMillis()
): String? {
    if (audiobookIsExplicitResourcePause(book)) {
        if (audiobookIsMemoryResourcePause(book)) return audiobookMemoryResourceWaitMessage()
        return book.errorMessage?.trim()?.takeIf { it.isNotEmpty() }
            ?: "后台将在空闲时段继续处理；已完成章节可以先下载和收听。"
    }
    if (!audiobookResourceWindowIsRestricted(nowMillis)) return null
    if ((audiobookBookStaleMillis(book, nowMillis) ?: return null) < AUDIOBOOK_QUEUE_STALL_MILLIS) return null
    return "当前为工作时段（08:30–18:30），后台会降低资源占用继续处理；18:30 后恢复完整速度。已完成章节可以先下载和收听。"
}

fun audiobookResourceWaitMessage(
    book: AudiobookBook,
    chapters: List<AudiobookChapter>,
    nowMillis: Long = System.currentTimeMillis()
): String? {
    // A paused sibling must not hide an actively rendering chapter. The
    // chapter row still exposes the pause, while the book-level state reflects
    // the work that is actually progressing now.
    if (audiobookHasActivelyRenderingChapter(chapters)) return null
    if (!audiobookHasResourcePausedChapter(chapters)) {
        return audiobookResourceWaitMessage(book, nowMillis)
    }
    if (audiobookIsMemoryResourcePause(book, chapters)) return audiobookMemoryResourceWaitMessage()
    return audiobookResourceWaitMessage(book, nowMillis)
        ?: "后台将在空闲时段继续处理；已完成章节可以先下载和收听。"
}

fun audiobookTransferAffectsBook(
    transfer: AudiobookTransferState,
    bookId: String
): Boolean = transfer.active && (transfer.bookId.isNullOrBlank() || transfer.bookId == bookId)

private fun audiobookQueueWaitMillis(book: AudiobookBook, nowMillis: Long): Long? {
    if (book.status != AudiobookBookStatus.ACCEPTED || book.chaptersTotal > 0) return null
    val timestamp = sequenceOf(book.updatedAt, book.createdAt)
        .mapNotNull { value ->
            value.trim().takeIf { it.isNotEmpty() }?.let {
                parseAudiobookTimestampMillis(it)
            }
        }
        .firstOrNull() ?: return null
    return (nowMillis - timestamp).coerceAtLeast(0L)
}

fun audiobookBookProgressSummary(
    book: AudiobookBook,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val generationTarget = audiobookGenerationTarget(book)
    audiobookResourceWaitMessage(book, nowMillis)?.let {
        return if (book.chaptersTotal > 0) {
            audiobookReadyChapterSummary(
                book = book,
                prefix = when {
                    audiobookIsMemoryResourcePause(book) -> "等待设备资源"
                    audiobookIsExplicitResourcePause(book) -> "等待空闲时段"
                    else -> "低资源处理中"
                },
                generationTarget = generationTarget,
            )
        } else {
            it
        }
    }
    if (book.chaptersTotal <= 0) {
        return when (book.status) {
            AudiobookBookStatus.INGESTING -> "正在接收原文，章节数待返回"
            AudiobookBookStatus.ANALYZING -> if (book.progress >= 85) {
                "正在固定整本书的角色声音，章节数待返回"
            } else {
                "整本分析中，章节数待返回"
            }
            AudiobookBookStatus.SPLITTING -> "正在拆分章节，章节数待返回"
            AudiobookBookStatus.ACCEPTED -> if (
                audiobookQueueWaitMillis(book, nowMillis) ?: 0L >= AUDIOBOOK_QUEUE_STALL_MILLIS
            ) {
                "后台处理尚未开始，已等待超过 10 分钟；请点击刷新"
            } else {
                "正在排队处理，章节数待返回"
            }
            else -> "等待章节信息"
        }
    }
    val prefix = if (book.status in setOf(
            AudiobookBookStatus.PREVIEW_READY,
            AudiobookBookStatus.PREVIEW_PARTIAL_FAILED,
            AudiobookBookStatus.PREVIEW_FAILED,
        )) "代表章节进度" else "生成进度"
    return audiobookReadyChapterSummary(book, "$prefix ${book.progress}%", generationTarget)
}

fun audiobookBookProgressSummary(
    book: AudiobookBook,
    chapters: List<AudiobookChapter>,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val generationTarget = audiobookGenerationTarget(book)
    if (audiobookResourceWaitMessage(book, chapters, nowMillis) != null && book.chaptersTotal > 0) {
        return audiobookReadyChapterSummary(
            book = book,
            prefix = audiobookResourceWaitLabel(book, chapters),
            generationTarget = generationTarget,
        )
    }
    return audiobookBookProgressSummary(book, nowMillis)
}

private fun audiobookGenerationTarget(book: AudiobookBook): Int {
    val target = book.generationTargetCount
    return if (
        book.generationScope.equals("representative", ignoreCase = true) &&
            target > 0 && book.chaptersTotal > 0
    ) {
        target.coerceAtMost(book.chaptersTotal)
    } else {
        book.chaptersTotal
    }
}

private fun audiobookReadyChapterSummary(
    book: AudiobookBook,
    prefix: String,
    generationTarget: Int,
): String = if (
    book.generationScope.equals("representative", ignoreCase = true) &&
        book.generationTargetCount > 0 &&
        generationTarget < book.chaptersTotal
) {
    "$prefix · ${book.chaptersReady}/$generationTarget 个代表章节可听（全书 ${book.chaptersTotal} 章）"
} else {
    "$prefix · ${book.chaptersReady}/${book.chaptersTotal} 章可听"
}

/**
 * A partial chapter response must never authorize deleting an unknown local
 * canonical WAV. The IDs check prevents a duplicated row from masquerading
 * as a complete manifest.
 */
fun audiobookChapterSnapshotIsComplete(
    book: AudiobookBook,
    chapters: List<AudiobookChapter>
): Boolean {
    if (book.chaptersTotal <= 0) return false
    val rows = chapters.filter { it.bookId == book.id }
    val ordinals = rows.map { it.ordinal }
    return rows.size == book.chaptersTotal &&
        rows.map { it.id }.toSet().size == rows.size &&
        ordinals.toSet().size == ordinals.size &&
        ordinals.sorted() == (1..book.chaptersTotal).toList()
}

data class AudiobookChapterSnapshotMergeResult(
    val chapters: List<AudiobookChapter>,
    val snapshotComplete: Boolean
)

private fun audiobookBookSnapshotIdentityProven(
    incoming: AudiobookBook,
    local: AudiobookBook
): Boolean {
    if (incoming.sourceSha256.isBlank() || local.sourceSha256.isBlank()) return false
    if (!incoming.sourceSha256.equals(local.sourceSha256, ignoreCase = true)) return false
    if (incoming.manifestRevision != local.manifestRevision &&
        (incoming.manifestRevision > 0L || local.manifestRevision > 0L)
    ) return false
    return incoming.chaptersTotal <= 0 || local.chaptersTotal <= 0 ||
        incoming.chaptersTotal == local.chaptersTotal
}

/**
 * A detail response may be paginated or temporarily incomplete. Keep the
 * last known rows for the same source/count, but revoke the cleanup fence
 * until one response proves the complete manifest again.
 */
fun mergeAudiobookChapterSnapshot(
    incomingBook: AudiobookBook,
    incomingChapters: List<AudiobookChapter>,
    localBook: AudiobookBook?,
    localChapters: List<AudiobookChapter>
): AudiobookChapterSnapshotMergeResult {
    val incomingRows = incomingChapters.filter { it.bookId == incomingBook.id }
    val complete = incomingBook.chaptersSnapshotComplete &&
        audiobookChapterSnapshotIsComplete(incomingBook, incomingRows)
    if (complete || localBook == null || !audiobookBookSnapshotIdentityProven(incomingBook, localBook)) {
        return AudiobookChapterSnapshotMergeResult(
            chapters = incomingRows.sortedBy { it.ordinal },
            snapshotComplete = complete
        )
    }

    val incomingById = incomingRows.associateBy { it.id }
    val localRows = localChapters.filter { it.bookId == incomingBook.id }
    val localIds = localRows.mapTo(mutableSetOf()) { it.id }
    val merged = localRows.map { local -> incomingById[local.id] ?: local } +
        incomingRows.filterNot { it.id in localIds }
    return AudiobookChapterSnapshotMergeResult(
        chapters = merged.sortedBy { it.ordinal },
        snapshotComplete = false
    )
}

fun audiobookCanReconcileOrphanCanonicalFiles(book: AudiobookBook): Boolean =
    book.chaptersSnapshotComplete

fun audiobookCanReconcileOrphanCanonicalFiles(
    book: AudiobookBook,
    chapters: List<AudiobookChapter>
): Boolean =
    book.chaptersSnapshotComplete &&
        book.sourceSha256.isNotBlank() &&
        audiobookChapterSnapshotIsComplete(book, chapters)

fun audiobookLocalAudioSummary(
    chapters: List<AudiobookChapter>,
    localAvailable: (AudiobookChapter) -> Boolean
): String? {
    val ready = chapters.count { it.status == AudiobookChapterStatus.READY }
    if (ready == 0) return null
    val cached = chapters.count { it.status == AudiobookChapterStatus.READY && localAvailable(it) }
    return when {
        cached == ready -> "手机已缓存 $cached/$ready 章，可离线播放"
        cached > 0 -> "手机已缓存 $cached/$ready 章，其余已生成章节正在下载"
        else -> "远端已生成 $ready 章，手机正在下载"
    }
}

fun audiobookPlaybackLabel(state: AudiobookPlaybackState): String = when {
    state.isPlaying -> "正在播放"
    state.durationMs > 0L && state.positionMs >= state.durationMs -> "本章已播放完"
    state.durationMs > 0L && state.positionMs > 0L -> "已暂停"
    else -> "未播放"
}

fun audiobookPlaybackErrorLabel(state: AudiobookPlaybackState): String =
    state.errorMessage?.takeIf { it.isNotBlank() }?.let { "播放失败：$it" }.orEmpty()

fun audiobookPlaybackActionLabel(state: AudiobookPlaybackState): String = when {
    state.isPlaying -> "暂停"
    state.durationMs > 0L && state.positionMs >= state.durationMs -> "从头播放"
    else -> "继续"
}

fun shouldAutoDownloadChapter(status: AudiobookChapterStatus, localAvailable: Boolean): Boolean =
    status == AudiobookChapterStatus.READY && !localAvailable

fun canPlayAudiobookChapter(status: AudiobookChapterStatus, localAvailable: Boolean): Boolean =
    status == AudiobookChapterStatus.READY && localAvailable

fun audiobookChapterStatusLabel(chapter: AudiobookChapter, localAvailable: Boolean): String = when {
    localAvailable -> "已下载，可播放"
    !chapter.downloadErrorMessage.isNullOrBlank() -> "下载失败，可重试"
    chapter.status == AudiobookChapterStatus.READY -> "已生成，正在下载"
    chapter.status == AudiobookChapterStatus.WAITING_RESOURCE_WINDOW ||
        chapter.errorCode == "RESOURCE_WINDOW_CLOSED" -> "等待空闲时段"
    chapter.status == AudiobookChapterStatus.RENDERING -> "音频生成中 ${chapter.progress}%"
    chapter.status == AudiobookChapterStatus.FAILED_RETRYABLE -> "生成失败，可重新生成"
    chapter.status == AudiobookChapterStatus.FAILED_PERMANENT -> "生成失败，可重新生成"
    chapter.status == AudiobookChapterStatus.NOT_SELECTED -> "未纳入本次试生成"
    else -> "等待生成"
}

fun audiobookChapterActionLabel(chapter: AudiobookChapter, localAvailable: Boolean): String? = when {
    localAvailable || chapter.status != AudiobookChapterStatus.READY -> null
    !chapter.downloadErrorMessage.isNullOrBlank() -> "重试下载"
    else -> "下载"
}

fun audiobookChapterActionHint(chapter: AudiobookChapter): String? = when (chapter.status) {
    AudiobookChapterStatus.PENDING -> "等待生成"
    AudiobookChapterStatus.ANNOTATING -> "分析中"
    AudiobookChapterStatus.READY_TO_RENDER -> "待生成"
    AudiobookChapterStatus.RENDERING -> "生成中"
    AudiobookChapterStatus.UPLOADING -> "上传中"
    AudiobookChapterStatus.VERIFYING -> "校验中"
    AudiobookChapterStatus.WAITING_RESOURCE_WINDOW -> "等待空闲"
    AudiobookChapterStatus.NEEDS_REVIEW -> "待检查"
    AudiobookChapterStatus.CANCELLED -> "已取消"
    AudiobookChapterStatus.UNKNOWN -> "等待同步"
    AudiobookChapterStatus.READY,
    AudiobookChapterStatus.FAILED_RETRYABLE,
    AudiobookChapterStatus.FAILED_PERMANENT,
    AudiobookChapterStatus.NOT_SELECTED -> null
}

/** A published failed chapter can be requeued without rebuilding the book manifest. */
fun canRetryAudiobookChapterGeneration(
    chapter: AudiobookChapter,
    transferActive: Boolean = false,
): Boolean = !transferActive &&
    chapter.manifestRevision > 0L &&
    chapter.status in setOf(
        AudiobookChapterStatus.FAILED_RETRYABLE,
        AudiobookChapterStatus.FAILED_PERMANENT,
    )

fun audiobookChapterHistoryLabel(history: AudiobookHistoryEntry?): String? {
    val entry = history ?: return null
    val duration = entry.durationMs
    if (duration <= 0L) return null
    val position = entry.positionMs.coerceAtLeast(0L)
    return if (position >= (duration - PLAYBACK_RESTART_TAIL_MS).coerceAtLeast(0L)) {
        "已听完，再听从头开始"
    } else {
        "继续听到 ${(position.coerceAtMost(duration) * 100L / duration).toInt().coerceIn(0, 99)}%"
    }
}

fun audiobookBookRecoveryActionLabel(status: AudiobookBookStatus, transferActive: Boolean = false): String? = when (status) {
    AudiobookBookStatus.FAILED,
    AudiobookBookStatus.PARTIAL_FAILED -> if (transferActive) "正在查询…" else "重新查询"
    else -> null
}

/**
 * A failed book can be resubmitted only before a chapter manifest exists.
 * This mirrors the server fence so the client never presents an action that
 * would overwrite an already-published chapter snapshot.
 */
fun audiobookCanRetryAnalysis(
    book: AudiobookBook,
    transferActive: Boolean = false
): Boolean = !transferActive &&
    book.status == AudiobookBookStatus.FAILED &&
    book.sourceStatus.equals("complete", ignoreCase = true) &&
    book.manifestRevision == 0L &&
    book.chaptersTotal == 0

fun audiobookBookRecoveryActionLabel(book: AudiobookBook, transferActive: Boolean = false): String? = when {
    audiobookCanRetryAnalysis(book, transferActive) -> "重新提交分析"
    book.status == AudiobookBookStatus.FAILED || book.status == AudiobookBookStatus.PARTIAL_FAILED ->
        if (transferActive) "正在查询…" else "重新查询"
    else -> null
}

/** Keep immediately playable books discoverable without changing the server's task order. */
fun audiobookBookDisplayOrder(books: List<AudiobookBook>): List<AudiobookBook> =
    books.sortedWith(compareBy { audiobookBookDisplayPriority(it) })

internal enum class AudiobookShelfSection {
    REMOTE_BOOKS,
    LOCAL_IMPORTS,
}

/** Render server-backed audiobooks before the optional local import list. */
internal fun audiobookShelfSectionOrder(hasRemoteBooks: Boolean): List<AudiobookShelfSection> =
    if (hasRemoteBooks) {
        listOf(AudiobookShelfSection.REMOTE_BOOKS, AudiobookShelfSection.LOCAL_IMPORTS)
    } else {
        listOf(AudiobookShelfSection.LOCAL_IMPORTS)
    }

/**
 * A successful list response is not a deletion command. Keep local records
 * that are still navigable while a server snapshot is incomplete or delayed.
 * For an existing id the server remains authoritative, except for the local
 * source URI which is the phone's own offline pointer.
 */
fun mergeAudiobookBooks(
    remoteBooks: List<AudiobookBook>,
    localBooks: List<AudiobookBook>
): List<AudiobookBook> {
    val localById = localBooks.associateBy { it.id }
    val remoteIds = remoteBooks.mapTo(mutableSetOf()) { it.id }
    val refreshed = remoteBooks.map { remote ->
        mergeAudiobookBookLocalState(remote, localById[remote.id])
    }
    return refreshed + localBooks.filterNot { it.id in remoteIds }
}

/** Preserve phone-owned metadata while accepting newer server book fields. */
fun mergeAudiobookBookLocalState(
    incoming: AudiobookBook,
    local: AudiobookBook?
): AudiobookBook {
    if (local == null) return incoming
    return incoming.copy(
        sourceUri = incoming.sourceUri ?: local.sourceUri,
        chaptersSnapshotComplete = incoming.chaptersSnapshotComplete ||
            (local.chaptersSnapshotComplete && audiobookBookSnapshotIdentityProven(incoming, local))
    )
}

/**
 * Apply a download result only to the same remote audio revision that was
 * downloaded. A late completion must never overwrite a newer chapter row.
 */
fun mergeDownloadedAudiobookChapter(
    current: AudiobookChapter?,
    downloaded: AudiobookChapter
): AudiobookChapter? {
    if (current == null || current.id != downloaded.id || current.bookId != downloaded.bookId) return null
    if (current.status != AudiobookChapterStatus.READY || downloaded.status != AudiobookChapterStatus.READY) return null
    if (current.sourceSpanSha256 != downloaded.sourceSpanSha256 ||
        (current.manifestRevision != downloaded.manifestRevision &&
            (current.manifestRevision > 0L || downloaded.manifestRevision > 0L)) ||
        current.audioArtifactId != downloaded.audioArtifactId ||
        !current.audioSha256.orEmpty().equals(downloaded.audioSha256.orEmpty(), ignoreCase = true) ||
        current.audioBytes != downloaded.audioBytes ||
        current.audioContentType != downloaded.audioContentType ||
        current.durationMs != downloaded.durationMs
    ) return null
    if (downloaded.audioSha256.isNullOrBlank() || downloaded.audioBytes == null || downloaded.audioBytes <= 0L) return null
    return current.copy(downloadErrorMessage = null)
}

/**
 * Build the identity used by the resumable phone download cache. It includes
 * the remote artifact metadata so a new generated revision never resumes an
 * older revision's bytes.
 */
fun audiobookChapterAudioIdentity(chapter: AudiobookChapter): String = buildString {
    append("book=").append(chapter.bookId)
    append("|chapter=").append(chapter.id)
    append("|manifestRevision=").append(chapter.manifestRevision)
    append("|sourceSpan=").append(chapter.sourceSpanSha256.lowercase())
    append("|artifact=").append(chapter.audioArtifactId.orEmpty())
    append("|sha=").append(chapter.audioSha256.orEmpty().lowercase())
    append("|bytes=").append(chapter.audioBytes ?: "")
    append("|contentType=").append(chapter.audioContentType.orEmpty())
    append("|duration=").append(chapter.durationMs ?: "")
}

/** Stable, filesystem-safe suffix for a chapter's remote audio revision. */
fun audiobookDownloadRevisionSuffix(chapter: AudiobookChapter): String =
    MessageDigest.getInstance("SHA-256")
        .digest(audiobookChapterAudioIdentity(chapter).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        .take(16)

/**
 * Download into a revision-isolated sibling of the canonical target. A late
 * completion can therefore only contend at the guarded repository commit,
 * never while writing the canonical audio file itself.
 */
fun audiobookDownloadArtifactName(targetName: String, chapter: AudiobookChapter): String {
    val extensionIndex = targetName.lastIndexOf('.')
    val stem = if (extensionIndex > 0) targetName.substring(0, extensionIndex) else targetName
    val extension = if (extensionIndex > 0) targetName.substring(extensionIndex) else ""
    // Keep revision-isolated output outside the canonical audio namespace. A
    // completed-but-not-yet-committed artifact must not look like an orphan
    // canonical audio file to startup reconciliation.
    return "$stem.${audiobookDownloadRevisionSuffix(chapter)}$extension.pending"
}

/** Pick the local cache extension from the server's verified media type. */
fun audiobookAudioFileExtension(contentType: String?): String = when (
    contentType.orEmpty().substringBefore(';').trim().lowercase()
) {
    "audio/mpeg", "audio/mp3" -> ".mp3"
    "audio/wav", "audio/x-wav", "audio/wave" -> ".wav"
    else -> ".wav"
}

/** A tiny sidecar name used to bind the canonical audio to its verified revision. */
fun audiobookAudioCacheMarkerName(targetName: String): String = "$targetName.meta"

/**
 * Check the cheap local cache gate without hashing on the UI thread. The
 * marker is written only after the downloader has verified bytes and SHA-256;
 * matching it prevents an equal-length older audio artifact from being reused.
 */
fun audiobookAudioCacheIsValid(
    fileExists: Boolean,
    actualBytes: Long,
    chapter: AudiobookChapter,
    cachedIdentity: String?
): Boolean = fileExists &&
    chapter.audioSha256?.isNotBlank() == true &&
    chapter.audioBytes?.let { it > 0L && actualBytes == it } == true &&
    cachedIdentity == audiobookChapterAudioIdentity(chapter)

/** Ignore late callbacks from a superseded refresh/download operation. */
fun audiobookOperationIsCurrent(expectedGeneration: Long, currentGeneration: Long): Boolean =
    expectedGeneration == currentGeneration

/** A refresh result buffer must be able to receive every listed book. */
fun audiobookRefreshResultBufferCapacity(totalBooks: Int): Int = totalBooks.coerceAtLeast(1)

/** Keep resumable bytes for cancellation and transient I/O failures. */
fun shouldRetainAudiobookPartial(error: Throwable): Boolean =
    error is CancellationException || error is IOException

private fun audiobookBookDisplayPriority(book: AudiobookBook): Int = when {
    book.usable || book.status == AudiobookBookStatus.PREVIEW_READY || book.status == AudiobookBookStatus.READY -> 0
    book.chaptersReady > 0 || book.status == AudiobookBookStatus.PARTIAL_READY -> 1
    book.status == AudiobookBookStatus.FAILED ||
        book.status == AudiobookBookStatus.PARTIAL_FAILED ||
        book.status == AudiobookBookStatus.PREVIEW_PARTIAL_FAILED ||
        book.status == AudiobookBookStatus.PREVIEW_FAILED -> 2
    book.status == AudiobookBookStatus.UNKNOWN -> 4
    else -> 3
}

fun audiobookRefreshMessage(failedChapterTitles: List<String>): String =
    if (failedChapterTitles.isEmpty()) ""
    else "后台状态已刷新；${failedChapterTitles.joinToString("、")}下载失败，可重试"

fun audiobookRefreshProgressMessage(completed: Int, total: Int): String {
    val safeTotal = total.coerceAtLeast(0)
    if (safeTotal == 0) return "正在查询后台任务…"
    return "正在查询后台任务 ${completed.coerceIn(0, safeTotal)}/$safeTotal…"
}

fun audiobookRefreshTimeoutMessage(timeoutSeconds: Int): String =
    "后台查询超过${timeoutSeconds.coerceAtLeast(1)}秒仍未完成；已保留已同步内容，可稍后重试。"

fun audiobookRefreshResultMessage(
    failedBookTitles: List<String>,
    failedChapterTitles: List<String>
): String {
    val details = buildList {
        if (failedBookTitles.isNotEmpty()) {
            add("${failedBookTitles.joinToString("、")}状态暂未返回")
        }
        if (failedChapterTitles.isNotEmpty()) {
            add("${failedChapterTitles.joinToString("、")}下载失败，可重试")
        }
    }
    return if (details.isEmpty()) "后台状态已刷新" else "后台状态已刷新；${details.joinToString("；")}"
}

fun audiobookExistingTaskMessage(bookTitle: String, failedChapterTitles: List<String>): String =
    if (failedChapterTitles.isEmpty()) {
        "《$bookTitle》已存在，已恢复后台进度"
    } else {
        "《$bookTitle》已存在，已恢复后台进度；${failedChapterTitles.joinToString("、")}下载失败，可重试"
    }

/** Restart a chapter after it has reached the end; otherwise keep its saved position. */
fun playbackResumePosition(positionMs: Long?, durationMs: Long?): Long {
    val position = positionMs ?: return 0L
    val duration = durationMs ?: return position.coerceAtLeast(0L)
    if (duration <= 0L || position >= (duration - PLAYBACK_RESTART_TAIL_MS).coerceAtLeast(0L)) return 0L
    return position.coerceAtLeast(0L)
}

private const val PLAYBACK_RESTART_TAIL_MS = 1_000L

fun audiobookProgressPercent(ready: Int, failed: Int, total: Int): Int {
    if (total <= 0) return 0
    if (ready >= total) return 100
    return (ready.coerceIn(0, total) * 100 / total).coerceIn(0, 99)
}

fun friendlyAudiobookHttpError(code: Int, body: String): String {
    val compact = body.replace(Regex("\\s+"), " ").trim()
    val isHtml = compact.startsWith("<") ||
        compact.contains("<html", ignoreCase = true) ||
        compact.contains("<!doctype", ignoreCase = true)
    if (isHtml) {
        return "听书服务暂时不可用，请稍后重试"
    }
    return when (code) {
        401 -> "听书服务连接已更新，请稍后重试"
        403 -> "当前网络暂时无法访问听书服务，请稍后重试"
        404 -> "找不到请求的听书内容，请刷新后重试"
        409 -> "这本书的导入任务发生冲突，请刷新书架后重试"
        in 500..599 -> "听书服务暂时繁忙，请稍后重试"
        else -> "听书服务暂时不可用，请稍后重试"
    }
}
