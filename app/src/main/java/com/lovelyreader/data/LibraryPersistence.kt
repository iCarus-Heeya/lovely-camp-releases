package com.lovelyreader.data

interface LibraryPersistence {
    suspend fun save(snapshot: LibrarySnapshot)
    suspend fun load(): LibrarySnapshot?
}
