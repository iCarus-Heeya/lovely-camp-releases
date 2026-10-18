package com.lovelyreader.audiobook

import org.junit.Assert.assertEquals
import org.junit.Test

class AudiobookShelfLayoutTest {
    @Test
    fun remoteAudiobooksArePlacedBeforeLocalImportSection() {
        assertEquals(
            listOf(AudiobookShelfSection.REMOTE_BOOKS, AudiobookShelfSection.LOCAL_IMPORTS),
            audiobookShelfSectionOrder(hasRemoteBooks = true)
        )
    }
}
