package com.lovelyreader.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Displays a source-provided public cover and falls back to the deterministic local cover. */
@Composable
fun BookCoverImage(
    url: String?,
    title: String,
    author: String,
    modifier: Modifier = Modifier,
    showAuthor: Boolean = true
) {
    val context = LocalContext.current
    val fixtureAsset = url?.takeIf { it.startsWith("fixture://") }?.removePrefix("fixture://")
    val safeUrl = url?.takeIf(::isSafeBookCoverUrl)
    val bitmap = produceState<Bitmap?>(initialValue = null, safeUrl, fixtureAsset) {
        value = fixtureAsset?.let { asset ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open("fixture/$asset").use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
        } ?: safeUrl?.let { loadBookCover(it) }
    }.value

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "$title 封面",
            // The same source artwork is shown in cards with different bounds.
            // Fit keeps the complete cover (including publisher marks) visible
            // instead of cropping a different slice on each page.
            contentScale = when (highFidelityBookCoverScalePolicy()) {
                BookCoverScalePolicy.Fit -> ContentScale.Fit
            },
            modifier = modifier
        )
    } else if (fixtureAsset != null) {
        FixtureBookCover(title = title, author = author, modifier = modifier)
    } else {
        RealBookCover(title = title, author = author, modifier = modifier, showAuthor = showAuthor)
    }
}

/**
 * A deterministic, illustrated fallback for Debug high-fidelity fixtures.
 * It avoids the generic avatar-like placeholder when a fixture asset is not
 * bundled while keeping the production network/fallback path unchanged.
 */
@Composable
private fun FixtureBookCover(title: String, author: String, modifier: Modifier = Modifier) {
    val dark = title.hashCode() and 1 == 0
    val top = if (dark) Color(0xFF302D3B) else Color(0xFF7A5361)
    val bottom = if (dark) Color(0xFFB87954) else Color(0xFFE7C6AE)
    Box(
        modifier = modifier
            .background(Brush.verticalGradient(listOf(top, bottom)), RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = .26f), Color.Transparent),
                        radius = 240f
                    )
                )
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text(
                title,
                color = Color.White.copy(alpha = .94f),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                author,
                color = Color.White.copy(alpha = .78f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

fun isSafeBookCoverUrl(value: String): Boolean {
    val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    val host = uri.host?.lowercase()?.trim('[', ']')?.trimEnd('.') ?: return false
    return !host.contains(":") && host !in setOf("localhost", "::1") && !host.endsWith(".local") &&
        !host.startsWith("127.") && !host.startsWith("10.") && !host.startsWith("192.168.") &&
        !host.matches(Regex("""172\.(1[6-9]|2[0-9]|3[0-1])\..*"""))
}

private suspend fun loadBookCover(url: String): Bitmap? = withContext(Dispatchers.IO) {
    bookCoverCache.get(url) ?: runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 8_000
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("Accept", "image/*")
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream)?.also { bitmap -> bookCoverCache.put(url, bitmap) }
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

private val bookCoverCache = object : LruCache<String, Bitmap>(24) {}
