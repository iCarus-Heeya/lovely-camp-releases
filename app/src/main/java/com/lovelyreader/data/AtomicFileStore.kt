package com.lovelyreader.data

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class AtomicFileStore(
    private val target: File,
    private val commit: (File, File) -> Unit = ::atomicReplace
) {
    fun writeText(text: String) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        try {
            temporary.outputStream().buffered().use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.flush()
            }
            commit(temporary, target)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    fun readTextOrNull(): String? = target.takeIf(File::isFile)?.readText(Charsets.UTF_8)

    fun checksumOrNull(): String? = target.takeIf(File::isFile)?.inputStream()?.use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private fun atomicReplace(source: File, target: File) {
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
