package com.lovelyreader.ui.video.ranking

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.lovelyreader.ui.video.isSafeDramaPosterUrl
import com.lovelyreader.ui.video.DramaPoster
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

/**
 * Loads a checked-in release poster first, then an allowlisted public poster
 * URL when the catalog has a verified metadata image.  Long-tail catalogue
 * rows may still fall back to the deterministic title art when no image is
 * available; arbitrary hosts never reach the network loader.
 *
 * Remote poster URLs are fetched only through the address-bound, redirect-safe
 * transport below.  This keeps the ranking surface from reintroducing the
 * DNS/HTTPS failure mode that the bundled snapshot was designed to avoid;
 * failures still fall back to deterministic title art.
 */
@Composable
fun RankingPoster(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val assetPath = url?.takeIf { it.startsWith("asset://") }?.removePrefix("asset://")
    if (assetPath == null) {
        val remote = url?.takeIf { isSafeRankingRemotePoster(it) }
        val context = LocalContext.current
        val bitmap = produceState<Bitmap?>(initialValue = null, remote) {
            value = remote?.let { imageUrl ->
                withContext(Dispatchers.IO) { loadRankingRemotePoster(context, imageUrl) }
            }
        }.value
        if (bitmap == null) {
            DramaPoster(url = null, title = title, modifier = modifier, contentScale = contentScale)
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "$title 封面",
                contentScale = contentScale,
                modifier = modifier
            )
        }
        return
    }
    val context = LocalContext.current
    val bitmap = produceState<Bitmap?>(initialValue = null, assetPath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(assetPath).use { stream -> BitmapFactory.decodeStream(stream) }
            }.getOrNull()
        }
    }.value
    if (bitmap == null) {
        DramaPoster(url = null, title = title, modifier = modifier, contentScale = contentScale)
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "$title 封面",
            contentScale = contentScale,
            modifier = modifier
        )
    }
}

internal fun isSafeRankingRemotePoster(url: String): Boolean {
    if (!isSafeDramaPosterUrl(url)) return false
    val host = runCatching { java.net.URI(url).host?.lowercase()?.trimEnd('.') }.getOrNull() ?: return false
    return host == "static.tvmaze.com" ||
        host == "upload.wikimedia.org" ||
        host == "thumb.wikimedia.org" ||
        host == "commons.wikimedia.org" ||
        host == "m.media-amazon.com"
}

internal fun resolveSafeRankingPosterRedirect(current: String, location: String): String? {
    val next = runCatching { URI(current).resolve(location).toString() }.getOrNull() ?: return null
    return next.takeIf(::isSafeRankingRemotePoster)
}

private suspend fun loadRankingRemotePoster(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
    rankingPosterCache.get(url)
        ?: readDiskPoster(context, url)?.also { rankingPosterCache.put(url, it) }
        ?: rankingPosterLoadSlots.withPermit {
        rankingPosterCache.get(url)
            ?: readDiskPoster(context, url)?.also { rankingPosterCache.put(url, it) }
            ?: loadRankingRemotePosterWith(url) { current ->
            runCatching {
                (URL(current).openConnection() as HttpURLConnection).apply {
                    connectTimeout = POSTER_CONNECT_TIMEOUT_MILLIS
                    readTimeout = POSTER_READ_TIMEOUT_MILLIS
                    instanceFollowRedirects = false
                    doInput = true
                    useCaches = true
                    requestMethod = "GET"
                    setRequestProperty("Accept", "image/avif,image/webp,image/jpeg,image/png,image/*;q=0.8")
                    // Wikimedia and TVmaze are public web CDNs; a browser-like
                    // UA/referer avoids their HTML/anti-bot response path.
                    setRequestProperty("User-Agent", POSTER_USER_AGENT)
                    setRequestProperty("Referer", url)
                }
            }.getOrNull()
            }.also { bitmap -> bitmap?.let { writeDiskPoster(context, url, it) } }
    }
}

/**
 * Injectable transport seam for the poster loader.  More importantly, the
 * response-code/read path is guarded as one unit: a timeout during TLS or
 * response headers is a normal missing-poster result, never an uncaught
 * coroutine exception that can terminate the activity.
 */
internal suspend fun loadRankingRemotePosterWith(
    url: String,
    openConnection: (String) -> HttpURLConnection?
): Bitmap? {
    runCatching { rankingPosterCache.get(url) }.getOrNull()?.let { return it }
    var current = url
    repeat(MAX_REDIRECTS + 1) {
        val connection = runCatching { openConnection(current) }.getOrNull() ?: return null
        try {
            when (connection.responseCode) {
                in 300..399 -> {
                    val location = connection.getHeaderField("Location") ?: return null
                    current = resolveSafeRankingPosterRedirect(current, location) ?: return null
                }
                in 200..299 -> {
                    if (!rankingPosterContentTypeAllowed(connection.contentType)) return null
                    if (connection.contentLengthLong > MAX_POSTER_BYTES) return null
                    val bytes = connection.inputStream.use(::readRankingPosterBytes) ?: return null
                    val bitmap = decodeRankingPoster(bytes) ?: return null
                    runCatching {
                        rankingPosterCache.put(url, bitmap)
                        if (current != url) rankingPosterCache.put(current, bitmap)
                    }
                    return bitmap
                }
                else -> return null
            }
        } catch (error: Exception) {
            // A poster is optional metadata.  Network/TLS/read failures must
            // leave the deterministic title art in place without crashing the
            // ranking screen or the process.
            runCatching {
                Log.d(RANKING_POSTER_TAG, "poster unavailable: $current (${error.javaClass.simpleName})")
            }
            return null
        } finally {
            connection.disconnect()
        }
    }
    return null
}

/** A missing Content-Type is common on image CDNs; explicit HTML is not an image. */
internal fun rankingPosterContentTypeAllowed(contentType: String?): Boolean {
    val normalized = contentType?.trim().orEmpty()
    return normalized.isEmpty() || normalized.startsWith("image/", ignoreCase = true)
}

private fun readRankingPosterBytes(stream: java.io.InputStream): ByteArray? {
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

private fun decodeRankingPoster(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = rankingPosterSampleSize(bounds.outWidth, bounds.outHeight)
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun posterDiskFile(context: Context, url: String): File {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return File(File(context.cacheDir, POSTER_DISK_CACHE_DIR), "$digest.jpg")
}

private fun readDiskPoster(context: Context, url: String): Bitmap? = runCatching {
    posterDiskFile(context, url).takeIf { it.isFile && it.length() in 1..MAX_DISK_POSTER_BYTES }?.let {
        BitmapFactory.decodeFile(it.absolutePath)
    }
}.getOrNull()

private fun writeDiskPoster(context: Context, url: String, bitmap: Bitmap) {
    runCatching {
        val directory = File(context.cacheDir, POSTER_DISK_CACHE_DIR)
        if (!directory.exists()) directory.mkdirs()
        val target = posterDiskFile(context, url)
        val temporary = File(directory, "${target.name}.tmp")
        FileOutputStream(temporary).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output))
        }
        if (temporary.length() <= MAX_DISK_POSTER_BYTES) {
            temporary.renameTo(target)
        } else {
            temporary.delete()
        }
        val files = directory.listFiles().orEmpty()
            .filter { it.isFile && it.extension == "jpg" }
            .sortedByDescending { it.lastModified() }
        var total = 0L
        files.forEach { file ->
            total += file.length()
            if (total > MAX_DISK_POSTER_TOTAL_BYTES) file.delete()
        }
    }
}

private fun rankingPosterSampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (width / sample > POSTER_MAX_WIDTH_PX || height / sample > POSTER_MAX_HEIGHT_PX) {
        sample = (sample * 2).coerceAtMost(128)
        if (sample == 128) break
    }
    return sample
}

private const val RANKING_POSTER_TAG = "RankingPoster"
private const val MAX_REDIRECTS = 3
private const val MAX_POSTER_BYTES = 8L * 1024L * 1024L
private const val POSTER_CONNECT_TIMEOUT_MILLIS = 4_000
private const val POSTER_READ_TIMEOUT_MILLIS = 8_000
private const val POSTER_MAX_WIDTH_PX = 720
private const val POSTER_MAX_HEIGHT_PX = 960
private const val POSTER_DISK_CACHE_DIR = "ranking-posters"
private const val MAX_DISK_POSTER_BYTES = 512L * 1024L
private const val MAX_DISK_POSTER_TOTAL_BYTES = 32L * 1024L * 1024L
private const val POSTER_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile LovelyReader/0.9"
private val rankingPosterLoadSlots = Semaphore(permits = 3)
private val rankingPosterCache = object : LruCache<String, Bitmap>(12 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
}
