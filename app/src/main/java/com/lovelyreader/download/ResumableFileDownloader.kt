package com.lovelyreader.download

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class DownloadTransferProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val speedBytesPerSecond: Long
) {
    val percent: Int?
        get() = totalBytes?.takeIf { it > 0L }
            ?.let { (downloadedBytes * 100L / it).toInt().coerceIn(0, 100) }
}

interface DownloadConnection : AutoCloseable {
    val responseCode: Int
    val contentLength: Long?
    val input: InputStream
}

/** File downloader with Range resume and atomic target commit. */
class ResumableFileDownloader(
    private val open: (url: String, startByte: Long) -> DownloadConnection,
    private val nowNanos: () -> Long = System::nanoTime
) {
    suspend fun download(
        url: String,
        partial: File,
        target: File,
        onProgress: suspend (DownloadTransferProgress) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        partial.parentFile?.mkdirs()
        target.parentFile?.mkdirs()
        val requestedStart = partial.takeIf { it.isFile }?.length()?.coerceAtLeast(0L) ?: 0L
        open(url, requestedStart).use { connection ->
            require(connection.responseCode == 200 || connection.responseCode == 206) {
                "下载服务响应异常：${connection.responseCode}"
            }
            val append = requestedStart > 0L && connection.responseCode == 206
            val initialBytes = if (append) requestedStart else 0L
            if (!append && partial.exists()) partial.delete()
            var downloaded = initialBytes
            val total = connection.contentLength?.takeIf { it >= 0L }?.let { length ->
                if (append) initialBytes + length else length
            }
            var lastSampleAt = nowNanos()
            var lastSampleBytes = downloaded
            onProgress(DownloadTransferProgress(downloaded, total, 0L))
            FileOutputStream(partial, append).buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val read = connection.input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    val now = nowNanos()
                    val elapsed = (now - lastSampleAt).coerceAtLeast(1L)
                    val speed = ((downloaded - lastSampleBytes) * 1_000_000_000L / elapsed).coerceAtLeast(0L)
                    if (now - lastSampleAt >= 100_000_000L) {
                        onProgress(DownloadTransferProgress(downloaded, total, speed))
                        lastSampleAt = now
                        lastSampleBytes = downloaded
                    }
                }
            }
            onProgress(DownloadTransferProgress(downloaded, total, 0L))
        }
        atomicMove(partial, target)
        target
    }

    companion object {
        fun cleanupStalePartials(directory: File, olderThanMillis: Long, nowMillis: Long = System.currentTimeMillis()): Int {
            if (!directory.isDirectory) return 0
            return directory.listFiles()
                .orEmpty()
                .filter { it.name.endsWith(".part") && nowMillis - it.lastModified() > olderThanMillis }
                .count { it.delete() }
        }

        private fun atomicMove(source: File, target: File) {
            try {
                Files.move(
                    source.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (_: UnsupportedOperationException) {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
