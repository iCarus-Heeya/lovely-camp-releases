package com.lovelyreader.audiobook

import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookCacheTransactionTest {
    @Test
    fun `cache transaction lock serializes concurrent callbacks`() {
        val root = Files.createTempDirectory("lovely-audio-transaction-lock").toFile()
        val events = Collections.synchronizedList(mutableListOf<String>())
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)

        val first = thread(start = true) {
            withAudiobookCacheTransactionLock(root) {
                events += "first-start"
                firstEntered.countDown()
                assertTrue(releaseFirst.await(2, TimeUnit.SECONDS))
                events += "first-end"
            }
        }
        assertTrue(firstEntered.await(2, TimeUnit.SECONDS))

        val second = thread(start = true) {
            withAudiobookCacheTransactionLock(root) {
                events += "second"
                secondEntered.countDown()
            }
        }

        assertFalse(secondEntered.await(150, TimeUnit.MILLISECONDS))
        releaseFirst.countDown()
        assertTrue(secondEntered.await(2, TimeUnit.SECONDS))
        first.join(2_000)
        second.join(2_000)

        assertEquals(listOf("first-start", "first-end", "second"), events)
    }
}
