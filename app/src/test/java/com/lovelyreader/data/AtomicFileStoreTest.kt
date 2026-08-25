package com.lovelyreader.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AtomicFileStoreTest {
    @Test
    fun `failed commit leaves the previous value intact`() {
        val target = File(Files.createTempDirectory("atomic-store").toFile(), "library.txt")
        target.writeText("old")
        val store = AtomicFileStore(target) { _, _ -> error("simulated interruption") }

        assertThrows(IllegalStateException::class.java) { store.writeText("new") }
        assertEquals("old", target.readText())
    }
}
