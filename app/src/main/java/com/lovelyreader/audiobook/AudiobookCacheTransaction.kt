package com.lovelyreader.audiobook

import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

/**
 * Serialize canonical audio, marker and snapshot mutations across repository
 * instances and Android processes. The lock file itself is not a cache entry.
 */
fun <T> withAudiobookCacheTransactionLock(root: File, block: () -> T): T {
    root.mkdirs()
    val lockFile = File(root, ".audiobook-cache.lock")
    FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
        var fileLock: FileLock? = null
        while (fileLock == null) {
            fileLock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (fileLock == null) {
                try {
                    Thread.sleep(25L)
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("等待有声书缓存锁时被中断", error)
                }
            }
        }
        return fileLock.use { block() }
    }
}
