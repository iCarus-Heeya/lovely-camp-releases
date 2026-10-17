package com.lovelyreader.audiobook

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookCacheReconciliationTest {
    @Test
    fun `valid canonical audio and marker survive reconciliation`() {
        val root = Files.createTempDirectory("lovely-audio-reconcile-valid").toFile()
        val chapter = chapter()
        val audio = File(root, "chapter.wav").apply { writeBytes(ByteArray(10)) }
        marker(audio).writeText(audiobookChapterAudioIdentity(chapter))

        val removed = reconcileAudiobookAudioCache(
            chapters = listOf(chapter),
            fileFor = { audio },
            markerFor = ::marker,
            readMarker = { it.takeIf(File::isFile)?.readText() }
        )

        assertEquals(0, removed)
        assertTrue(audio.exists())
        assertTrue(marker(audio).exists())
    }

    @Test
    fun `mismatched canonical identity is removed on reconciliation`() {
        val root = Files.createTempDirectory("lovely-audio-reconcile-mismatch").toFile()
        val chapter = chapter(audioSha256 = "sha-new")
        val audio = File(root, "chapter.wav").apply { writeBytes(ByteArray(10)) }
        marker(audio).writeText(audiobookChapterAudioIdentity(chapter.copy(audioSha256 = "sha-old")))

        val removed = reconcileAudiobookAudioCache(
            chapters = listOf(chapter),
            fileFor = { audio },
            markerFor = ::marker,
            readMarker = { it.takeIf(File::isFile)?.readText() }
        )

        assertEquals(1, removed)
        assertFalse(audio.exists())
        assertFalse(marker(audio).exists())
    }

    @Test
    fun `marker without canonical audio is removed on reconciliation`() {
        val root = Files.createTempDirectory("lovely-audio-reconcile-marker").toFile()
        val chapter = chapter()
        val audio = File(root, "chapter.wav")
        marker(audio).writeText(audiobookChapterAudioIdentity(chapter))

        val removed = reconcileAudiobookAudioCache(
            chapters = listOf(chapter),
            fileFor = { audio },
            markerFor = ::marker,
            readMarker = { it.takeIf(File::isFile)?.readText() }
        )

        assertEquals(1, removed)
        assertFalse(audio.exists())
        assertFalse(marker(audio).exists())
    }

    @Test
    fun `canonical audio without marker is removed on reconciliation`() {
        val root = Files.createTempDirectory("lovely-audio-reconcile-audio").toFile()
        val chapter = chapter()
        val audio = File(root, "chapter.wav").apply { writeBytes(ByteArray(10)) }

        val removed = reconcileAudiobookAudioCache(
            chapters = listOf(chapter),
            fileFor = { audio },
            markerFor = ::marker,
            readMarker = { it.takeIf(File::isFile)?.readText() }
        )

        assertEquals(1, removed)
        assertFalse(audio.exists())
        assertFalse(marker(audio).exists())
    }

    @Test
    fun `orphan canonical audio and marker are removed while temporary files survive`() {
        val root = Files.createTempDirectory("lovely-audio-reconcile-orphan").toFile()
        val chapter = chapter()
        val orphan = File(root, "orphan.wav").apply { writeBytes(ByteArray(5)) }
        marker(orphan).writeText("orphan")
        val partial = File(root, "orphan.wav.part").apply { writeText("resume") }
        val lock = File(root, "orphan.wav.part.lock").apply { writeText("lock") }

        val removed = reconcileAudiobookAudioCache(
            chapters = listOf(chapter),
            fileFor = { File(root, "chapter.wav") },
            markerFor = ::marker,
            readMarker = { it.takeIf(File::isFile)?.readText() },
            managedFiles = listOf(orphan, marker(orphan), partial, lock)
        )

        assertEquals(1, removed)
        assertFalse(orphan.exists())
        assertFalse(marker(orphan).exists())
        assertTrue(partial.exists())
        assertTrue(lock.exists())
    }

    @Test
    fun `orphan marker without audio is removed without touching unrelated temporary files`() {
        val root = Files.createTempDirectory("lovely-audio-reconcile-orphan-marker").toFile()
        val chapter = chapter()
        val orphan = File(root, "orphan.wav")
        marker(orphan).writeText("orphan")
        val partial = File(root, "orphan.wav.part").apply { writeText("resume") }

        val removed = reconcileAudiobookAudioCache(
            chapters = listOf(chapter),
            fileFor = { File(root, "chapter.wav") },
            markerFor = ::marker,
            readMarker = { it.takeIf(File::isFile)?.readText() },
            managedFiles = listOf(marker(orphan), partial)
        )

        assertEquals(1, removed)
        assertFalse(marker(orphan).exists())
        assertTrue(partial.exists())
    }

    @Test
    fun `revision isolated pending audio is not treated as orphan canonical audio`() {
        val root = Files.createTempDirectory("lovely-audio-reconcile-pending").toFile()
        val chapter = chapter()
        val pending = File(root, "chapter.deadbeef.wav.pending").apply { writeBytes(ByteArray(10)) }

        val removed = reconcileAudiobookAudioCache(
            chapters = listOf(chapter),
            fileFor = { File(root, "chapter.wav") },
            markerFor = ::marker,
            readMarker = { it.takeIf(File::isFile)?.readText() },
            managedFiles = listOf(pending)
        )

        assertEquals(0, removed)
        assertTrue(pending.exists())
    }

    @Test
    fun `orphan cleanup is blocked when the persisted chapter snapshot is incomplete`() {
        val root = Files.createTempDirectory("lovely-audio-reconcile-incomplete").toFile()
        val chapter = chapter()
        val orphan = File(root, "orphan.wav").apply { writeBytes(ByteArray(5)) }
        marker(orphan).writeText("orphan")

        val removed = reconcileAudiobookAudioCache(
            chapters = listOf(chapter),
            fileFor = { File(root, "chapter.wav") },
            markerFor = ::marker,
            readMarker = { it.takeIf(File::isFile)?.readText() },
            managedFiles = listOf(orphan, marker(orphan)),
            canDeleteManagedFile = { false }
        )

        assertEquals(0, removed)
        assertTrue(orphan.exists())
        assertTrue(marker(orphan).exists())
    }

    @Test
    fun `failed orphan deletion is not reported as removed`() {
        val root = Files.createTempDirectory("lovely-audio-reconcile-delete-failure").toFile()
        val chapter = chapter()
        val orphan = File(root, "orphan.wav").apply { writeBytes(ByteArray(5)) }
        marker(orphan).writeText("orphan")

        val removed = reconcileAudiobookAudioCache(
            chapters = listOf(chapter),
            fileFor = { File(root, "chapter.wav") },
            markerFor = ::marker,
            readMarker = { it.takeIf(File::isFile)?.readText() },
            managedFiles = listOf(orphan, marker(orphan)),
            deleteFile = { file -> file != orphan && file.delete() }
        )

        assertEquals(0, removed)
        assertTrue(orphan.exists())
    }

    private fun chapter(audioSha256: String = "sha-new") = AudiobookChapter(
        id = "chapter-1",
        bookId = "book-1",
        ordinal = 1,
        title = "第一章",
        status = AudiobookChapterStatus.READY,
        audioArtifactId = "artifact-new",
        audioSha256 = audioSha256,
        audioBytes = 10L,
        audioContentType = "audio/wav"
    )

    private fun marker(audio: File): File = File(
        audio.parentFile,
        audiobookAudioCacheMarkerName(audio.name)
    )
}
