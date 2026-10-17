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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** A small, memory-only poster loader for metadata already exposed by a title card. */
@Composable
fun DramaPoster(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
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
            contentScale = contentScale,
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
    if (uri.userInfo != null) return false
    return host !in setOf("localhost", "::1") && !host.startsWith("fe80:") && !host.endsWith(".local") &&
        !host.startsWith("127.") && !host.startsWith("10.") && !host.startsWith("192.168.") &&
        !host.matches(Regex("""172\.(1[6-9]|2[0-9]|3[0-1])\..*"""))
}

/** Resolves a poster redirect without allowing HTTP, credentials, or private hosts. */
internal fun resolveDramaPosterRedirect(currentUrl: String, location: String?): String? {
    val rawLocation = location?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val current = runCatching { URI(currentUrl.trim()) }.getOrNull() ?: return null
    val resolved = runCatching { current.resolve(rawLocation) }.getOrNull() ?: return null
    return resolved.toString().takeIf(::isSafeDramaPosterUrl)
}

/** Keeps full-size CDN images from exhausting the Compose/native bitmap heap. */
internal fun dramaPosterSampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
    if (width <= 0 || height <= 0 || maxWidth <= 0 || maxHeight <= 0) return 1
    var sample = 1
    while (width / sample > maxWidth || height / sample > maxHeight) {
        sample = (sample * 2).coerceAtMost(128)
        if (sample == 128) break
    }
    return sample
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
    posterCache.get(url) ?: posterLoadSlots.withPermit {
        posterCache.get(url) ?: runCatching {
            var currentUrl = url
            repeat(MAX_POSTER_REDIRECTS + 1) {
                val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = POSTER_CONNECT_TIMEOUT_MILLIS
                    readTimeout = POSTER_READ_TIMEOUT_MILLIS
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    setRequestProperty("Accept", "image/avif,image/webp,image/jpeg,image/png,image/*;q=0.8")
                    setRequestProperty("User-Agent", POSTER_USER_AGENT)
                    setRequestProperty("Referer", url)
                }
                try {
                    when (connection.responseCode) {
                        in 300..399 -> {
                            currentUrl = resolveDramaPosterRedirect(currentUrl, connection.getHeaderField("Location"))
                                ?: return@runCatching null
                        }
                        in 200..299 -> {
                            val contentLength = connection.contentLengthLong
                            if (contentLength > MAX_POSTER_BYTES) return@runCatching null
                            val contentType = connection.contentType.orEmpty()
                            if (contentType.isNotBlank() && !contentType.startsWith("image/", ignoreCase = true)) {
                                return@runCatching null
                            }
                            val bytes = connection.inputStream.use(::readPosterBytes) ?: return@runCatching null
                            return@runCatching rememberDecodedDramaPoster(
                                decoded = decodeDramaPoster(bytes),
                                cache = { bitmap ->
                                    posterCache.put(url, bitmap)
                                    if (currentUrl != url) posterCache.put(currentUrl, bitmap)
                                }
                            )
                        }
                        else -> return@runCatching null
                    }
                } finally {
                    connection.disconnect()
                }
            }
            null
        }.getOrNull()
    }
}

private fun readPosterBytes(stream: java.io.InputStream): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = stream.read(buffer)
        if (count < 0) break
        total += count
        if (total > MAX_POSTER_BYTES) return null
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun decodeDramaPoster(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = dramaPosterSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maxWidth = POSTER_MAX_WIDTH_PX,
            maxHeight = POSTER_MAX_HEIGHT_PX
        )
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

/** Keeps the successful decode as the loader result while updating both cache keys. */
internal fun <T> rememberDecodedDramaPoster(decoded: T?, cache: (T) -> Unit): T? =
    decoded?.also(cache)

private const val MAX_POSTER_REDIRECTS = 3
private const val MAX_POSTER_BYTES = 8L * 1024L * 1024L
private const val POSTER_CONNECT_TIMEOUT_MILLIS = 4_000
private const val POSTER_READ_TIMEOUT_MILLIS = 8_000
private const val POSTER_MAX_WIDTH_PX = 720
private const val POSTER_MAX_HEIGHT_PX = 960
private const val POSTER_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile LovelyReader/0.9"
private val posterLoadSlots = Semaphore(permits = 3)
private val posterCache = object : LruCache<String, Bitmap>(12 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
}
