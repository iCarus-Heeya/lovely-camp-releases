package com.lovelyreader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.lovelyreader.domain.AppTheme
import com.lovelyreader.domain.Book
import com.lovelyreader.domain.BookDetail
import com.lovelyreader.domain.ChapterContent
import com.lovelyreader.domain.RankingPeriod
import com.lovelyreader.domain.SearchResult
import com.lovelyreader.domain.SourceCapability
import com.lovelyreader.ui.theme.appColors
import com.lovelyreader.ui.theme.bookshelfHeaderGradient
import com.lovelyreader.update.UpdateHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    books: List<Book>,
    progressFor: (String) -> Int,
    downloadStatuses: Map<String, BookDownloadStatus>,
    sortMode: ShelfSortMode,
    onSortModeChanged: (ShelfSortMode) -> Unit,
    onSearch: () -> Unit,
    onOpenBook: (Book) -> Unit,
    onDeleteBook: (Book) -> Unit,
    onSettings: () -> Unit,
    lastReaderBookId: String? = null,
    bottomBar: @Composable () -> Unit = {}
) {
    val shelfGreeting = remember { WarmPhrases.shelfGreeting.random() }
    val shelfHeadline = remember { WarmPhrases.shelfHeadline.random() }
    val shelfHint = remember { WarmPhrases.shelfHint.random() }
    val currentBook = remember(books, lastReaderBookId) {
        lastReaderBookId?.let { id -> books.firstOrNull { it.id == id } }
            ?: books.maxByOrNull { progressFor(it.id) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "书架",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    TextButton(onClick = onSettings) {
                        Text("小纸条", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = appColors().cream,
                    scrolledContainerColor = appColors().cream
                )
            )
        },
        bottomBar = bottomBar,
        containerColor = Color.Transparent
    ) { padding ->
        InkWashBackground(Modifier.padding(padding).fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = shelfGreeting,
                            color = appColors().roseBeige,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = shelfHeadline,
                            color = appColors().cocoa,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = shelfHint,
                            color = appColors().cocoa.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Button(
                            onClick = onSearch,
                            colors = ButtonDefaults.buttonColors(containerColor = appColors().roseBeige),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                "找书",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (books.isEmpty()) {
                        SoftPanel {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "书架还空着，像一页没写的信",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "点一下上面的胶囊按钮，让老公帮你找一本喜欢的故事，把它慢慢装满。",
                                    color = appColors().cocoa.copy(alpha = 0.68f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        currentBook?.let { book ->
                            val status = downloadStatuses[book.id]
                                ?: BookDownloadStatus(
                                    state = if (progressFor(book.id) >= 100) DownloadState.Ready else DownloadState.NotStarted,
                                    percent = progressFor(book.id)
                                )
                            SoftPanel(modifier = Modifier.clickable { onOpenBook(book) }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BookCoverImage(
                                        url = book.coverUrl,
                                        title = book.title,
                                        author = book.author,
                                        modifier = Modifier
                                            .width(96.dp)
                                            .height(140.dp)
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            book.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = appColors().cocoa
                                        )
                                        Text(
                                            book.author.ifBlank { "未知作者" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = appColors().roseBeige
                                        )
                                        Text(
                                            "已读 ${progressFor(book.id)}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = appColors().cocoa.copy(alpha = 0.7f)
                                        )
                                        if (status.state == DownloadState.Downloading) {
                                            Text(
                                                downloadStatusDetail(status),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = appColors().roseBeige,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = {
                                                if (status.state == DownloadState.Downloading) {
                                                    status.percent / 100f
                                                } else {
                                                    progressFor(book.id) / 100f
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = appColors().roseBeige,
                                            trackColor = appColors().almond
                                        )
                                        Button(
                                            onClick = { onOpenBook(book) },
                                            colors = ButtonDefaults.buttonColors(containerColor = appColors().roseBeige),
                                            shape = MaterialTheme.shapes.large
                                        ) {
                                            Text(
                                                if (status.state == DownloadState.Ready) "继续阅读" else "继续下载/重试",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        SegmentedTextButtons(
                            options = listOf(ShelfSortMode.Default, ShelfSortMode.ByProgress, ShelfSortMode.ByTitle),
                            selected = sortMode,
                            label = {
                                when (it) {
                                    ShelfSortMode.Default -> "默认"
                                    ShelfSortMode.ByProgress -> "进度"
                                    ShelfSortMode.ByTitle -> "书名"
                                }
                            },
                            onSelected = onSortModeChanged
                        )
                    }
                }
            }

            gridItems(books, key = { it.id }) { book ->
                val status = downloadStatuses[book.id]
                    ?: BookDownloadStatus(
                        state = if (progressFor(book.id) >= 100) DownloadState.Ready else DownloadState.NotStarted,
                        percent = progressFor(book.id)
                    )
                BookCard(
                    book = book,
                    progress = progressFor(book.id),
                    downloadStatus = status,
                    onClick = { onOpenBook(book) },
                    onDelete = { onDeleteBook(book) }
                )
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    results: List<SearchResult>,
    rankingResults: List<SearchResult>,
    randomResults: List<SearchResult>,
    categories: List<String>,
    romanceCategories: List<String>,
    message: String,
    isSearching: Boolean,
    isLoadingRanking: Boolean,
    isLoadingRandom: Boolean,
    isRandomExhausted: Boolean,
    rankingMessage: String,
    randomMessage: String,
    searchHistory: List<String>,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onRankingChanged: (RankingPeriod, String) -> Unit,
    onRandomBrowse: (String) -> Unit,
    onRestartRandomBrowse: (String) -> Unit,
    onOpenResult: (SearchResult) -> Unit,
    onAddResultToShelf: (SearchResult) -> Unit,
    onSearchModeChanged: (String) -> Unit,
    onCancelDiscoveryLoads: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(SearchMode.Search) }
    var period by remember { mutableStateOf(RankingPeriod.MONTH) }
    var searchFilter by remember { mutableStateOf(SearchFilter.All) }
    val displayCategories = remember(categories) { listOf("全部") + categories }
    var category by remember { mutableStateOf("全部") }
    var randomPrimaryCategory by remember { mutableStateOf("全部") }
    var rankingCategory by remember { mutableStateOf("全部") }
    var rankingPrimaryCategory by remember { mutableStateOf("全部") }

    val displayResults = remember(results, query, searchFilter) {
        if (query.isBlank() && searchFilter == SearchFilter.All) {
            results
        } else {
            results.filter {
                when (searchFilter) {
                    SearchFilter.All -> true
                    SearchFilter.Title -> it.title.contains(query, ignoreCase = true)
                    SearchFilter.Author -> it.author.contains(query, ignoreCase = true)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("帮老婆找书") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("返回", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = appColors().cream)
            )
        },
        bottomBar = bottomBar,
        containerColor = Color.Transparent
    ) { padding ->
        InkWashBackground(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            item {
                SegmentedTextButtons(
                    options = listOf(SearchMode.Search, SearchMode.Rank, SearchMode.Random),
                    selected = mode,
                    label = { it.label },
                    onSelected = {
                        onCancelDiscoveryLoads()
                        mode = it
                        if (it == SearchMode.Rank) {
                            onRankingChanged(period, rankingCategory)
                        }
                        if (it == SearchMode.Random) {
                            onSearchModeChanged(category)
                        }
                    }
                )
            }
            when (mode) {
                SearchMode.Search -> {
                    item {
                        SegmentedTextButtons(
                            options = SearchFilter.entries,
                            selected = searchFilter,
                            label = { it.label },
                            onSelected = { searchFilter = it }
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("小说名或作者，支持模糊搜索") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.large
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onSearch(query) },
                            enabled = !isSearching,
                            colors = ButtonDefaults.buttonColors(containerColor = appColors().roseBeige),
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                if (isSearching) "正在找..." else "亲亲老公帮我找书",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (searchHistory.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("最近搜索", fontSize = 12.sp, color = appColors().cocoa.copy(alpha = 0.6f))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(searchHistory) { historyQuery ->
                                    OutlinedButton(
                                        onClick = {
                                            query = historyQuery
                                            onSearch(historyQuery)
                                        }
                                    ) {
                                        Text(historyQuery, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        Text(message, color = appColors().cocoa.copy(alpha = 0.7f))
                    }
                    items(
                        items = displayResults,
                        key = { "${it.title}-${it.author}" }
                    ) { result ->
                        SearchResultCard(
                            result = result,
                            onClick = { onOpenResult(result) },
                            onAddToShelf = { onAddResultToShelf(result) }
                        )
                    }
                }

                SearchMode.Rank -> {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(displayCategories) { item ->
                                FilterChipText(
                                    text = item,
                                    selected = item == rankingPrimaryCategory,
                                    enabled = !isLoadingRanking,
                                    onClick = {
                                        rankingPrimaryCategory = item
                                        rankingCategory = item
                                        onRankingChanged(period, rankingCategory)
                                    }
                                )
                            }
                        }
                        if (rankingPrimaryCategory == "言情") {
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(listOf("全部言情") + romanceCategories) { item ->
                                    val actual = if (item == "全部言情") "言情" else item
                                    FilterChipText(
                                        text = item,
                                        selected = actual == rankingCategory,
                                        enabled = !isLoadingRanking,
                                        onClick = {
                                            rankingCategory = actual
                                            onRankingChanged(period, actual)
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (rankingCategory == "全部") "来源首页精选" else "分类精选 · $rankingCategory",
                            color = appColors().roseBeige,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (isLoadingRanking) {
                                if (rankingCategory == "全部") "正在从已适配来源首页刷新精选，请稍等..."
                                else "正在读取「$rankingCategory」真实分类页，请稍等..."
                            } else rankingMessage,
                            color = appColors().cocoa.copy(alpha = 0.7f)
                        )
                    }
                    if (isLoadingRanking) {
                        item { LoadingPanel("老公正在认真挑精选") }
                    } else if (rankingResults.isEmpty()) {
                        item { SoftPanel { Text(rankingMessage.ifBlank { "暂时没有拿到精选内容，请稍后再试。" }) } }
                    } else {
                        items(
                            items = rankingResults,
                            key = { "${it.title}-${it.author}" }
                        ) { result ->
                            SearchResultCard(
                                result = result,
                                onClick = { onOpenResult(result) },
                                onAddToShelf = { onAddResultToShelf(result) }
                            )
                        }
                    }
                }

                SearchMode.Random -> {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(displayCategories) { item ->
                                FilterChipText(
                                    text = item,
                                    selected = item == randomPrimaryCategory,
                                    enabled = !isLoadingRandom,
                                    onClick = {
                                        randomPrimaryCategory = item
                                        category = item
                                        onRandomBrowse(category)
                                    }
                                )
                            }
                        }
                        if (randomPrimaryCategory == "言情") {
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(listOf("全部言情") + romanceCategories) { item ->
                                    val actual = if (item == "全部言情") "言情" else item
                                    FilterChipText(
                                        text = item,
                                        selected = actual == category,
                                        enabled = !isLoadingRandom,
                                        onClick = {
                                            category = actual
                                            onRandomBrowse(actual)
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                if (isRandomExhausted) onRestartRandomBrowse(category) else onRandomBrowse(category)
                            },
                            enabled = !isLoadingRandom,
                            colors = ButtonDefaults.buttonColors(containerColor = appColors().roseBeige),
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                when {
                                    isLoadingRandom -> "正在换一批"
                                    isRandomExhausted -> "重新开始"
                                    else -> "换一批"
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            if (isLoadingRandom) "正在从真实来源按「$category」随机拉取..." else randomMessage.ifBlank { "会尽量跳过本地已经看过或加入书架的书名。" },
                            color = appColors().cocoa.copy(alpha = 0.7f)
                        )
                    }
                    if (isLoadingRandom) {
                        item { LoadingPanel("老公正在重新挑一批") }
                    } else if (randomResults.isEmpty()) {
                        item { SoftPanel { Text(randomMessage.ifBlank { "暂时没有拿到真实推荐，请稍后再试。" }) } }
                    } else {
                        items(
                            items = randomResults,
                            key = { "${it.title}-${it.author}" }
                        ) { result ->
                            SearchResultCard(
                                result = result,
                                onClick = { onOpenResult(result) },
                                onAddToShelf = { onAddResultToShelf(result) }
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun LoadingPanel(text: String) {
    SoftPanel {
        LinearProgressIndicator(
            color = appColors().roseBeige,
            trackColor = appColors().almond,
            modifier = Modifier.fillMaxWidth()
        )
        Text(text, fontWeight = FontWeight.Medium)
        Text("如果来源站点响应慢，这里会安静等真实数据回来。", color = appColors().cocoa.copy(alpha = 0.68f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    result: SearchResult,
    detail: BookDetail?,
    onBack: () -> Unit,
    onAddToShelf: () -> Unit,
    onOpenOriginal: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        result.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("返回", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = appColors().cream)
            )
        },
        bottomBar = bottomBar,
        containerColor = appColors().cream
    ) { padding ->
        InkWashBackground(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            item {
                SoftPanel {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                        BookCoverImage(
                            url = detail?.book?.coverUrl ?: result.coverUrl,
                            title = detail?.book?.title ?: result.title,
                            author = detail?.book?.author ?: result.author,
                            modifier = Modifier.width(104.dp).height(148.dp)
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(detail?.book?.title ?: result.title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                            Text((detail?.book?.author ?: result.author).ifBlank { "未知作者" }, color = appColors().roseBeige)
                            detail?.category?.takeIf(String::isNotBlank)?.let { Text(it, color = appColors().cocoa.copy(alpha = .68f), style = MaterialTheme.typography.bodySmall) }
                            detail?.wordCountOrSize?.takeIf(String::isNotBlank)?.let { Text(it, color = appColors().cocoa.copy(alpha = .68f), style = MaterialTheme.typography.bodySmall) }
                            detail?.latestChapter?.takeIf(String::isNotBlank)?.let { Text("最新：$it", color = appColors().cocoa.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text((detail?.book?.summary ?: result.summary).ifBlank { "这个来源没有给简介，但可以先收进书架。" })
                    Spacer(Modifier.height(12.dp))
                    Text(detail?.offlineLabel ?: capabilityLabel(result), color = appColors().sage, fontWeight = FontWeight.Medium)
                }
            }
            item {
            Button(
                onClick = onAddToShelf,
                colors = ButtonDefaults.buttonColors(containerColor = appColors().roseBeige),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    "老公就它了",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            }
            item {
            OutlinedButton(
                onClick = onOpenOriginal,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    "打开原站",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    book: Book,
    chapterContent: ChapterContent?,
    isLoadingChapter: Boolean,
    chapterLoadAttempted: Boolean,
    initialFontSize: Int,
    initialNightMode: Boolean,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    loadChapter: suspend () -> Unit,
    onBack: () -> Unit,
    onPositionChanged: (percent: Int, index: Int, offset: Int) -> Unit,
    onFontSizeChanged: (Int) -> Unit,
    onNightModeChanged: (Boolean) -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    var fontSize by remember { mutableStateOf(initialFontSize.coerceIn(14, 24)) }
    var nightMode by remember { mutableStateOf(initialNightMode) }
    val loadingText = remember(book.id) { WarmPhrases.readerLoading.random() }
    val failedText = remember(book.id) { WarmPhrases.readerFailed.random() }
    val pageColor = if (nightMode) {
        Color(0xFF2A2420)
    } else {
        appColors().paper
    }
    // 正文阅读文字固定使用暖棕墨色，避免被主题色影响可读性
    val readerTextColor = if (nightMode) {
        Color(0xFFF5E9DD)
    } else {
        Color(0xFF46342F)
    }
    val readerChrome = if (nightMode) {
        Color(0xFF211C19)
    } else {
        appColors().cream
    }
    val toolbarColor = if (nightMode) {
        Color(0xCC2A2420)
    } else {
        Color(0xCCFFFDF9)
    }
    val readerScope = rememberCoroutineScope()

    var currentProgress by remember { mutableStateOf(0f) }
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    var showProgressSlider by remember { mutableStateOf(false) }
    var showReaderChrome by remember { mutableStateOf(true) }
    var showCatalog by remember { mutableStateOf(false) }

    var hasRestoredPosition by remember(book.id) { mutableStateOf(false) }
    var scrollSaveJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(book.id) {
        loadChapter()
    }
    LaunchedEffect(fontSize, nightMode) {
        onFontSizeChanged(fontSize)
        onNightModeChanged(nightMode)
    }

    Scaffold(
        topBar = {},
        bottomBar = {},
        containerColor = readerChrome
    ) { _ ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(readerChrome)
        ) {
            val density = LocalDensity.current
            val startPadding = 18.dp
            val endPadding = 28.dp
            val topPadding = 18.dp
            val bottomPadding = 18.dp
            val pageWidthPx = with(density) { (maxWidth - startPadding - endPadding).roundToPx() }
            val pageHeightPx = with(density) { (maxHeight - topPadding - bottomPadding).roundToPx() }
            val textStyle = TextStyle(
                fontSize = fontSize.sp,
                lineHeight = (fontSize + 16).sp
            )
            val textMeasurer = rememberTextMeasurer()
            val haptic = LocalHapticFeedback.current
            // 未加载时正文区域保持空白，禁止加载/失败文案进入正文分页
            val rawText = chapterContent?.content ?: ""
            // Keep the currently rendered pages while the new font layout is
            // measured. Clearing this state on every font click made the
            // reader flash blank and recreated the pager from its old start
            // index, which looked like the whole book had reloaded.
            var pages by remember(rawText, pageWidthPx, pageHeightPx) {
                mutableStateOf(listOf<String>())
            }
            var chapters by remember(rawText, pageWidthPx, pageHeightPx) {
                mutableStateOf(listOf<ReaderChapter>())
            }
            var pageLayoutGeneration by remember(book.id) { mutableStateOf(0) }
            LaunchedEffect(rawText, fontSize, pageWidthPx, pageHeightPx) {
                if (rawText.isBlank() || pageWidthPx <= 0 || pageHeightPx <= 0) return@LaunchedEffect
                val cacheKey = PageCacheKey(book.id, fontSize, pageWidthPx, pageHeightPx)
                val cached = readerPageCache[cacheKey]
                if (cached != null && cached.pages.isNotEmpty() && rawText.isNotBlank()) {
                    pages = cached.pages
                    chapters = cached.chapters
                    pageLayoutGeneration++
                    return@LaunchedEffect
                }
                val result = withContext(Dispatchers.Default) {
                    paginateReaderText(rawText, textStyle, textMeasurer, pageWidthPx, pageHeightPx)
                }
                pages = result.pages
                chapters = result.chapters
                if (result.pages.isNotEmpty()) {
                    if (readerPageCache.size >= 3) {
                        readerPageCache.remove(readerPageCache.keys.first())
                }
                readerPageCache[cacheKey] = CachedReaderPages(result.pages, result.chapters)
                pageLayoutGeneration++
            }
            }
            val pagerState = if (pages.isNotEmpty()) {
                rememberPagerState(
                    initialPage = initialScrollIndex.coerceIn(0, pages.size - 1),
                    pageCount = { pages.size }
                )
            } else {
                null
            }

            // Pagination necessarily changes page boundaries when the font
            // changes. Capture the current logical progress before starting
            // that work, then map it to the new page count when the result is
            // ready. This keeps the reader at the same part of the book.
            var pendingFontProgress by remember(book.id) { mutableStateOf<Float?>(null) }

            fun requestFontSize(nextSize: Int) {
                val next = nextSize.coerceIn(14, 24)
                if (next != fontSize) {
                    pendingFontProgress = currentProgress
                    fontSize = next
                }
            }

            fun saveCurrentPosition() {
                if (pages.isEmpty() || pagerState == null) return
                val percent = (currentProgress * 100).roundToInt().coerceIn(0, 100)
                onPositionChanged(percent, pagerState.currentPage.coerceIn(0, pages.size - 1), 0)
            }

            val navigateBack = {
                scrollSaveJob?.cancel()
                saveCurrentPosition()
                onBack()
            }
            BackHandler(onBack = navigateBack)

            if (pagerState != null) {
                LaunchedEffect(pages.size, book.id) {
                    if (pagerState.currentPage >= pages.size) {
                        pagerState.scrollToPage(pages.size - 1)
                    }
                    snapshotFlow { pagerState.currentPage to pagerState.currentPageOffsetFraction }
                        .collect { (page, offset) ->
                            currentProgress = if (pages.size <= 1) {
                                0f
                            } else {
                                (((page + offset).coerceIn(0f, pages.size.toFloat() - 1f)) / (pages.size - 1)).coerceIn(0f, 1f)
                            }
                        }
                }

                LaunchedEffect(pages.size, book.id) {
                    snapshotFlow { pagerState.isScrollInProgress }
                        .collect { scrolling ->
                            if (!scrolling && dragProgress == null && hasRestoredPosition) {
                                scrollSaveJob?.cancel()
                                scrollSaveJob = readerScope.launch {
                                    delay(200)
                                    saveCurrentPosition()
                                }
                            }
                        }
                }

                LaunchedEffect(pageLayoutGeneration, book.id) {
                    if (pages.isEmpty()) return@LaunchedEffect
                    val fontProgress = pendingFontProgress
                    if (fontProgress != null) {
                        val target = readerPageForProgress(fontProgress, pages.size)
                        pendingFontProgress = null
                        if (pagerState.currentPage != target) {
                            pagerState.scrollToPage(target)
                        }
                        currentProgress = if (pages.size <= 1) {
                            0f
                        } else {
                            (target.toFloat() / (pages.size - 1)).coerceIn(0f, 1f)
                        }
                    } else if (!hasRestoredPosition && chapterContent != null) {
                        val target = initialScrollIndex.coerceIn(0, pages.size - 1)
                        if (target > 0) {
                            pagerState.scrollToPage(target)
                        }
                        currentProgress = if (pages.size <= 1) {
                            0f
                        } else {
                            (target.toFloat() / (pages.size - 1)).coerceIn(0f, 1f)
                        }
                        hasRestoredPosition = true
                    }
                }
            }

            if (pagerState != null) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1
                ) { page ->
                    val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val absOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)
                    val rightShadowAlpha by animateFloatAsState(
                        targetValue = if (pageOffset <= 0f) 0.16f else 0.06f,
                        label = "rightShadow"
                    )
                    val leftShadowAlpha by animateFloatAsState(
                        targetValue = if (pageOffset >= 0f) 0.12f else 0.04f,
                        label = "leftShadow"
                    )
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(pageColor)
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = startPadding, end = endPadding, top = topPadding, bottom = bottomPadding)
                        ) {
                            key(pages.getOrElse(page) { "" }) {
                                Text(
                                    text = pages[page],
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize + 16).sp,
                                    color = readerTextColor
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(8.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = rightShadowAlpha * (1f - absOffset * 0.5f)))
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .width(8.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Black.copy(alpha = leftShadowAlpha * (1f - absOffset * 0.5f)), Color.Transparent)
                                    )
                                )
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(pages.size, showReaderChrome) {
                        detectTapGestures { offset ->
                            val width = size.width
                            when {
                                offset.x < width * 0.33f && pagerState != null -> {
                                    val target = (pagerState.currentPage - 1).coerceAtLeast(0)
                                    if (target != pagerState.currentPage) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        readerScope.launch { pagerState.animateScrollToPage(target) }
                                    }
                                }
                                offset.x > width * 0.67f && pagerState != null -> {
                                    val target = (pagerState.currentPage + 1).coerceAtMost(pages.size - 1)
                                    if (target != pagerState.currentPage) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        readerScope.launch { pagerState.animateScrollToPage(target) }
                                    }
                                }
                                else -> {
                                    showReaderChrome = !showReaderChrome
                                }
                            }
                        }
                    }
            )

            AnimatedVisibility(
                visible = showReaderChrome,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it }
            ) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            book.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = navigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "返回",
                                tint = readerTextColor
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { showProgressSlider = !showProgressSlider }) {
                            Text(
                                String.format(Locale.US, "%.2f%%", currentProgress * 100f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = readerTextColor
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = readerChrome)
                )
            }

            AnimatedVisibility(
                visible = showReaderChrome,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                ReaderBottomMenu(
                    nightMode = nightMode,
                    fontSize = fontSize,
                    currentProgress = currentProgress,
                    onNightModeToggle = { nightMode = !nightMode },
                    onFontSizeChange = ::requestFontSize,
                    onProgressClick = { showProgressSlider = !showProgressSlider },
                    onCatalogClick = { showCatalog = !showCatalog }
                )
            }

            if (showProgressSlider) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable { showProgressSlider = false },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(0.85f),
                        color = toolbarColor,
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                String.format(Locale.US, "%.2f%%", (dragProgress ?: currentProgress) * 100f),
                                color = readerTextColor,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = dragProgress ?: currentProgress,
                                onValueChange = { dragProgress = it },
                                onValueChangeFinished = {
                                    val denominator = (pages.size - 1).coerceAtLeast(1)
                                    val currentProgressValue = dragProgress ?: currentProgress
                                    val targetIndex = (currentProgressValue * denominator)
                                        .roundToInt()
                                        .coerceIn(0, pages.size - 1)
                                    val percent = (currentProgressValue * 100).roundToInt().coerceIn(0, 100)
                                    dragProgress = null
                                    showProgressSlider = false
                                    pagerState?.let { state ->
                                        readerScope.launch {
                                            state.animateScrollToPage(targetIndex)
                                        }
                                    }
                                    onPositionChanged(percent, targetIndex, 0)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (showCatalog) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable { showCatalog = false },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.62f),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = readerChrome,
                        shadowElevation = 8.dp
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "目录",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = readerTextColor
                                )
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val currentPage = pagerState?.currentPage ?: 0
                                items(chapters) { chapter ->
                                    val isCurrent = currentPage >= chapter.startPageIndex &&
                                        currentPage < (chapters.getOrNull(chapters.indexOf(chapter) + 1)?.startPageIndex ?: pages.size)
                                    Text(
                                        text = chapter.title,
                                        fontSize = 14.sp,
                                        color = if (isCurrent) appColors().roseBeige else readerTextColor.copy(alpha = 0.8f),
                                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showCatalog = false
                                                showReaderChrome = false
                                                pagerState?.let { state ->
                                                    readerScope.launch {
                                                        state.animateScrollToPage(chapter.startPageIndex)
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 20.dp, vertical = 14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 分页完成前持续显示加载/失败覆盖层，避免露出空白正文
            if (pages.isEmpty()) {
                if (chapterContent == null && chapterLoadAttempted) {
                    ReaderFailedOverlay(
                        book = book,
                        message = failedText,
                        onBack = navigateBack,
                        onRetry = { readerScope.launch { loadChapter() } },
                        readerBackground = pageColor,
                        readerTextColor = readerTextColor,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ReaderLoadingOverlay(
                        book = book,
                        message = loadingText,
                        onBack = navigateBack,
                        readerBackground = pageColor,
                        readerTextColor = readerTextColor,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomMenu(
    nightMode: Boolean,
    fontSize: Int,
    currentProgress: Float,
    onNightModeToggle: () -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onProgressClick: () -> Unit,
    onCatalogClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 12.dp),
        shape = MaterialTheme.shapes.large,
        color = appColors().warmWhite.copy(alpha = 0.86f),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.72f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MenuIconButton(
                    icon = Icons.AutoMirrored.Outlined.List,
                    label = "目录",
                    onClick = onCatalogClick
                )
                MenuIconButton(
                    icon = if (nightMode) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    label = if (nightMode) "日间" else "夜间",
                    onClick = onNightModeToggle
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { onFontSizeChange(fontSize - 2) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            text = "A",
                            fontSize = 12.sp,
                            color = appColors().cocoa.copy(alpha = 0.8f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "$fontSize",
                        fontSize = 13.sp,
                        color = appColors().cocoa,
                        modifier = Modifier.width(26.dp),
                        textAlign = TextAlign.Center
                    )
                    IconButton(
                        onClick = { onFontSizeChange(fontSize + 2) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            text = "A",
                            fontSize = 17.sp,
                            color = appColors().cocoa.copy(alpha = 0.8f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                MenuIconButton(
                    icon = if (nightMode) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    label = "进度",
                    onClick = onProgressClick
                )
            }

            Slider(
                value = currentProgress,
                onValueChange = { /* 进度由翻页驱动，这里仅展示 */ },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = appColors().roseBeige,
                    activeTrackColor = appColors().roseBeige,
                    inactiveTrackColor = appColors().almond
                )
            )
        }
    }
}

@Composable
private fun MenuIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = appColors().cocoa.copy(alpha = 0.75f)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = appColors().cocoa.copy(alpha = 0.7f),
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    notes: List<String>,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    currentTheme: AppTheme = AppTheme.Warm,
    onThemeChanged: (AppTheme) -> Unit = {},
    updateMessage: String = "",
    updateAvailable: com.lovelyreader.update.UpdateManifest? = null,
    onCheckUpdate: () -> Unit = {},
    onInstallUpdate: (com.lovelyreader.update.UpdateManifest) -> Unit = {},
    updateHistory: List<UpdateHistoryEntry> = emptyList(),
    updateHistoryMessage: String = "",
    onLoadUpdateHistory: () -> Unit = {}
) {
    val noteOfTheMoment = remember { WarmPhrases.notes.random() }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("老公的小纸条") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("返回", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = appColors().cream)
            )
        },
        bottomBar = bottomBar,
        containerColor = Color.Transparent
    ) { padding ->
        InkWashBackground(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            item {
                SoftPanel { Text(noteOfTheMoment, fontSize = 18.sp) }
            }
            items(notes) { note ->
                SoftPanel { Text(note, fontSize = 18.sp) }
            }
            item {
                SoftPanel {
                    Text("阅读外观", fontWeight = FontWeight.SemiBold)
                    Text("已启用奶油纸感、滚动进度、字号调节和晚安灯。", color = appColors().cocoa.copy(alpha = 0.7f))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ThemeChip(
                                text = "玫瑰晨曦",
                                selected = currentTheme == AppTheme.Warm,
                                onClick = { onThemeChanged(AppTheme.Warm) }
                            )
                            ThemeChip(
                                text = "紫红渐变",
                                selected = currentTheme == AppTheme.PurpleMagenta,
                                onClick = { onThemeChanged(AppTheme.PurpleMagenta) }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ThemeChip(
                                text = "薄荷奶绿",
                                selected = currentTheme == AppTheme.MintCream,
                                onClick = { onThemeChanged(AppTheme.MintCream) }
                            )
                            ThemeChip(
                                text = "海雾清晨",
                                selected = currentTheme == AppTheme.SeaFog,
                                onClick = { onThemeChanged(AppTheme.SeaFog) }
                            )
                        }
                    }
                }
            }
            item {
                SoftPanel {
                    Text("应用更新", fontWeight = FontWeight.SemiBold)
                    Text("应用会在已验证的 Wi-Fi 或以太网下每天自动检查一次；下载与系统安装始终由你确认，移动网络只在手动检查时使用。", color = appColors().cocoa.copy(alpha = 0.7f))
                    Button(
                        onClick = onCheckUpdate,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = appColors().roseBeige)
                    ) { Text("检查更新") }
                    LaunchedEffect(Unit) { onLoadUpdateHistory() }
                    if (updateMessage.isNotBlank()) Text(updateMessage, modifier = Modifier.padding(top = 8.dp), color = appColors().softGray)
                    updateAvailable?.let { manifest ->
                        Text(manifest.notes.ifBlank { "包含体验改进" }, modifier = Modifier.padding(top = 8.dp))
                        Button(
                            onClick = { onInstallUpdate(manifest) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = appColors().roseBeige)
                        ) { Text("下载并安装 ${manifest.versionName}") }
                    }
                    if (updateHistoryMessage.isNotBlank()) {
                        Text(updateHistoryMessage, modifier = Modifier.padding(top = 8.dp), color = appColors().softGray)
                    }
                    if (updateHistory.isNotEmpty()) {
                        Text("版本记录", modifier = Modifier.padding(top = 12.dp), fontWeight = FontWeight.SemiBold)
                        updateHistory.forEach { entry ->
                            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("v${entry.versionName}", fontWeight = FontWeight.Medium)
                                entry.publishedAt?.takeIf(String::isNotBlank)?.let { Text(formatUpdateDate(it), style = MaterialTheme.typography.bodySmall, color = appColors().softGray) }
                                Text(entry.notes, style = MaterialTheme.typography.bodySmall, color = appColors().cocoa.copy(alpha = .72f))
                            }
                        }
                    }
                }
            }
            item {
                SoftPanel {
                    Text("来源管理", fontWeight = FontWeight.SemiBold)
                    Text("6 个来源均已预置。已验证闭环：爱下电子书章节阅读、奇书网TXT下载阅读、勤看小说多格式下载阅读、知轩藏书TXT下载阅读；久久小说可搜索但详情下载待验证；言情小说站被 Cloudflare 拦截，暂不计入已支持。", color = appColors().cocoa.copy(alpha = 0.7f))
                }
            }
            }
        }
    }
}

fun ordinarySettingsSectionLabels(): List<String> = listOf("阅读外观", "应用更新", "来源管理")

private fun formatUpdateDate(value: String): String = value
    .replace('T', ' ')
    .replace(Regex("Z$"), "")
    .take(16)

@Composable
private fun ThemeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) appColors().roseBeige else appColors().warmWhite,
        label = "themeChipBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.White else appColors().cocoa.copy(alpha = 0.7f),
        label = "themeChipText"
    )
    val borderColor = if (selected) appColors().roseBeige else appColors().lineColor
    Surface(
        color = backgroundColor,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SyncSettingsPanel(
    configured: Boolean,
    message: String,
    defaultToken: String,
    defaultGistId: String,
    onSaveCredentials: (String, String) -> Unit,
    onClearMessage: () -> Unit,
    onClearAuth: () -> Unit
) {
    var token by remember(defaultToken) { mutableStateOf(defaultToken) }
    var gistId by remember(defaultGistId) { mutableStateOf(defaultGistId) }

    LaunchedEffect(message) {
        if (message.isNotBlank()) {
            kotlinx.coroutines.delay(2500)
            onClearMessage()
        }
    }

    SoftPanel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("阅读记录同步", fontWeight = FontWeight.SemiBold)
            Text(
                if (configured) "已配置 GitHub Gist，自动记录阅读事件" else "需先填写 GitHub Token 和 Gist ID",
                color = appColors().cocoa.copy(alpha = 0.7f)
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("GitHub Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )
            OutlinedTextField(
                value = gistId,
                onValueChange = { gistId = it },
                label = { Text("Gist ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )
            Button(
                onClick = { onSaveCredentials(token, gistId) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = appColors().roseBeige),
                shape = MaterialTheme.shapes.large
            ) {
                Text("保存同步凭证")
            }
            if (configured) {
                OutlinedButton(
                    onClick = onClearAuth,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("清除凭证")
                }
            }
            if (message.isNotBlank()) {
                Text(
                    message,
                    color = if (configured) appColors().sage else appColors().roseBeige,
                    fontSize = 13.sp
                )
            }
            Text(
                "自动记录：打开书、阅读进度、搜索、加入书架、删除书、添加书签。不上报 IMEI 或阅读内容。",
                color = appColors().cocoa.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun BookCard(
    book: Book,
    progress: Int,
    downloadStatus: BookDownloadStatus,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val statusText = when (downloadStatus.state) {
        DownloadState.NotStarted -> "待下载"
        DownloadState.Downloading -> downloadStatus.message.ifBlank { "下载 ${downloadStatus.percent}%" }
        DownloadState.Ready -> "可阅读"
        DownloadState.Failed -> downloadStatus.message.ifBlank { "点书重试" }
    }
    val statusColor = when (downloadStatus.state) {
        DownloadState.Ready -> appColors().sage
        DownloadState.Failed -> appColors().roseBeige
        else -> appColors().cocoa.copy(alpha = 0.68f)
    }
    val progressFraction = if (downloadStatus.state == DownloadState.Downloading) {
        downloadStatus.percent / 100f
    } else {
        progress / 100f
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = appColors().warmWhite),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.62f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onClick() }
        ) {
            BookCoverImage(
                url = book.coverUrl,
                title = book.title,
                author = book.author,
                modifier = Modifier.fillMaxSize()
            )

            Surface(
                color = statusColor.copy(alpha = 0.18f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Surface(
                onClick = onDelete,
                shape = RoundedCornerShape(10.dp),
                color = appColors().almond.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "删除",
                        tint = appColors().cocoa.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            val colors = appColors()
            val progressGradient = remember(colors.mistPink, colors.roseBeige) {
                Brush.horizontalGradient(listOf(colors.mistPink, colors.roseBeige))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(appColors().almond)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction)
                        .background(progressGradient)
                )
            }
        }
    }
}

private fun downloadStatusDetail(status: BookDownloadStatus): String {
    val progress = "下载 ${status.percent}%"
    val transfer = if (status.totalBytes > 0L) {
        "${formatDownloadBytes(status.downloadedBytes)}/${formatDownloadBytes(status.totalBytes)}"
    } else {
        status.downloadedChapters.takeIf { it > 0 && status.totalChapters > 0 }
            ?.let { "$it/${status.totalChapters} 章" }
    }
    val speed = status.speedBytesPerSecond.takeIf { it > 0L }?.let { "${formatDownloadBytes(it)}/秒" }
    val eta = status.etaSeconds?.takeIf { it >= 0L }?.let { "剩余约 ${it} 秒" }
    return listOf(progress, transfer, speed, eta).filterNotNull().joinToString(" · ")
}

private fun formatDownloadBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    if (bytes < 1024L * 1024L) return "${bytes / 1024L} KB"
    return "${bytes / (1024L * 1024L)} MB"
}

@Composable
private fun SearchResultCard(result: SearchResult, onClick: () -> Unit, onAddToShelf: () -> Unit) {
    SoftPanel(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            BookCoverImage(
                url = result.coverUrl,
                title = result.title,
                author = result.author,
                modifier = Modifier
                    .width(52.dp)
                    .height(76.dp),
                showAuthor = false
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    result.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = appColors().cocoa
                )
                Text(
                    result.author.ifBlank { "未知作者" },
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().roseBeige,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    result.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors().cocoa.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    color = appColors().sage.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        capabilityLabel(result),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().sage,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    "查看详情",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onAddToShelf,
                colors = ButtonDefaults.buttonColors(containerColor = appColors().roseBeige),
                modifier = Modifier.weight(1.6f),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    "老公就它了",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private enum class SearchMode(val label: String) {
    Search("搜索"),
    Rank("首页精选"),
    Random("随便看看")
}

private enum class SearchFilter(val label: String) {
    All("综合"),
    Title("书名"),
    Author("作者")
}

private fun capabilityLabel(result: SearchResult): String {
    return when {
        SourceCapability.READ_CHAPTER in result.capabilities -> "可在书架阅读"
        SourceCapability.TXT_IMPORT in result.capabilities -> "原站可能提供 TXT 下载"
        SourceCapability.EPUB_IMPORT in result.capabilities -> "可导入 EPUB"
        else -> "仅打开原站"
    }
}

@Composable
private fun ReaderLoadingOverlay(
    book: Book,
    message: String,
    onBack: () -> Unit,
    readerBackground: Color,
    readerTextColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(readerBackground)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = readerTextColor
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            RealBookCover(
                title = book.title,
                author = book.author,
                modifier = Modifier
                    .width(120.dp)
                    .height(170.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleLarge,
                color = readerTextColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Text(
                text = book.author.ifBlank { "未知作者" },
                style = MaterialTheme.typography.bodyMedium,
                color = readerTextColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp, bottom = 36.dp)
            )
            CircularProgressIndicator(
                color = appColors().roseBeige,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "正在加载...",
                style = MaterialTheme.typography.bodyMedium,
                color = readerTextColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = readerTextColor.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 48.dp, end = 48.dp)
            )
        }
    }
}

@Composable
private fun ReaderFailedOverlay(
    book: Book,
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    readerBackground: Color,
    readerTextColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(readerBackground)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = readerTextColor
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            RealBookCover(
                title = book.title,
                author = book.author,
                modifier = Modifier
                    .width(120.dp)
                    .height(170.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleLarge,
                color = readerTextColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Text(
                text = book.author.ifBlank { "未知作者" },
                style = MaterialTheme.typography.bodyMedium,
                color = readerTextColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )
            Text(
                text = "加载失败",
                style = MaterialTheme.typography.titleMedium,
                color = readerTextColor,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = readerTextColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 48.dp, end = 48.dp, bottom = 24.dp)
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = appColors().roseBeige),
                shape = MaterialTheme.shapes.large
            ) {
                Text("重新加载", color = Color.White)
            }
        }
    }
}

private val chapterTitlePattern = Regex(
    """^[\s　]*(?:第\s*[一二三四五六七八九十百千万零\d]+\s*(?:章|节|卷|集|回)[\s　:：]?\s*[^\n]*)$|^\s*(?:Chapter|CHAPTER)\s+\d+[\s　:：]?\s*[^\n]*$|^\s*\d+[\.．、\s][\s　]*[^\n]{0,40}$|^\s*[一二三四五六七八九十百千万零]+[\.．、\s][\s　]*[^\n]{0,40}$""",
    RegexOption.MULTILINE
)

private fun splitIntoChapters(text: String): List<Pair<String, String>> {
    val matches = chapterTitlePattern.findAll(text).toList()
    if (matches.size < 2) return listOf("" to text)

    val chapters = mutableListOf<Pair<String, String>>()
    matches.forEachIndexed { index, match ->
        val start = match.range.first
        val end = if (index < matches.size - 1) matches[index + 1].range.first else text.length
        val title = match.value.trim()
        val content = text.substring(start, end).trim()
        if (content.isNotBlank()) chapters += title to content
    }
    return chapters
}

private const val readerMeasurementChunkMaxChars = 10_000

private fun paginateParagraphs(
    text: String,
    style: TextStyle,
    textMeasurer: TextMeasurer,
    maxWidth: Int,
    maxHeight: Int
): List<String> {
    val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
    if (normalized.isBlank()) return emptyList()

    val safeMaxHeight = (maxHeight * 0.94f).toInt().coerceAtLeast(1)
    val paragraphs = normalized.split(Regex("\n{2,}"))
    val pages = mutableListOf<String>()
    val current = StringBuilder()

    fun measure(content: String): Int {
        return textMeasurer.measure(
            text = content,
            style = style,
            constraints = Constraints(maxWidth = maxWidth)
        ).size.height.coerceAtLeast(0)
    }

    fun flush() {
        val page = current.toString().trim()
        if (page.isNotBlank()) pages += page
        current.clear()
    }

    fun splitLongParagraph(paragraph: String) {
        splitReaderTextIntoCharacterBoundedChunks(paragraph, maxChars = readerMeasurementChunkMaxChars).forEach { measurementChunk ->
            var start = 0
            while (start < measurementChunk.length) {
                var low = start + 1
                var high = measurementChunk.length
                var bestEnd = low
                while (low <= high) {
                    val mid = (low + high) / 2
                    val chunk = measurementChunk.substring(start, mid)
                    if (measure(chunk) <= safeMaxHeight) {
                        bestEnd = mid
                        low = mid + 1
                    } else {
                        high = mid - 1
                    }
                }
                if (bestEnd <= start) bestEnd = (start + 1).coerceAtMost(measurementChunk.length)
                val chunk = measurementChunk.substring(start, bestEnd).trim()
                if (chunk.isNotBlank()) pages += chunk
                start = bestEnd
            }
        }
    }

    paragraphs.forEach { rawParagraph ->
        val paragraph = rawParagraph.trim()
        if (paragraph.isBlank()) return@forEach
        if (paragraph.length > readerMeasurementChunkMaxChars) {
            if (current.isNotBlank()) flush()
            splitLongParagraph(paragraph)
            return@forEach
        }

        val separator = if (current.isBlank()) "" else "\n\n"
        val candidate = if (current.isBlank()) paragraph else "$current$separator$paragraph"

        if (measure(candidate) <= safeMaxHeight) {
            if (current.isNotBlank()) current.append(separator)
            current.append(paragraph)
        } else {
            if (current.isNotBlank()) {
                flush()
            }
            val fitsSingle = measure(paragraph) <= safeMaxHeight
            if (fitsSingle) {
                current.append(paragraph)
            } else {
                splitLongParagraph(paragraph)
            }
        }
    }

    if (current.isNotBlank()) flush()
    return pages
}

/**
 * Prevents a single text-layout measurement from receiving an unbounded paragraph.
 * The renderer applies layout-based pagination after this guard has bounded the input.
 */
internal fun splitReaderTextIntoCharacterBoundedChunks(text: String, maxChars: Int): List<String> {
    require(maxChars > 0) { "maxChars must be positive" }
    if (text.isBlank()) return emptyList()

    return buildList {
        var start = 0
        while (start < text.length) {
            val end = (start + maxChars).coerceAtMost(text.length)
            add(text.substring(start, end))
            start = end
        }
    }
}

internal fun paginateReaderText(
    text: String,
    style: TextStyle,
    textMeasurer: TextMeasurer,
    maxWidth: Int,
    maxHeight: Int
): PaginationResult {
    val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
    if (normalized.isBlank()) return PaginationResult(pages = emptyList())
    if (maxWidth <= 0 || maxHeight <= 0) return PaginationResult(pages = emptyList())

    val chapters = splitIntoChapters(normalized)
    val allPages = mutableListOf<String>()
    val chapterInfos = mutableListOf<ReaderChapter>()
    chapters.forEach { (title, content) ->
        val startIndex = allPages.size
        val chapterPages = paginateParagraphs(content, style, textMeasurer, maxWidth, maxHeight)
        if (chapterPages.isNotEmpty()) {
            allPages += chapterPages
            chapterInfos += ReaderChapter(title = title, startPageIndex = startIndex)
        }
    }
    // 兜底过滤掉任何 trim 后为空的页面，避免 HorizontalPager 出现空白页
    val finalPages = allPages.map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { listOf(normalized) }
    return PaginationResult(
        pages = finalPages,
        chapters = chapterInfos
    )
}

data class ReaderChapter(val title: String, val startPageIndex: Int)

data class PaginationResult(
    val pages: List<String> = emptyList(),
    val chapters: List<ReaderChapter> = emptyList()
)

private data class PageCacheKey(
    val bookId: String,
    val fontSize: Int,
    val pageWidthPx: Int,
    val pageHeightPx: Int
)

private data class CachedReaderPages(
    val pages: List<String>,
    val chapters: List<ReaderChapter>
)

private val readerPageCache = LinkedHashMap<PageCacheKey, CachedReaderPages>(16, 0.75f, true)

internal fun readerProgress(
    pagesSize: Int,
    index: Int,
    offset: Int,
    itemHeight: Int?
): Float {
    if (pagesSize <= 1) return 0f
    val denominator = (pagesSize - 1).coerceAtLeast(1)
    val safeIndex = index.coerceIn(0, pagesSize - 1)
    val safeOffset = offset.coerceAtLeast(0)
    val height = itemHeight?.takeIf { it > 0 }?.toFloat()
    return if (height != null) {
        ((safeIndex + safeOffset / height) / denominator).coerceIn(0f, 1f)
    } else {
        (safeIndex.toFloat() / denominator).coerceIn(0f, 1f)
    }
}

/**
 * Maps a saved reader progress value to the closest page after repagination.
 * Font changes alter page count, but not the user's logical position in the
 * chapter, so this helper keeps the restore rule deterministic and testable.
 */
internal fun readerPageForProgress(progress: Float, pagesSize: Int): Int {
    if (pagesSize <= 1) return 0
    return (progress.coerceIn(0f, 1f) * (pagesSize - 1))
        .roundToInt()
        .coerceIn(0, pagesSize - 1)
}
