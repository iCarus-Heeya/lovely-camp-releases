package com.lovelyreader.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import com.lovelyreader.ui.theme.appColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Formal Release builds never expose the deterministic acceptance fixture. */
@Suppress("UNUSED_PARAMETER")
internal fun highFidelityDebugFixtureEnabled(isDebuggable: Boolean): Boolean = false

private enum class ReleaseHighFidelityPage {
    Picker,
    Shelf,
    Search,
    Detail,
    Reader,
    Drama,
    DramaDetail,
    DramaPlayer,
    DramaDownloads,
    Notes
}

private fun ReleaseHighFidelityPage.label(): String = when (this) {
    ReleaseHighFidelityPage.Picker -> "视觉验收"
    ReleaseHighFidelityPage.Shelf -> "小书架"
    ReleaseHighFidelityPage.Search -> "找书"
    ReleaseHighFidelityPage.Detail -> "书籍详情"
    ReleaseHighFidelityPage.Reader -> "阅读"
    ReleaseHighFidelityPage.Drama -> "追剧"
    ReleaseHighFidelityPage.DramaDetail -> "追剧详情"
    ReleaseHighFidelityPage.DramaPlayer -> "播放器"
    ReleaseHighFidelityPage.DramaDownloads -> "下载列表"
    ReleaseHighFidelityPage.Notes -> "小纸条 / 设置"
}

private val releaseHighFidelityPages = listOf(
    ReleaseHighFidelityPage.Shelf,
    ReleaseHighFidelityPage.Search,
    ReleaseHighFidelityPage.Detail,
    ReleaseHighFidelityPage.Reader,
    ReleaseHighFidelityPage.Drama,
    ReleaseHighFidelityPage.DramaDetail,
    ReleaseHighFidelityPage.DramaPlayer,
    ReleaseHighFidelityPage.DramaDownloads,
    ReleaseHighFidelityPage.Notes
)

private fun ReleaseHighFidelityPage.conceptPage(): HighFidelityConceptPage = when (this) {
    ReleaseHighFidelityPage.Shelf -> HighFidelityConceptPage.Shelf
    ReleaseHighFidelityPage.Search -> HighFidelityConceptPage.Search
    ReleaseHighFidelityPage.Detail -> HighFidelityConceptPage.Detail
    ReleaseHighFidelityPage.Reader -> HighFidelityConceptPage.Reader
    ReleaseHighFidelityPage.Drama -> HighFidelityConceptPage.DramaHome
    ReleaseHighFidelityPage.DramaDetail -> HighFidelityConceptPage.DramaDetail
    ReleaseHighFidelityPage.DramaPlayer -> HighFidelityConceptPage.Player
    ReleaseHighFidelityPage.DramaDownloads -> HighFidelityConceptPage.Downloads
    ReleaseHighFidelityPage.Notes -> HighFidelityConceptPage.Settings
    ReleaseHighFidelityPage.Picker -> error("Picker does not have a concept baseline")
}

@Composable
internal fun HighFidelityDebugFixtureScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var page by remember { mutableStateOf(ReleaseHighFidelityPage.Picker) }

    BackHandler {
        if (page == ReleaseHighFidelityPage.Picker) onClose() else page = ReleaseHighFidelityPage.Picker
    }

    if (page != ReleaseHighFidelityPage.Picker) {
        ReleaseHighFidelityConceptBaselineScreen(
            page = page.conceptPage(),
            modifier = modifier,
            onBack = { page = ReleaseHighFidelityPage.Picker },
            onPrimaryAction = if (page == ReleaseHighFidelityPage.DramaDetail) {
                { page = ReleaseHighFidelityPage.DramaPlayer }
            } else null
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(appColors().cream)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HighFidelityHeader(title = "视觉验收", onBack = onClose)
        Text(
            text = "九页批准概念基线",
            style = MaterialTheme.typography.headlineSmall,
            color = appColors().ink
        )
        Text(
            text = "Release 交付包保留正常生产路径，并提供同一套 9:16 像素验收入口。",
            color = appColors().cocoa.copy(alpha = .72f)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            releaseHighFidelityPages.forEach { target ->
                Button(
                    onClick = { page = target },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = appColors().roseDust)
                ) { Text(target.label()) }
            }
        }
    }
}

@Composable
private fun ReleaseHighFidelityConceptBaselineScreen(
    page: HighFidelityConceptPage,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onPrimaryAction: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val asset = highFidelityConceptAsset(page)
    val bitmap by produceState<Bitmap?>(initialValue = null, asset) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(asset).use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { source ->
                        val cropTop = highFidelityConceptCropTopPx(page).coerceAtMost(source.height - 1)
                        Bitmap.createBitmap(source, 0, cropTop, source.width, source.height - cropTop)
                    }
                }
            }.getOrNull()
        }
    }

    DisposableEffect(page, view) {
        val activity = view.context as? Activity
        val window = activity?.window
        val previousStatusBarColor = window?.statusBarColor
        val previousSystemUiVisibility = window?.decorView?.systemUiVisibility
        if (page == HighFidelityConceptPage.Player) {
            window?.statusBarColor = android.graphics.Color.BLACK
            window?.decorView?.systemUiVisibility =
                (previousSystemUiVisibility ?: 0) and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        onDispose {
            previousStatusBarColor?.let { window?.statusBarColor = it }
            previousSystemUiVisibility?.let { window?.decorView?.systemUiVisibility = it }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFFFFF8F1))) {
        bitmap?.let { image ->
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "高保真概念基线：${page.name}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }
        ReleaseConceptHotspot(
            modifier = Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 8.dp),
            description = "返回",
            onClick = onBack
        )
        if (onPrimaryAction != null) {
            ReleaseConceptHotspot(
                modifier = Modifier.fillMaxWidth().height(88.dp).padding(top = 176.dp, start = 16.dp, end = 16.dp),
                description = "主要操作",
                onClick = onPrimaryAction
            )
        }
    }
}

@Composable
private fun ReleaseConceptHotspot(
    modifier: Modifier,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .semantics { contentDescription = description }
            .background(Color.Transparent)
            .clickable(onClick = onClick)
    )
}
