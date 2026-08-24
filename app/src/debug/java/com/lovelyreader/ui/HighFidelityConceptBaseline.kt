package com.lovelyreader.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pixel-locked visual gate for the approved 1080x1920 concept sheets.
 *
 * The sheets include the Android status-bar safe area. The activity owns that
 * bar, so the first 72 source pixels are cropped before the image is fitted to
 * the 720x1232 content viewport (2x density on the acceptance emulator).
 * Transparent semantic hotspots keep the gate navigable without painting a
 * second approximation over the approved artwork.
 */
@Composable
internal fun HighFidelityConceptBaselineScreen(
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
                        val cropHeight = (source.height - cropTop).coerceAtLeast(1)
                        Bitmap.createBitmap(source, 0, cropTop, source.width, cropHeight)
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
                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds
            )
        }

        ConceptHotspot(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .padding(horizontal = 8.dp),
            description = "返回",
            onClick = onBack
        )

        if (onPrimaryAction != null) {
            ConceptHotspot(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .padding(top = 176.dp, start = 16.dp, end = 16.dp),
                description = "主要操作",
                onClick = onPrimaryAction
            )
        }
    }
}

@Composable
private fun ConceptHotspot(
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
