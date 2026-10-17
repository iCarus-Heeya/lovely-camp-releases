package com.lovelyreader.download

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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

data class DownloadContentRange(
    val start: Long,
    val end: Long,
    val total: Long?
)

interface DownloadConnection : AutoCloseable {
    val responseCode: Int
    val contentLength: Long?
    val input: InputStream
    val contentRangeStart: Long?
        get() = null
    val contentRangeEnd: Long?
        get() = null
    val contentRangeTotal: Long?
        get() = null
}

/**
 * Signals that the caller must refresh its session before retrying the
 * request. The resumable downloader must not consume this as a transient
 * network failure, otherwise a refresh response can be mistaken for audio
 * data and corrupt the partial file.
 */
open class DownloadAuthenticationException(message: String) : IOException(message)

fun parseContentRange(header: String?): DownloadContentRange? {
    val match = Regex("^bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)$", RegexOption.IGNORE_CASE)
        .matchEntire(header?.trim().orEmpty()) ?: return null
    val start = match.groupValues[1].toLongOrNull() ?: return null
    val end = match.groupValues[2].toLongOrNull() ?: return null
    val rawTotal = match.groupValues[3]
    val total = if (rawTotal == "*") null else rawTotal.toLongOrNull() ?: return null
    if (end < start || (total != null && end >= total)) return null
    return DownloadContentRange(start = start, end = end, total = total)
}

fun parseContentRangeStart(header: String?): Long? {
    return parseContentRange(header)?.start
}

/** File downloader with Range resume and atomic target commit. */
class ResumableFileDownloader(
    private val open: suspend (url: String, startByte: Long) -> DownloadConnection,
    private val nowNanos: () -> Long = System::nanoTime,
    private val retryDelay: suspend (Long) -> Unit = { delay(it) },
    private val maxAttempts: Int = 3
) {
    suspend fun download(
        url: String,
        partial: File,
        target: File,
        expectedBytes: Long? = null,
        partialIdentity: String? = null,
        onProgress: suspend (DownloadTransferProgress) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        require(maxAttempts > 0) { "下载重试次数必须大于 0" }
        require(expectedBytes == null || expectedBytes >= 0L) { "预期字节数不能为负数" }
        partial.parentFile?.mkdirs()
        target.parentFile?.mkdirs()
        withDownloadLock(partial) {
            val identityFile = partialIdentityFile(partial)
            if (!partialIdentity.isNullOrBlank()) {
                val storedIdentity = identityFile.takeIf { it.isFile }?.readText()
                if (storedIdentity != partialIdentity) {
                    partial.delete()
                    identityFile.delete()
                }
                identityFile.writeText(partialIdentity)
            }
            var attempt = 1
            var result: File? = null
            while (result == null) {
                try {
                    result = downloadOnce(
                        url = url,
                        partial = partial,
                        target = target,
                        onProgress = onProgress,
                        expectedBytes = expectedBytes
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    if (!isRetryable(error) || attempt >= maxAttempts) throw error
                    retryDelay(retryDelayMillis(attempt))
                    attempt += 1
                }
            }
            val completed = checkNotNull(result) { "下载未生成目标文件" }
            if (!partialIdentity.isNullOrBlank()) identityFile.delete()
            completed
        }
    }

    private suspend fun downloadOnce(
        url: String,
        partial: File,
        target: File,
        onProgress: suspend (DownloadTransferProgress) -> Unit,
        expectedBytes: Long?
    ): File {
        val requestedStart = partial.takeIf { it.isFile }?.length()?.coerceAtLeast(0L) ?: 0L
        val connection = try {
            open(url, requestedStart)
        } catch (error: IOException) {
            throw TransientDownloadException(error)
        }
        connection.use { activeConnection ->
            val responseCode = try {
                activeConnection.responseCode
            } catch (error: DownloadAuthenticationException) {
                throw error
            } catch (error: IOException) {
                throw TransientDownloadException(error)
            }
            require(responseCode == 200 || responseCode == 206) {
                "下载服务响应异常：$responseCode"
            }
            val append = requestedStart > 0L && responseCode == 206
            val responseLength = activeConnection.contentLength?.takeIf { it >= 0L }
            val declaredResponseBytes = if (responseCode == 206) {
                val rangeStart = activeConnection.contentRangeStart
                val rangeEnd = activeConnection.contentRangeEnd
                val rangeTotal = activeConnection.contentRangeTotal
                require(rangeStart != null && rangeEnd != null) {
                    "断点续传响应缺少完整 Content-Range"
                }
                require(rangeStart == requestedStart) {
                    "断点续传响应范围错误：预期从 $requestedStart 开始"
                }
                val rangeBytes = rangeEnd - rangeStart + 1L
                require(rangeBytes > 0L) {
                    "断点续传响应范围长度异常"
                }
                require(responseLength == null || responseLength == rangeBytes) {
                    "断点续传响应长度与 Content-Range 不一致"
                }
                require(expectedBytes == null || rangeTotal == null || rangeTotal == expectedBytes) {
                    "断点续传响应总长度与预期文件不一致"
                }
                rangeBytes
            } else {
                responseLength
            }
            val initialBytes = if (append) requestedStart else 0L
            if (!append && partial.exists()) partial.delete()
            var downloaded = initialBytes
            val total = responseLength?.let { length ->
                if (append) initialBytes + length else length
            }
            var lastSampleAt = nowNanos()
            var lastSampleBytes = downloaded
            onProgress(DownloadTransferProgress(downloaded, total, 0L))
            FileOutputStream(partial, append).buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val read = try {
                        activeConnection.input.read(buffer)
                    } catch (error: IOException) {
                        throw TransientDownloadException(error)
                    }
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
            val actualResponseBytes = downloaded - initialBytes
            if (declaredResponseBytes != null && actualResponseBytes != declaredResponseBytes) {
                if (actualResponseBytes > declaredResponseBytes) {
                    RandomAccessFile(partial, "rw").use { file ->
                        file.setLength(initialBytes + declaredResponseBytes)
                    }
                }
                throw IncompleteDownloadException(declaredResponseBytes, actualResponseBytes)
            }
            if (expectedBytes != null && downloaded != expectedBytes) {
                throw IncompleteDownloadException(expectedBytes, downloaded)
            }
            onProgress(DownloadTransferProgress(downloaded, total, 0L))
        }
        atomicMove(partial, target)
        return target
    }

    private suspend fun <T> withDownloadLock(partial: File, block: suspend () -> T): T {
        val lockFile = File(partial.parentFile, "${partial.name}.lock")
        return FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            var fileLock: FileLock? = null
            while (fileLock == null) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                fileLock = try {
                    channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    null
                }
                if (fileLock == null) delay(DOWNLOAD_LOCK_RETRY_MILLIS)
            }
            fileLock.use { block() }
        }
    }

    private fun isRetryable(error: Throwable): Boolean =
        error is TransientDownloadException || error is IncompleteDownloadException

    private fun retryDelayMillis(attempt: Int): Long = when (attempt) {
        1 -> 250L
        else -> 750L
    }

    companion object {
        private const val DOWNLOAD_LOCK_RETRY_MILLIS = 25L

        fun cleanupStalePartials(directory: File, olderThanMillis: Long, nowMillis: Long = System.currentTimeMillis()): Int {
            if (!directory.isDirectory) return 0
            return directory.listFiles()
                .orEmpty()
                .filter { it.name.endsWith(".part") && nowMillis - it.lastModified() > olderThanMillis }
                .count { partial ->
                    val lockFile = File(partial.parentFile, "${partial.name}.lock")
                    val cleaned = try {
                        FileChannel.open(
                            lockFile.toPath(),
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE
                        ).use { channel ->
                            val fileLock = try {
                                channel.tryLock()
                            } catch (_: OverlappingFileLockException) {
                                null
                            }
                            if (fileLock == null) {
                                false
                            } else {
                                fileLock.use {
                                    if (!partial.isFile || nowMillis - partial.lastModified() <= olderThanMillis) {
                                        false
                                    } else {
                                        val deleted = partial.delete()
                                        if (deleted) partialIdentityFile(partial).delete()
                                        deleted
                                    }
                                }
                            }
                        }
                    } catch (_: IOException) {
                        false
                    } catch (_: SecurityException) {
                        false
                    }
                    if (cleaned) lockFile.delete()
                    cleaned
                }
        }

        private fun partialIdentityFile(partial: File): File = File(partial.parentFile, "${partial.name}.meta")

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

class IncompleteDownloadException(
    val expectedBytes: Long,
    val actualBytes: Long
) : IOException("下载数据不完整：预期 ${expectedBytes} bytes，实际 ${actualBytes} bytes")

private class TransientDownloadException(cause: IOException) : IOException("下载连接中断", cause)
