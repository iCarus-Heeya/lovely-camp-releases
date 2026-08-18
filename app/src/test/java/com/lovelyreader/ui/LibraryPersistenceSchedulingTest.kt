package com.lovelyreader.ui

import com.lovelyreader.data.LibraryPersistence
import com.lovelyreader.data.LibraryRepository
import com.lovelyreader.data.LibrarySnapshot
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryPersistenceSchedulingTest {
    @Test
    fun `scheduling persistence saves one repository snapshot without recursively scheduling itself`() = runTest {
        val repository = LibraryRepository()
        val persistence = RecordingPersistence()

        persistLibrarySnapshot(this, persistence, repository::snapshot)
        advanceUntilIdle()

        assertEquals(1, persistence.savedSnapshots.size)
        assertEquals(repository.snapshot(), persistence.savedSnapshots.single())
    }

    private class RecordingPersistence : LibraryPersistence {
        val savedSnapshots = mutableListOf<LibrarySnapshot>()

        override suspend fun save(snapshot: LibrarySnapshot) {
            savedSnapshots += snapshot
        }

        override suspend fun load(): LibrarySnapshot? = null
    }
}
