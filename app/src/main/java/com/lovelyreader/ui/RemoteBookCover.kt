package com.lovelyreader.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
    val safeUrl = url?.takeIf(::isSafeBookCoverUrl)
    val bitmap = produceState<Bitmap?>(initialValue = null, safeUrl) {
        value = safeUrl?.let { loadBookCover(it) }
    }.value

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "$title 封面",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        RealBookCover(title = title, author = author, modifier = modifier, showAuthor = showAuthor)
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
