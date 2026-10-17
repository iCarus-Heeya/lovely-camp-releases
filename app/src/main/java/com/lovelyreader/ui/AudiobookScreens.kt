package com.lovelyreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.lovelyreader.audiobook.AudiobookBook
import com.lovelyreader.audiobook.AudiobookBookStatus
import com.lovelyreader.audiobook.AudiobookChapter
import com.lovelyreader.audiobook.AudiobookChapterStatus
import com.lovelyreader.audiobook.AudiobookTransferKind
import com.lovelyreader.audiobook.AudiobookViewModel
import com.lovelyreader.audiobook.audiobookBookStatusLabel
import com.lovelyreader.audiobook.audiobookBookProgressSummary
import com.lovelyreader.audiobook.audiobookBookRecoveryActionLabel
import com.lovelyreader.audiobook.audiobookBookDisplayOrder
import com.lovelyreader.audiobook.audiobookGenerationResourceHint
import com.lovelyreader.audiobook.audiobookPreviewReadyHint
import com.lovelyreader.audiobook.audiobookSyncedAudioHint
import com.lovelyreader.audiobook.audiobookResourceWaitMessage
import com.lovelyreader.audiobook.audiobookLocalAudioSummary
import com.lovelyreader.audiobook.audiobookLastSyncedLabel
import com.lovelyreader.audiobook.audiobookServiceCardTitle
import com.lovelyreader.audiobook.audiobookCanRetryAnalysis
import com.lovelyreader.audiobook.audiobookChapterActionHint
import com.lovelyreader.audiobook.audiobookChapterActionLabel
import com.lovelyreader.audiobook.audiobookChapterHistoryLabel
import com.lovelyreader.audiobook.audiobookChapterStatusLabel
import com.lovelyreader.audiobook.audiobookPlaybackActionLabel
import com.lovelyreader.audiobook.audiobookPlaybackErrorLabel
import com.lovelyreader.audiobook.audiobookPlaybackLabel
import com.lovelyreader.audiobook.AudiobookShelfSection
import com.lovelyreader.audiobook.audiobookTransferActiveMessage
import com.lovelyreader.audiobook.audiobookTransferAffectsBook
import com.lovelyreader.audiobook.audiobookShelfSectionOrder
import com.lovelyreader.audiobook.canPlayAudiobookChapter
import com.lovelyreader.audiobook.canRetryAudiobookChapterGeneration
import com.lovelyreader.domain.Book
import com.lovelyreader.ui.theme.appColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookScreen(
    viewModel: AudiobookViewModel,
    onBack: () -> Unit,
    onImportTxt: () -> Unit,
    downloadedNovelBooks: List<Book>,
    onImportDownloadedBook: (Book) -> Unit,
    bottomBar: @Composable () -> Unit
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val selectedBookId by viewModel.selectedBookId.collectAsStateWithLifecycle()
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val connectionReady by viewModel.connectionReady.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val uiSnapshot by viewModel.uiSnapshot.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    var statusClockMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            statusClockMillis = System.currentTimeMillis()
        }
    }

    BackHandler {
        if (selectedBookId != null) viewModel.selectBook(null) else onBack()
    }

    val selectedBook = books.firstOrNull { it.id == selectedBookId }
    val selectedChapters = selectedBook?.let { book ->
        remember(book.id, uiSnapshot.revision) {
            viewModel.chapters(book.id)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedBook?.title ?: "听书") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedBookId != null) viewModel.selectBook(null) else onBack()
                    }) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !transfer.active) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新后台进度")
                    }
                }
            )
        },
        bottomBar = bottomBar
    ) { contentPadding ->
        if (selectedBook == null) {
            AudiobookShelfContent(
                books = books,
                transferMessage = transfer.message,
                transferPercent = transfer.percent,
                transferActive = transfer.active,
                transferKind = transfer.kind,
                message = message,
                connectionReady = connectionReady,
                nowMillis = statusClockMillis,
                onImportTxt = onImportTxt,
                downloadedNovelBooks = downloadedNovelBooks,
                onImportDownloadedBook = onImportDownloadedBook,
                onOpenBook = viewModel::selectBook,
                modifier = Modifier.padding(contentPadding)
            )
        } else {
            AudiobookDetailContent(
                book = selectedBook,
                chapters = selectedChapters.orEmpty(),
                playerState = playerState,
                message = message,
                transfer = transfer,
                hasAudio = viewModel::hasAudio,
                historyFor = viewModel::historyFor,
                onDownload = { chapter -> viewModel.downloadChapter(selectedBook.id, chapter) },
                onPlay = { chapter -> viewModel.playChapter(selectedBook.id, chapter) },
                onPause = viewModel::pausePlayback,
                onResume = viewModel::resumePlayback,
                onReplay = viewModel::replayPlayback,
                onRetryPlayback = viewModel::retryPlayback,
                onRetryAnalysis = viewModel::retryBookAnalysis,
                onRetryGeneration = { chapter -> viewModel.retryChapter(selectedBook.id, chapter.id) },
                onRefresh = viewModel::refresh,
                nowMillis = statusClockMillis,
                modifier = Modifier.padding(contentPadding)
            )
        }
    }
}

@Composable
private fun AudiobookShelfContent(
    books: List<AudiobookBook>,
    transferMessage: String,
    transferPercent: Int,
    transferActive: Boolean,
    transferKind: AudiobookTransferKind,
    message: String,
    connectionReady: Boolean,
    nowMillis: Long,
    onImportTxt: () -> Unit,
    downloadedNovelBooks: List<Book>,
    onImportDownloadedBook: (Book) -> Unit,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier
    ) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Headphones, contentDescription = null, tint = appColors().roseDust, modifier = Modifier.size(30.dp))
            Column {
                Text("我的有声书", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("小说会在后台分析并生成音频，完成后自动同步到手机", color = appColors().cocoa.copy(alpha = .72f))
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = appColors().warmWhite.copy(alpha = .86f))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    audiobookServiceCardTitle(connectionReady, transferActive, transferKind),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (transferActive) {
                    Text(
                        audiobookTransferActiveMessage(transferKind),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().cocoa.copy(alpha = .72f)
                    )
                } else {
                    Text(
                        if (connectionReady) {
                            audiobookSyncedAudioHint()
                        } else {
                            "正在准备听书服务，完成后就可以导入小说。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().cocoa.copy(alpha = .65f)
                    )
                    if (connectionReady) {
                        Text(
                            audiobookGenerationResourceHint(),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().cocoa.copy(alpha = .65f)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onImportTxt,
            enabled = connectionReady && !transferActive,
            modifier = Modifier.fillMaxWidth()
        ) { Text("导入 TXT") }
        audiobookShelfSectionOrder(books.isNotEmpty()).forEach { section ->
            when (section) {
                AudiobookShelfSection.REMOTE_BOOKS -> {
                    Spacer(Modifier.height(10.dp))
                    Text("后台有声书", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        audiobookBookDisplayOrder(books).forEach { book ->
                            AudiobookBookCard(book, nowMillis, onClick = { onOpenBook(book.id) })
                        }
                    }
                }
                AudiobookShelfSection.LOCAL_IMPORTS -> {
                    Spacer(Modifier.height(8.dp))
                    Text("从已下载小说导入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "只显示本机已下载完整正文的小说；导入后会自动分析整本书，并按章节准备音频。",
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().cocoa.copy(alpha = .68f)
                    )
                    if (downloadedNovelBooks.isEmpty()) {
                        Text(
                            "目前没有可导入的完整正文，请先回到书架下载完成。",
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().cocoa.copy(alpha = .68f)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            downloadedNovelBooks.forEach { book ->
                                Card(colors = CardDefaults.cardColors(containerColor = appColors().almond.copy(alpha = .6f))) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(book.title, fontWeight = FontWeight.Medium)
                                            if (book.author.isNotBlank()) {
                                                Text(book.author, style = MaterialTheme.typography.bodySmall)
                                            }
                                            Text("完整正文已在本机", style = MaterialTheme.typography.labelSmall)
                                        }
                                        OutlinedButton(
                                            onClick = { onImportDownloadedBook(book) },
                                            enabled = connectionReady && !transferActive
                                        ) { Text("导入") }
                                    }
                                }
                            }
                        }
                    }
                    if (transferMessage.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(transferMessage, style = MaterialTheme.typography.bodySmall)
                        if (transferPercent in 1..99) LinearProgressIndicator(progress = { transferPercent / 100f }, Modifier.fillMaxWidth())
                    }
                    message.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(6.dp)); Text(it, color = appColors().roseDust, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (books.isEmpty()) {
                        Surface(color = appColors().warmWhite.copy(alpha = .7f), modifier = Modifier.fillMaxWidth()) {
                            Text("还没有有声书。导入一本 TXT 后，系统会先分析整本书，再按章节准备音频。", Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudiobookBookCard(book: AudiobookBook, nowMillis: Long, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = appColors().warmWhite.copy(alpha = .9f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (book.author.isNotBlank()) Text(book.author, style = MaterialTheme.typography.bodySmall)
            Text(audiobookBookStatusLabel(book, nowMillis), color = appColors().roseDust, style = MaterialTheme.typography.bodyMedium)
            Text(
                audiobookBookProgressSummary(book, nowMillis),
                style = MaterialTheme.typography.bodySmall
            )
            audiobookLastSyncedLabel(book, nowMillis)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = appColors().cocoa.copy(alpha = .68f))
            }
            LinearProgressIndicator(progress = { book.progress / 100f }, Modifier.fillMaxWidth())
            Text(
                if (book.chaptersReady > 0) "打开详情，继续播放" else "打开详情，查看生成进度",
                style = MaterialTheme.typography.labelMedium,
                color = appColors().roseDust
            )
        }
    }
}

@Composable
private fun AudiobookDetailContent(
    book: AudiobookBook,
    chapters: List<AudiobookChapter>,
    playerState: com.lovelyreader.audiobook.AudiobookPlaybackState,
    message: String,
    transfer: com.lovelyreader.audiobook.AudiobookTransferState,
    hasAudio: (AudiobookChapter) -> Boolean,
    historyFor: (String, String) -> com.lovelyreader.audiobook.AudiobookHistoryEntry?,
    onDownload: (AudiobookChapter) -> Unit,
    onPlay: (AudiobookChapter) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReplay: () -> Unit,
    onRetryPlayback: () -> Unit,
    onRetryAnalysis: (String) -> Unit,
    onRetryGeneration: (AudiobookChapter) -> Unit,
    onRefresh: () -> Unit,
    nowMillis: Long,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        val resourceWaitMessage = audiobookResourceWaitMessage(book, chapters, nowMillis)
        Text(audiobookBookStatusLabel(book, chapters, nowMillis), color = appColors().roseDust, style = MaterialTheme.typography.titleMedium)
        Text(audiobookBookProgressSummary(book, chapters, nowMillis), style = MaterialTheme.typography.bodySmall)
        audiobookLastSyncedLabel(book, nowMillis)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = appColors().cocoa.copy(alpha = .68f))
        }
        resourceWaitMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = appColors().cocoa.copy(alpha = .72f))
        }
        if (resourceWaitMessage == null && book.status in setOf(
                AudiobookBookStatus.ACCEPTED,
                AudiobookBookStatus.ANALYZING,
                AudiobookBookStatus.SPLITTING,
                AudiobookBookStatus.GENERATING,
                AudiobookBookStatus.PARTIAL_READY,
            )) {
            Text(
                audiobookGenerationResourceHint(),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().cocoa.copy(alpha = .68f)
            )
        }
        audiobookLocalAudioSummary(chapters, hasAudio)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = appColors().cocoa.copy(alpha = .68f))
        }
        if (book.status == AudiobookBookStatus.PREVIEW_READY) {
            Text(
                audiobookPreviewReadyHint(),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().cocoa.copy(alpha = .68f)
            )
        }
        val transferAffectsBook = audiobookTransferAffectsBook(transfer, book.id)
        val retryAnalysis = audiobookCanRetryAnalysis(book, transferAffectsBook)
        audiobookBookRecoveryActionLabel(book, transferAffectsBook)?.let { actionLabel ->
            if (transferAffectsBook) {
                Text(
                    audiobookTransferActiveMessage(transfer.kind),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().cocoa.copy(alpha = .72f)
                )
            } else {
                Text(
                    if (retryAnalysis) {
                        "原文已保留；重新提交后会从头分析整本书，再按规则生成代表章节。"
                    } else if (transfer.active) {
                        "另一本书正在同步，完成后可重新查询当前任务。"
                    } else {
                        "后台生成状态已结束，当前客户端只能重新查询最新状态。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().cocoa.copy(alpha = .72f)
                )
                OutlinedButton(
                    onClick = if (retryAnalysis) ({ onRetryAnalysis(book.id) }) else onRefresh,
                    enabled = !transfer.active
                ) { Text(actionLabel) }
            }
        }
        transfer.message.takeIf { it.isNotBlank() && (!transfer.active || transferAffectsBook) }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        message.takeIf { it.isNotBlank() }?.let { Text(it, color = appColors().roseDust, style = MaterialTheme.typography.bodySmall) }
        playerState.takeIf { it.bookId == book.id && it.chapterId != null }?.let { state ->
            Card(colors = CardDefaults.cardColors(containerColor = appColors().almond.copy(alpha = .72f)), modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(audiobookPlaybackLabel(state), style = MaterialTheme.typography.labelMedium)
                        Text(state.title, fontWeight = FontWeight.SemiBold)
                        Text("${formatMs(state.positionMs)} / ${formatMs(state.durationMs)}", style = MaterialTheme.typography.bodySmall)
                        audiobookPlaybackErrorLabel(state).takeIf { it.isNotBlank() }?.let {
                            Text(it, color = appColors().roseDust, style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = onRetryPlayback) { Text("重试播放") }
                        }
                    }
                    IconButton(onClick = when {
                        state.isPlaying -> onPause
                        state.durationMs > 0L && state.positionMs >= state.durationMs -> onReplay
                        else -> onResume
                    }) {
                        Icon(
                            if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = audiobookPlaybackActionLabel(state)
                        )
                    }
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(chapters, key = { it.id }) { chapter ->
                AudiobookChapterRow(
                    chapter = chapter,
                    localAvailable = hasAudio(chapter),
                    history = historyFor(book.id, chapter.id),
                    playing = playerState.bookId == book.id && playerState.chapterId == chapter.id && playerState.isPlaying,
                    onDownload = { onDownload(chapter) },
                    onPlay = { onPlay(chapter) },
                    onPause = onPause,
                    onRetryGeneration = { onRetryGeneration(chapter) },
                    retryEnabled = canRetryAudiobookChapterGeneration(chapter, transfer.active)
                )
            }
        }
    }
}

@Composable
private fun AudiobookChapterRow(
    chapter: AudiobookChapter,
    localAvailable: Boolean,
    history: com.lovelyreader.audiobook.AudiobookHistoryEntry?,
    playing: Boolean,
    onDownload: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onRetryGeneration: () -> Unit,
    retryEnabled: Boolean,
) {
    Card(colors = CardDefaults.cardColors(containerColor = appColors().warmWhite.copy(alpha = .86f))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("${chapter.ordinal}. ${chapter.title}", fontWeight = FontWeight.Medium)
                Text(audiobookChapterStatusLabel(chapter, localAvailable), style = MaterialTheme.typography.bodySmall, color = appColors().cocoa.copy(alpha = .7f))
                audiobookChapterHistoryLabel(history)?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
            when {
                playing -> IconButton(onClick = onPause) { Icon(Icons.Outlined.Pause, contentDescription = "暂停") }
                canPlayAudiobookChapter(chapter.status, localAvailable) -> IconButton(onClick = onPlay) { Icon(Icons.Outlined.PlayArrow, contentDescription = "播放") }
                chapter.status == AudiobookChapterStatus.READY -> TextButton(onClick = onDownload) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text(audiobookChapterActionLabel(chapter, localAvailable) ?: "下载")
                }
                chapter.status == AudiobookChapterStatus.FAILED_RETRYABLE ||
                    chapter.status == AudiobookChapterStatus.FAILED_PERMANENT -> TextButton(
                    onClick = onRetryGeneration,
                    enabled = retryEnabled
                ) { Text("重新生成") }
                chapter.status == AudiobookChapterStatus.NOT_SELECTED -> Text(
                    "本次不生成",
                    style = MaterialTheme.typography.labelSmall,
                )
                else -> audiobookChapterActionHint(chapter)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun formatMs(value: Long): String {
    val seconds = (value / 1000L).coerceAtLeast(0L)
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}
