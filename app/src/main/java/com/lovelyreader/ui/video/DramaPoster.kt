package com.lovelyreader.ui.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.lovelyreader.ui.theme.appColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** A small, memory-only poster loader for metadata already exposed by a title card. */
@Composable
fun DramaPoster(url: String?, title: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fixtureAsset = url?.takeIf { it.startsWith("fixture://") }?.removePrefix("fixture://")
    val safeUrl = url?.takeIf(::isSafeDramaPosterUrl)
    val bitmap = produceState<Bitmap?>(initialValue = null, safeUrl, fixtureAsset) {
        value = fixtureAsset?.let { asset ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open("fixture/$asset").use { stream ->
                        BitmapFactory.decodeStream(stream)?.let(::cropFixturePoster)
                    }
                }.getOrNull()
            }
        } ?: safeUrl?.let { imageUrl -> loadDramaPoster(imageUrl) }
    }.value

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "$title 封面",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        DramaPosterFallback(title = title, modifier = modifier)
    }
}

/** Fixture art is stored as a contact sheet so it can be reused by cards; detail needs one poster. */
private fun cropFixturePoster(bitmap: Bitmap): Bitmap {
    if (bitmap.width <= 0 || bitmap.height <= bitmap.width * 1.2f) return bitmap
    val firstPosterHeight = (bitmap.width * 1.02f).toInt().coerceIn(1, bitmap.height)
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, firstPosterHeight, null, true)
}

fun isSafeDramaPosterUrl(value: String): Boolean {
    val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
    return host !in setOf("localhost", "::1") && !host.endsWith(".local") &&
        !host.startsWith("127.") && !host.startsWith("10.") && !host.startsWith("192.168.") &&
        !host.matches(Regex("""172\.(1[6-9]|2[0-9]|3[0-1])\..*"""))
}

@Composable
private fun DramaPosterFallback(title: String, modifier: Modifier) {
    val colors = appColors()
    Box(
        modifier = modifier.background(Brush.linearGradient(listOf(colors.mistPink, colors.almond))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.take(8),
            color = colors.cocoa,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private suspend fun loadDramaPoster(url: String): Bitmap? = withContext(Dispatchers.IO) {
    posterCache.get(url) ?: runCatching {
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
                BitmapFactory.decodeStream(stream)?.also { bitmap -> posterCache.put(url, bitmap) }
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

private val posterCache = object : LruCache<String, Bitmap>(24) {}
