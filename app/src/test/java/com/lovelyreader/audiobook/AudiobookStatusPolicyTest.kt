package com.lovelyreader.audiobook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException

class AudiobookStatusPolicyTest {
    @Test
    fun `audiobook client uses the current shared production contract`() {
        assertEquals("lovely-reader-team-v4", AudiobookPipelineContract.POLICY_ID)
        assertEquals(
            "51337ffccbbc664444d07c0e6ce6563bcffc1a0d34af146ea2e5e2d61b3b7cac",
            AudiobookPipelineContract.POLICY_SHA256
        )
        assertEquals(
            "9105d1c7eeb5f11d8ac34ec9088a651da42db34bd008a307ced4cb46774e1b53",
            AudiobookPipelineContract.PIPELINE_FINGERPRINT
        )
    }

    @Test
    fun `audio cache follows the verified remote content type`() {
        assertEquals(".mp3", audiobookAudioFileExtension("audio/mpeg"))
        assertEquals(".mp3", audiobookAudioFileExtension("audio/mp3; charset=binary"))
        assertEquals(".wav", audiobookAudioFileExtension("audio/wav"))
        assertEquals(".wav", audiobookAudioFileExtension(null))
    }

    @Test
    fun `downloaded novel debug import explicitly targets representative chapters`() {
        assertEquals("representative", AudiobookPipelineContract.DEFAULT_GENERATION_SCOPE)
        assertEquals("full", AudiobookPipelineContract.FULL_GENERATION_SCOPE)
    }

    @Test
    fun `audiobook connection is automatic and has no user editable config`() {
        assertEquals("https://lovely-reader-voice-lab.icarus-heeya.chatgpt.site", AudiobookConfigStore.DEFAULT_BASE_URL)
        assertEquals("正在连接听书服务", audiobookServiceCardTitle(connectionReady = false, transferActive = false))
        assertEquals("听书服务已连接", audiobookServiceCardTitle(connectionReady = true, transferActive = false))
    }

    @Test
    fun `generation resource hint keeps background waiting understandable without exposing internals`() {
        val hint = audiobookGenerationResourceHint()

        assertEquals("工作时段会降低后台资源占用继续生成；夜间恢复完整速度；已完成章节可以直接播放。", hint)
        assertFalse(hint.contains("公司电脑"))
        assertFalse(hint.contains("令牌"))
        assertFalse(hint.contains("模型"))
    }

    @Test
    fun `resource window pause is shown as waiting instead of failure`() {
        val book = AudiobookBook(
            "book-1",
            "book-1",
            "样书",
            "",
            status = AudiobookBookStatus.GENERATING,
            phase = "waiting_resource_window",
            chaptersTotal = 49,
            chaptersReady = 1,
            errorCode = "RESOURCE_WINDOW_CLOSED",
            errorMessage = "后台将在空闲时段继续处理，预计下次开始时间：2026-09-03T18:30+08:00（本机时间）。"
        )

        assertEquals("等待空闲时段", audiobookBookStatusLabel(book))
        assertEquals("等待空闲时段 · 1/49 章可听", audiobookBookProgressSummary(book))
        assertEquals(book.errorMessage, audiobookResourceWaitMessage(book))
        assertEquals(
            AudiobookChapterStatus.WAITING_RESOURCE_WINDOW,
            AudiobookChapterStatus.fromWire("waiting_resource_window")
        )
    }

    @Test
    fun `chapter memory pause prevents detail page from claiming audio is still generating`() {
        val book = AudiobookBook(
            "book-1",
            "book-1",
            "样书",
            "",
            status = AudiobookBookStatus.GENERATING,
            phase = "generating",
            progress = 41,
            chaptersTotal = 49,
            chaptersReady = 2,
            generationScope = "representative",
            generationTargetCount = 5
        )
        val chapters = listOf(
            AudiobookChapter(
                id = "chapter-11",
                bookId = book.id,
                ordinal = 11,
                title = "Part 11",
                status = AudiobookChapterStatus.WAITING_RESOURCE_WINDOW,
                errorCode = "RESOURCE_MEMORY_PRESSURE",
                errorMessage = "系统内存余量不足以安全加载 TTS 模型。"
            )
        )

        assertEquals("等待设备资源", audiobookBookStatusLabel(book, chapters))
        assertEquals(
            "等待设备资源 · 2/5 个代表章节可听（全书 49 章）",
            audiobookBookProgressSummary(book, chapters)
        )
        assertEquals(
            "设备当前资源不足，已暂缓生成；资源恢复后会自动继续，已完成章节可以先下载和收听。",
            audiobookResourceWaitMessage(book, chapters)
        )
    }

    @Test
    fun `active chapter keeps detail generation state when another chapter waits for memory`() {
        val book = AudiobookBook(
            "book-1",
            "book-1",
            "样书",
            "",
            status = AudiobookBookStatus.GENERATING,
            phase = "generating",
            progress = 41,
            chaptersTotal = 49,
            chaptersReady = 2,
            generationScope = "representative",
            generationTargetCount = 5
        )
        val chapters = listOf(
            AudiobookChapter(
                id = "chapter-11",
                bookId = book.id,
                ordinal = 11,
                title = "Part 11",
                status = AudiobookChapterStatus.WAITING_RESOURCE_WINDOW,
                errorCode = "RESOURCE_MEMORY_PRESSURE",
                errorMessage = "系统内存余量不足以安全加载 TTS 模型。"
            ),
            AudiobookChapter(
                id = "chapter-13",
                bookId = book.id,
                ordinal = 13,
                title = "Part 13",
                status = AudiobookChapterStatus.RENDERING,
                progress = 5
            )
        )

        assertNull(audiobookResourceWaitMessage(book, chapters))
        assertEquals("正在生成音频", audiobookBookStatusLabel(book, chapters))
        assertEquals(
            "生成进度 41% · 2/5 个代表章节可听（全书 49 章）",
            audiobookBookProgressSummary(book, chapters)
        )
    }

    @Test
    fun `detail snapshot revision changes when a chapter becomes actively rendering`() {
        val book = AudiobookBook("book-1", "book-1", "样书", "", chaptersTotal = 2, chaptersReady = 1)
        val pending = AudiobookChapter("chapter-2", book.id, 2, "第二章", status = AudiobookChapterStatus.PENDING)
        val rendering = pending.copy(status = AudiobookChapterStatus.RENDERING, progress = 23)
        val initial = AudiobookUiSnapshot(
            AudiobookLibrarySnapshot(books = listOf(book), chapters = listOf(pending)),
            revision = 0L
        )

        val updated = publishAudiobookUiSnapshot(
            initial,
            AudiobookLibrarySnapshot(books = listOf(book), chapters = listOf(rendering))
        )

        assertEquals(1L, updated.revision)
        assertEquals(AudiobookChapterStatus.RENDERING, updated.library.chapters.single().status)
        assertEquals(23, updated.library.chapters.single().progress)
    }

    @Test
    fun `stale daytime queue is shown as a planned resource wait`() {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.of(2026, 9, 3, 11, 0, 0, 0, zone)
        val book = AudiobookBook(
            "book-1",
            "book-1",
            "样书",
            "",
            status = AudiobookBookStatus.ACCEPTED,
            phase = "queued",
            createdAt = now.minusHours(2).toOffsetDateTime().toString(),
            updatedAt = now.minusHours(2).toOffsetDateTime().toString()
        )

        assertEquals("低资源处理中", audiobookBookStatusLabel(book, now.toInstant().toEpochMilli()))
        assertEquals(
            "当前为工作时段（08:30–18:30），后台会降低资源占用继续处理；18:30 后恢复完整速度。已完成章节可以先下载和收听。",
            audiobookResourceWaitMessage(book, now.toInstant().toEpochMilli())
        )
    }

    @Test
    fun `html edge errors become a short actionable message`() {
        val html = "<!DOCTYPE html><html><head><title>Sorry, you have been blocked</title></head><body>lots of edge markup</body></html>"

        assertEquals(
            "听书服务暂时不可用，请稍后重试",
            friendlyAudiobookHttpError(403, html)
        )
        assertEquals("听书服务暂时繁忙，请稍后重试", friendlyAudiobookHttpError(503, "backend detail"))
        assertEquals(
            "这本书的导入任务发生冲突，请刷新书架后重试",
            friendlyAudiobookHttpError(409, "Idempotency-Key 已被不同任务使用。")
        )
        assertFalse(friendlyAudiobookHttpError(403, html).contains("HTTP"))
        assertFalse(friendlyAudiobookHttpError(403, html).contains("代理"))
    }

    @Test
    fun `book status text keeps partial readiness distinct from generation`() {
        assertEquals("正在分析整本书", audiobookBookStatusLabel(AudiobookBookStatus.ANALYZING))
        assertEquals("已有可听章节，后台继续生成", audiobookBookStatusLabel(AudiobookBookStatus.PARTIAL_READY))
        assertEquals("全部章节可听", audiobookBookStatusLabel(AudiobookBookStatus.READY))
    }

    @Test
    fun `preview ready wire status is visible as downloadable representative audio`() {
        assertEquals(AudiobookBookStatus.PREVIEW_READY, AudiobookBookStatus.fromWire("preview_ready"))
        assertEquals("代表章节已生成，可离线播放", audiobookBookStatusLabel(AudiobookBookStatus.PREVIEW_READY))
        assertEquals(
            "当前已准备代表章节；其他章节未纳入本次试生成，如需整本音频请重新选择整本生成。",
            audiobookPreviewReadyHint()
        )
        assertEquals(
            "已同步的章节可离线播放；请在书籍详情查看本次生成范围与进度。",
            audiobookSyncedAudioHint()
        )
    }

    @Test
    fun `preview failure wire statuses stay visible and actionable`() {
        assertEquals(AudiobookBookStatus.PREVIEW_PARTIAL_FAILED, AudiobookBookStatus.fromWire("preview_partial_failed"))
        assertEquals(AudiobookBookStatus.PREVIEW_FAILED, AudiobookBookStatus.fromWire("preview_failed"))
        assertEquals("部分代表章节失败，可重试", audiobookBookStatusLabel(AudiobookBookStatus.PREVIEW_PARTIAL_FAILED))
        assertEquals("代表章节生成失败，可重试", audiobookBookStatusLabel(AudiobookBookStatus.PREVIEW_FAILED))
        assertEquals(
            "代表章节进度 0% · 0/49 章可听",
            audiobookBookProgressSummary(
                AudiobookBook("book-1", "book-1", "样书", "", status = AudiobookBookStatus.PREVIEW_FAILED, chaptersTotal = 49)
            )
        )
    }

    @Test
    fun `only verified ready chapter can be downloaded or played`() {
        assertTrue(shouldAutoDownloadChapter(AudiobookChapterStatus.READY, false))
        assertFalse(shouldAutoDownloadChapter(AudiobookChapterStatus.RENDERING, false))
        assertFalse(shouldAutoDownloadChapter(AudiobookChapterStatus.READY, true))
        assertTrue(canPlayAudiobookChapter(AudiobookChapterStatus.READY, true))
        assertFalse(canPlayAudiobookChapter(AudiobookChapterStatus.READY, false))
    }

    @Test
    fun `progress never exceeds the verified boundary`() {
        assertEquals(0, audiobookProgressPercent(0, 0, 5))
        assertEquals(40, audiobookProgressPercent(2, 0, 5))
        assertEquals(100, audiobookProgressPercent(5, 0, 5))
        assertEquals(0, audiobookProgressPercent(9, 0, 0))
    }

    @Test
    fun `completed chapter resumes from the beginning while an unfinished chapter keeps its position`() {
        assertEquals(0L, playbackResumePosition(118_549L, 118_540L))
        assertEquals(42_000L, playbackResumePosition(42_000L, 118_540L))
    }

    @Test
    fun `playback card distinguishes playing paused and completed states`() {
        assertEquals(
            "正在播放",
            audiobookPlaybackLabel(AudiobookPlaybackState(isPlaying = true, positionMs = 2_000L, durationMs = 80_200L))
        )
        assertEquals(
            "已暂停",
            audiobookPlaybackLabel(AudiobookPlaybackState(isPlaying = false, positionMs = 2_000L, durationMs = 80_200L))
        )
        assertEquals(
            "本章已播放完",
            audiobookPlaybackLabel(AudiobookPlaybackState(isPlaying = false, positionMs = 80_200L, durationMs = 80_200L))
        )
    }

    @Test
    fun `refresh keeps automatic chapter download failures visible`() {
        assertEquals("", audiobookRefreshMessage(emptyList()))
        assertEquals(
            "后台状态已刷新；第一章、第二章下载失败，可重试",
            audiobookRefreshMessage(listOf("第一章", "第二章"))
        )
    }

    @Test
    fun `manual chapter download always leaves the transfer idle`() {
        val success = audiobookDownloadResultState("book-1", "第一章", null)
        val failure = audiobookDownloadResultState("book-1", "第一章", IllegalStateException("网络中断"))

        assertFalse(success.active)
        assertEquals("《第一章》下载完成", success.message)
        assertFalse(failure.active)
        assertEquals("《第一章》下载失败：网络中断", failure.message)
    }

    @Test
    fun `chapter download failure remains visible as a retryable local state`() {
        val chapter = AudiobookChapter(
            id = "chapter-1",
            bookId = "book-1",
            ordinal = 1,
            title = "第一章",
            status = AudiobookChapterStatus.READY,
            downloadErrorMessage = "网络中断"
        )

        assertEquals("下载失败，可重试", audiobookChapterStatusLabel(chapter, false))
        assertEquals("已下载，可播放", audiobookChapterStatusLabel(chapter, true))
    }

    @Test
    fun `permanent chapter generation failures expose an explicit retry action`() {
        val failed = AudiobookChapter(
            id = "chapter-1",
            bookId = "book-1",
            ordinal = 1,
            title = "第一章",
            manifestRevision = 1L,
            status = AudiobookChapterStatus.FAILED_PERMANENT
        )

        assertEquals("生成失败，可重新生成", audiobookChapterStatusLabel(failed, false))
        assertTrue(canRetryAudiobookChapterGeneration(failed))
        assertFalse(canRetryAudiobookChapterGeneration(failed, transferActive = true))
        assertFalse(canRetryAudiobookChapterGeneration(failed.copy(manifestRevision = 0L)))
        assertFalse(canRetryAudiobookChapterGeneration(failed.copy(status = AudiobookChapterStatus.READY)))
    }

    @Test
    fun `existing task restore message does not hide chapter download failures`() {
        assertEquals(
            "《样书》已存在，已恢复后台进度",
            audiobookExistingTaskMessage("样书", emptyList())
        )
        assertEquals(
            "《样书》已存在，已恢复后台进度；第一章、第二章下载失败，可重试",
            audiobookExistingTaskMessage("样书", listOf("第一章", "第二章"))
        )
    }

    @Test
    fun `book progress explains that chapter count is not available yet`() {
        assertEquals(
            "整本分析中，章节数待返回",
            audiobookBookProgressSummary(AudiobookBook("book-1", "book-1", "样书", "", status = AudiobookBookStatus.ANALYZING))
        )
        assertEquals(
            "正在固定整本书的角色声音，章节数待返回",
            audiobookBookProgressSummary(
                AudiobookBook("book-1", "book-1", "样书", "", status = AudiobookBookStatus.ANALYZING, progress = 90)
            )
        )
        assertEquals(
            "生成进度 40% · 2/5 章可听",
            audiobookBookProgressSummary(AudiobookBook("book-1", "book-1", "样书", "", progress = 40, chaptersTotal = 5, chaptersReady = 2))
        )
    }

    @Test
    fun `representative progress is counted against the selected generation target`() {
        assertEquals(
            "生成进度 43% · 2/5 个代表章节可听（全书 49 章）",
            audiobookBookProgressSummary(
                AudiobookBook(
                    "book-1",
                    "book-1",
                    "样书",
                    "",
                    progress = 43,
                    generationScope = "representative",
                    generationTargetCount = 5,
                    chaptersTotal = 49,
                    chaptersReady = 2
                )
            )
        )
    }

    @Test
    fun `not selected chapter is not presented as waiting for generation`() {
        val chapter = AudiobookChapter(
            id = "chapter-2",
            bookId = "book-1",
            ordinal = 2,
            title = "第二章",
            status = AudiobookChapterStatus.NOT_SELECTED
        )

        assertEquals(
            AudiobookChapterStatus.NOT_SELECTED,
            AudiobookChapterStatus.fromWire("not_selected")
        )
        assertEquals("未纳入本次试生成", audiobookChapterStatusLabel(chapter, false))
    }

    @Test
    fun `chapter row action hint does not say waiting while rendering`() {
        val rendering = AudiobookChapter(
            id = "chapter-1",
            bookId = "book-1",
            ordinal = 1,
            title = "第一章",
            status = AudiobookChapterStatus.RENDERING,
            progress = 55
        )
        val pending = rendering.copy(status = AudiobookChapterStatus.PENDING, progress = 0)

        assertEquals("生成中", audiobookChapterActionHint(rendering))
        assertEquals("等待生成", audiobookChapterActionHint(pending))
    }

    @Test
    fun `queued book explains when processing has not started for ten minutes`() {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.of(2026, 9, 2, 2, 10, 1, 0, zone)
        val book = AudiobookBook(
            "book-1",
            "book-1",
            "样书",
            "",
            status = AudiobookBookStatus.ACCEPTED,
            phase = "queued",
            updatedAt = now.minusMinutes(10).toOffsetDateTime().toString()
        )

        assertEquals(
            "后台处理尚未开始，已等待超过 10 分钟；请点击刷新",
            audiobookBookProgressSummary(
                book,
                nowMillis = now.toInstant().toEpochMilli()
            )
        )
    }

    @Test
    fun `only a complete persisted chapter manifest authorizes orphan cleanup`() {
        val incomplete = AudiobookBook("book-1", "book-1", "样书", "")
        val complete = incomplete.copy(chaptersSnapshotComplete = true)

        assertFalse(audiobookCanReconcileOrphanCanonicalFiles(incomplete))
        assertTrue(audiobookCanReconcileOrphanCanonicalFiles(complete))
    }

    @Test
    fun `incomplete detail keeps the last known chapter rows and revokes orphan cleanup authority`() {
        val book = AudiobookBook(
            id = "book-1",
            clientBookId = "book-1",
            title = "样书",
            author = "作者",
            sourceSha256 = "source-v1",
            chaptersTotal = 2,
            chaptersSnapshotComplete = true
        )
        val local = listOf(
            AudiobookChapter("chapter-1", "book-1", 1, "第一章", sourceSpanSha256 = "span-1"),
            AudiobookChapter("chapter-2", "book-1", 2, "第二章", sourceSpanSha256 = "span-2")
        )
        val partial = listOf(local[0].copy(title = "第一章（更新）"))

        val merged = mergeAudiobookChapterSnapshot(
            incomingBook = book.copy(chaptersSnapshotComplete = true),
            incomingChapters = partial,
            localBook = book,
            localChapters = local
        )

        assertFalse(merged.snapshotComplete)
        assertEquals(listOf("chapter-1", "chapter-2"), merged.chapters.map { it.id })
        assertEquals("第一章（更新）", merged.chapters.first().title)
        assertFalse(audiobookCanReconcileOrphanCanonicalFiles(book.copy(chaptersSnapshotComplete = merged.snapshotComplete)))
    }

    @Test
    fun `incomplete detail from a new source does not resurrect old chapter rows`() {
        val oldBook = AudiobookBook(
            id = "book-1",
            clientBookId = "book-1",
            title = "旧书",
            author = "作者",
            sourceSha256 = "source-v1",
            chaptersTotal = 2,
            chaptersSnapshotComplete = true
        )
        val newBook = oldBook.copy(title = "新书", sourceSha256 = "source-v2")
        val oldChapters = listOf(
            AudiobookChapter("old-1", "book-1", 1, "旧第一章", sourceSpanSha256 = "old-span-1"),
            AudiobookChapter("old-2", "book-1", 2, "旧第二章", sourceSpanSha256 = "old-span-2")
        )
        val partial = listOf(AudiobookChapter("new-1", "book-1", 1, "新第一章", sourceSpanSha256 = "new-span-1"))

        val merged = mergeAudiobookChapterSnapshot(newBook, partial, oldBook, oldChapters)

        assertFalse(merged.snapshotComplete)
        assertEquals(listOf("new-1"), merged.chapters.map { it.id })
    }

    @Test
    fun `incomplete detail without a source identity does not reuse old chapter rows`() {
        val oldBook = AudiobookBook(
            id = "book-1",
            clientBookId = "book-1",
            title = "旧书",
            author = "作者",
            sourceSha256 = "source-v1",
            chaptersTotal = 2,
            chaptersSnapshotComplete = true
        )
        val partialWithoutIdentity = oldBook.copy(
            title = "待确认的新书",
            sourceSha256 = "",
            chaptersSnapshotComplete = false
        )
        val oldChapters = listOf(
            AudiobookChapter("old-1", "book-1", 1, "旧第一章", sourceSpanSha256 = "old-span-1"),
            AudiobookChapter("old-2", "book-1", 2, "旧第二章", sourceSpanSha256 = "old-span-2")
        )
        val partial = listOf(AudiobookChapter("new-1", "book-1", 1, "待确认第一章", sourceSpanSha256 = "new-span-1"))

        val merged = mergeAudiobookChapterSnapshot(
            partialWithoutIdentity,
            partial,
            oldBook,
            oldChapters
        )

        assertFalse(merged.snapshotComplete)
        assertEquals(listOf("new-1"), merged.chapters.map { it.id })
    }

    @Test
    fun `book list refresh without a source identity cannot retain orphan cleanup authority`() {
        val local = AudiobookBook(
            id = "book-1",
            clientBookId = "book-1",
            title = "旧书",
            author = "作者",
            sourceSha256 = "source-v1",
            chaptersSnapshotComplete = true
        )
        val incomingWithoutIdentity = local.copy(
            title = "待确认的新书",
            sourceSha256 = "",
            chaptersSnapshotComplete = false
        )

        val merged = mergeAudiobookBookLocalState(incomingWithoutIdentity, local)

        assertFalse(merged.chaptersSnapshotComplete)
    }

    @Test
    fun `book list refresh with the same known source preserves cleanup authority`() {
        val local = AudiobookBook(
            id = "book-1",
            clientBookId = "book-1",
            title = "旧书",
            author = "作者",
            sourceSha256 = "source-v1",
            chaptersTotal = 2,
            chaptersSnapshotComplete = true
        )
        val incoming = local.copy(title = "刷新后的书", chaptersSnapshotComplete = false)

        val merged = mergeAudiobookBookLocalState(incoming, local)

        assertTrue(merged.chaptersSnapshotComplete)
    }

    @Test
    fun `book list refresh with a newer manifest revision cannot retain cleanup authority`() {
        val local = AudiobookBook(
            id = "book-1",
            clientBookId = "book-1",
            title = "旧书",
            author = "作者",
            sourceSha256 = "source-v1",
            chaptersTotal = 2,
            manifestRevision = 3L,
            chaptersSnapshotComplete = true
        )
        val incoming = local.copy(
            title = "新快照",
            manifestRevision = 4L,
            chaptersSnapshotComplete = false
        )

        val merged = mergeAudiobookBookLocalState(incoming, local)

        assertFalse(merged.chaptersSnapshotComplete)
    }

    @Test
    fun `manifest revision disappearing cannot retain cleanup authority`() {
        val local = AudiobookBook(
            id = "book-1",
            clientBookId = "book-1",
            title = "旧书",
            author = "作者",
            sourceSha256 = "source-v1",
            chaptersTotal = 2,
            manifestRevision = 3L,
            chaptersSnapshotComplete = true
        )
        val incoming = local.copy(manifestRevision = 0L, chaptersSnapshotComplete = false)

        val merged = mergeAudiobookBookLocalState(incoming, local)

        assertFalse(merged.chaptersSnapshotComplete)
    }

    @Test
    fun `chapter snapshot with an ordinal gap cannot authorize orphan cleanup`() {
        val book = AudiobookBook(
            id = "book-1",
            clientBookId = "book-1",
            title = "样书",
            author = "作者",
            chaptersTotal = 2
        )
        val malformed = listOf(
            AudiobookChapter("chapter-1", "book-1", 1, "第一章"),
            AudiobookChapter("chapter-3", "book-1", 3, "第三章")
        )

        assertFalse(audiobookChapterSnapshotIsComplete(book, malformed))
    }

    @Test
    fun `orphan cleanup requires a complete manifest with a proven source identity`() {
        val book = AudiobookBook(
            id = "book-1",
            clientBookId = "book-1",
            title = "样书",
            author = "作者",
            sourceSha256 = "source-v1",
            chaptersTotal = 2,
            chaptersSnapshotComplete = true
        )
        val completeRows = listOf(
            AudiobookChapter("chapter-1", "book-1", 1, "第一章"),
            AudiobookChapter("chapter-2", "book-1", 2, "第二章")
        )

        assertTrue(audiobookCanReconcileOrphanCanonicalFiles(book, completeRows))
        assertFalse(
            audiobookCanReconcileOrphanCanonicalFiles(
                book.copy(sourceSha256 = ""),
                completeRows
            )
        )
        assertFalse(
            audiobookCanReconcileOrphanCanonicalFiles(
                book,
                completeRows.dropLast(1)
            )
        )
    }

    @Test
    fun `ready chapter exposes a visible download or retry action`() {
        val ready = AudiobookChapter("chapter-1", "book-1", 1, "第一章", status = AudiobookChapterStatus.READY)
        val failed = ready.copy(downloadErrorMessage = "网络中断")

        assertEquals("下载", audiobookChapterActionLabel(ready, false))
        assertEquals("重试下载", audiobookChapterActionLabel(failed, false))
        assertEquals(null, audiobookChapterActionLabel(failed, true))
    }

    @Test
    fun `player errors have an explicit user-facing label`() {
        assertEquals(
            "播放失败：音频解码失败",
            audiobookPlaybackErrorLabel(AudiobookPlaybackState(errorMessage = "音频解码失败"))
        )
        assertEquals("", audiobookPlaybackErrorLabel(AudiobookPlaybackState()))
    }

    @Test
    fun `failed books expose an honest query action`() {
        assertEquals("生成失败，请重新查询", audiobookBookStatusLabel(AudiobookBookStatus.FAILED))
        assertEquals("重新查询", audiobookBookRecoveryActionLabel(AudiobookBookStatus.FAILED))
        assertEquals("重新查询", audiobookBookRecoveryActionLabel(AudiobookBookStatus.PARTIAL_FAILED))
        assertEquals(null, audiobookBookRecoveryActionLabel(AudiobookBookStatus.PREVIEW_READY))
    }

    @Test
    fun `pre-manifest failed analysis exposes a real resubmit action`() {
        val failed = AudiobookBook(
            id = "book-1",
            clientBookId = "client-book-1",
            title = "样书",
            author = "作者",
            status = AudiobookBookStatus.FAILED,
            sourceStatus = "complete",
            manifestRevision = 0L,
            chaptersTotal = 0,
        )

        assertEquals("重新提交分析", audiobookBookRecoveryActionLabel(failed))
        assertEquals("重新查询", audiobookBookRecoveryActionLabel(failed.copy(manifestRevision = 1L)))
        assertEquals("正在查询…", audiobookBookRecoveryActionLabel(failed, transferActive = true))
        assertEquals(true, audiobookCanRetryAnalysis(failed))
        assertEquals(false, audiobookCanRetryAnalysis(failed.copy(chaptersTotal = 1)))
    }

    @Test
    fun `automatic session bootstrap has a visible but non technical status`() {
        assertEquals(
            "正在查询后台",
            audiobookServiceCardTitle(connectionReady = false, transferActive = true)
        )
        assertEquals(
            "正在连接听书服务",
            audiobookServiceCardTitle(connectionReady = false, transferActive = false)
        )
        assertEquals(
            "听书服务已连接",
            audiobookServiceCardTitle(connectionReady = true, transferActive = false)
        )
    }

    @Test
    fun `automatic connection never asks the user to edit deployment config`() {
        assertFalse(AudiobookScreensUserConfigPolicy.exposesDeploymentCredentials)
    }

    @Test
    fun `public audiobook copy hides implementation and deployment details`() {
        val publicCopy = listOf(
            "我的有声书",
            "小说会在后台分析并生成音频，完成后自动同步到手机",
            "只显示本机已下载完整正文的小说；导入后会自动分析整本书，并按章节准备音频。",
            audiobookBookStatusLabel(AudiobookBookStatus.ACCEPTED),
            audiobookBookProgressSummary(
                AudiobookBook("book-1", "book-1", "样书", "", status = AudiobookBookStatus.ACCEPTED)
            ),
            friendlyAudiobookHttpError(403, "blocked")
        ).joinToString(" ")

        assertFalse(publicCopy.contains("公司电脑"))
        assertFalse(publicCopy.contains("本地模型"))
        assertFalse(publicCopy.contains("中转"))
        assertFalse(publicCopy.contains("HTTP"))
        assertFalse(publicCopy.contains("代理"))
        assertFalse(publicCopy.contains("令牌"))
    }

    @Test
    fun `failed book query action is disabled while refresh is active`() {
        assertEquals(
            "正在查询…",
            audiobookBookRecoveryActionLabel(AudiobookBookStatus.FAILED, transferActive = true)
        )
        assertEquals(
            "重新查询",
            audiobookBookRecoveryActionLabel(AudiobookBookStatus.FAILED, transferActive = false)
        )
    }

    @Test
    fun `transfer copy makes query and download phases understandable`() {
        assertEquals(
            "正在下载章节",
            audiobookServiceCardTitle(
                connectionReady = true,
                transferActive = true,
                transferKind = AudiobookTransferKind.DOWNLOAD
            )
        )
        assertEquals(
            "正在查询后台任务和章节进度，请稍候；已同步内容不会丢失。",
            audiobookTransferActiveMessage(AudiobookTransferKind.QUERY)
        )
        assertEquals(
            "正在下载已生成章节；后台状态已刷新，已下载章节仍可播放。",
            audiobookTransferActiveMessage(AudiobookTransferKind.DOWNLOAD)
        )
        assertEquals(
            "正在处理听书任务，请稍候；已同步内容不会丢失。",
            audiobookTransferActiveMessage(AudiobookTransferKind.OTHER)
        )
    }

    @Test
    fun `active transfer copy stays user-facing and never exposes deployment details`() {
        listOf(
            audiobookTransferActiveMessage(AudiobookTransferKind.QUERY),
            audiobookTransferActiveMessage(AudiobookTransferKind.DOWNLOAD),
            audiobookTransferActiveMessage(AudiobookTransferKind.OTHER),
        ).forEach { copy ->
            assertFalse(copy.contains("配置"))
            assertFalse(copy.contains("令牌"))
            assertFalse(copy.contains("Authorization", ignoreCase = true))
        }
    }

    @Test
    fun `book scoped transfer does not block unrelated recovery actions`() {
        assertTrue(
            audiobookTransferAffectsBook(
                AudiobookTransferState(bookId = null, active = true),
                "book-1"
            )
        )
        assertTrue(
            audiobookTransferAffectsBook(
                AudiobookTransferState(bookId = "book-1", active = true),
                "book-1"
            )
        )
        assertFalse(
            audiobookTransferAffectsBook(
                AudiobookTransferState(bookId = "book-1", active = true),
                "book-2"
            )
        )
    }

    @Test
    fun `refresh exposes bounded progress and an honest timeout`() {
        assertEquals("正在查询后台任务 0/17…", audiobookRefreshProgressMessage(0, 17))
        assertEquals("正在查询后台任务 17/17…", audiobookRefreshProgressMessage(22, 17))
        assertEquals(
            "后台查询超过45秒仍未完成；已保留已同步内容，可稍后重试。",
            audiobookRefreshTimeoutMessage(45)
        )
    }

    @Test
    fun `refresh result separates book query failures from chapter download failures`() {
        assertEquals(
            "后台状态已刷新；样书A、样书B状态暂未返回；第一章、第二章下载失败，可重试",
            audiobookRefreshResultMessage(
                failedBookTitles = listOf("样书A", "样书B"),
                failedChapterTitles = listOf("第一章", "第二章")
            )
        )
        assertEquals("后台状态已刷新", audiobookRefreshResultMessage(emptyList(), emptyList()))
    }

    @Test
    fun `playable books are surfaced before waiting tasks while preserving order`() {
        val waiting = AudiobookBook(
            id = "waiting",
            clientBookId = "waiting",
            title = "等待任务",
            author = "",
            status = AudiobookBookStatus.ANALYZING
        )
        val ready = AudiobookBook(
            id = "ready",
            clientBookId = "ready",
            title = "可播放书",
            author = "",
            status = AudiobookBookStatus.PREVIEW_READY,
            usable = true,
            chaptersReady = 2,
            chaptersTotal = 2
        )
        val partial = AudiobookBook(
            id = "partial",
            clientBookId = "partial",
            title = "部分可听",
            author = "",
            status = AudiobookBookStatus.PARTIAL_READY,
            chaptersReady = 1,
            chaptersTotal = 3
        )
        val failed = AudiobookBook(
            id = "failed",
            clientBookId = "failed",
            title = "失败任务",
            author = "",
            status = AudiobookBookStatus.FAILED
        )

        assertEquals(
            listOf("ready", "partial", "failed", "waiting"),
            audiobookBookDisplayOrder(listOf(waiting, ready, partial, failed)).map { it.id }
        )
    }

    @Test
    fun `remote refresh merges local records instead of hiding them`() {
        val localOnly = AudiobookBook(
            id = "local-only",
            clientBookId = "local-only",
            title = "本地可播放书",
            author = "本地",
            sourceUri = "content://offline/local-only"
        )
        val existing = localOnly.copy(id = "same", clientBookId = "same", title = "旧标题")
        val remote = listOf(
            AudiobookBook("same", "same", "新标题", "远端", status = AudiobookBookStatus.READY),
            AudiobookBook("remote-only", "remote-only", "远端新书", "远端")
        )

        val merged = mergeAudiobookBooks(remote, listOf(existing, localOnly))

        assertEquals(listOf("same", "remote-only", "local-only"), merged.map { it.id })
        assertEquals("新标题", merged.first().title)
        assertEquals("content://offline/local-only", merged.first().sourceUri)
        assertEquals("content://offline/local-only", merged.last().sourceUri)
    }

    @Test
    fun `stale refresh results cannot publish over a newer operation`() {
        assertTrue(audiobookOperationIsCurrent(7L, 7L))
        assertFalse(audiobookOperationIsCurrent(7L, 8L))
    }

    @Test
    fun `refresh result buffer can hold every listed book`() {
        assertEquals(1, audiobookRefreshResultBufferCapacity(0))
        assertEquals(17, audiobookRefreshResultBufferCapacity(17))
    }

    @Test
    fun `cancellation and transient IO failures retain partial audiobook downloads`() {
        assertTrue(shouldRetainAudiobookPartial(CancellationException("cancelled")))
        assertTrue(shouldRetainAudiobookPartial(IOException("connection reset")))
        assertFalse(shouldRetainAudiobookPartial(IllegalStateException("not retryable")))
    }

    @Test
    fun `stale download completion cannot clear or overwrite a newer remote chapter`() {
        val old = AudiobookChapter(
            id = "chapter-1",
            bookId = "book-1",
            ordinal = 1,
            title = "第一章",
            status = AudiobookChapterStatus.READY,
            audioArtifactId = "artifact-old",
            audioSha256 = "sha-old",
            audioBytes = 10L,
            audioContentType = "audio/wav",
            durationMs = 1_000L
        )
        val newer = old.copy(
            audioArtifactId = "artifact-new",
            audioSha256 = "sha-new",
            audioBytes = 20L,
            downloadErrorMessage = "上一轮下载失败"
        )

        assertEquals(null, mergeDownloadedAudiobookChapter(newer, old))
        assertEquals(
            null,
            mergeDownloadedAudiobookChapter(old.copy(status = AudiobookChapterStatus.RENDERING), old)
        )
        assertEquals(
            old.copy(downloadErrorMessage = null),
            mergeDownloadedAudiobookChapter(old.copy(downloadErrorMessage = "网络中断"), old)
        )
    }

    @Test
    fun `different audio revisions use isolated download artifact names`() {
        val old = AudiobookChapter(
            id = "chapter-1",
            bookId = "book-1",
            ordinal = 1,
            title = "第一章",
            status = AudiobookChapterStatus.READY,
            audioArtifactId = "artifact-old",
            audioSha256 = "sha-old",
            audioBytes = 10L,
            audioContentType = "audio/wav",
            durationMs = 1_000L
        )
        val newer = old.copy(
            audioArtifactId = "artifact-new",
            audioSha256 = "sha-new",
            audioBytes = 20L
        )

        val oldName = audiobookDownloadArtifactName("chapter.wav", old)
        val newName = audiobookDownloadArtifactName("chapter.wav", newer)

        assertNotEquals(oldName, newName)
        assertTrue(oldName.endsWith(".wav.pending"))
        assertTrue(newName.endsWith(".wav.pending"))
        assertNotEquals(audiobookDownloadRevisionSuffix(old), audiobookDownloadRevisionSuffix(newer))
    }

    @Test
    fun `manifest revision participates in the chapter cache identity`() {
        val old = AudiobookChapter(
            id = "chapter-1",
            bookId = "book-1",
            ordinal = 1,
            title = "第一章",
            status = AudiobookChapterStatus.READY,
            manifestRevision = 3L,
            audioArtifactId = "artifact",
            audioSha256 = "sha",
            audioBytes = 10L,
            audioContentType = "audio/wav",
            durationMs = 1_000L
        )
        val newer = old.copy(manifestRevision = 4L)

        assertNotEquals(audiobookChapterAudioIdentity(old), audiobookChapterAudioIdentity(newer))
        assertNotEquals(audiobookDownloadRevisionSuffix(old), audiobookDownloadRevisionSuffix(newer))
    }

    @Test
    fun `equal length old audio is not valid for a newer cache identity`() {
        val old = AudiobookChapter(
            id = "chapter-1",
            bookId = "book-1",
            ordinal = 1,
            title = "第一章",
            status = AudiobookChapterStatus.READY,
            audioArtifactId = "artifact-old",
            audioSha256 = "same-length-old",
            audioBytes = 10L,
            audioContentType = "audio/wav",
            durationMs = 1_000L
        )
        val newer = old.copy(audioArtifactId = "artifact-new", audioSha256 = "same-length-new")

        assertEquals("chapter.wav.meta", audiobookAudioCacheMarkerName("chapter.wav"))
        assertTrue(audiobookAudioCacheIsValid(true, 10L, old, audiobookChapterAudioIdentity(old)))
        assertFalse(audiobookAudioCacheIsValid(true, 10L, newer, audiobookChapterAudioIdentity(old)))
    }

    @Test
    fun `chapter history explains completed replay and unfinished resume`() {
        assertEquals(
            "已听完，再听从头开始",
            audiobookChapterHistoryLabel(AudiobookHistoryEntry("b", "书", "c", "章", 100_000L, 100_000L, 1L))
        )
        assertEquals(
            "继续听到 42%",
            audiobookChapterHistoryLabel(AudiobookHistoryEntry("b", "书", "c", "章", 42_000L, 100_000L, 1L))
        )
    }

    @Test
    fun `completed playback exposes replay instead of ambiguous resume`() {
        assertEquals(
            "从头播放",
            audiobookPlaybackActionLabel(AudiobookPlaybackState(isPlaying = false, positionMs = 100_000L, durationMs = 100_000L))
        )
        assertEquals(
            "继续",
            audiobookPlaybackActionLabel(AudiobookPlaybackState(isPlaying = false, positionMs = 42_000L, durationMs = 100_000L))
        )
        assertEquals(
            "暂停",
            audiobookPlaybackActionLabel(AudiobookPlaybackState(isPlaying = true, positionMs = 42_000L, durationMs = 100_000L))
        )
    }

    @Test
    fun `detail separates remote ready chapters from local cached chapters`() {
        val chapters = listOf(
            AudiobookChapter("chapter-1", "book-1", 1, "第一章", status = AudiobookChapterStatus.READY),
            AudiobookChapter("chapter-2", "book-1", 2, "第二章", status = AudiobookChapterStatus.READY),
            AudiobookChapter("chapter-3", "book-1", 3, "第三章", status = AudiobookChapterStatus.RENDERING)
        )

        assertEquals(
            "手机已缓存 1/2 章，其余已生成章节正在下载",
            audiobookLocalAudioSummary(chapters) { it.id == "chapter-1" }
        )
        assertEquals(
            "手机已缓存 2/2 章，可离线播放",
            audiobookLocalAudioSummary(chapters) { it.status == AudiobookChapterStatus.READY }
        )
        assertEquals(
            "远端已生成 2 章，手机正在下载",
            audiobookLocalAudioSummary(chapters) { false }
        )
    }

    @Test
    fun `last sync label makes background freshness visible`() {
        val now = Instant.parse("2026-09-05T14:00:00Z").toEpochMilli()
        val book = AudiobookBook(
            "book-1",
            "book-1",
            "样书",
            "",
            updatedAt = "2026-09-05T13:48:00Z"
        )

        assertEquals("最近同步：12 分钟前", audiobookLastSyncedLabel(book, now))
        assertEquals(
            "最近同步：刚刚",
            audiobookLastSyncedLabel(book.copy(updatedAt = "2026-09-05T13:59:45Z"), now)
        )
        assertNull(audiobookLastSyncedLabel(book.copy(updatedAt = "not-a-timestamp"), now))
    }
}
