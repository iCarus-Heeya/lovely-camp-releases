package com.lovelyreader.audiobook

import java.io.File

/**
 * Removes managed canonical audio artifacts that cannot be proven to belong
 * to the current remote chapter revision.
 */
fun reconcileAudiobookAudioCache(
    chapters: List<AudiobookChapter>,
    fileFor: (AudiobookChapter) -> File,
    markerFor: (File) -> File,
    readMarker: (File) -> String?,
    managedFiles: List<File> = emptyList(),
    canDeleteManagedFile: (File) -> Boolean = { true },
    deleteFile: (File) -> Boolean = { it.delete() }
): Int {
    val expectedAudioPaths = chapters
        .map { fileFor(it).absolutePath }
        .toSet()
    var removed = 0
    chapters.forEach { chapter ->
        val audio = fileFor(chapter)
        val marker = markerFor(audio)
        if (!audio.exists() && !marker.exists()) return@forEach
        val identity = marker.takeIf(File::isFile)?.let { readMarker(it) }
        if (audiobookAudioCacheIsValid(audio.isFile, audio.length(), chapter, identity)) return@forEach
        val hadArtifact = audio.exists() || marker.exists()
        val audioDeleted = deleteIfAbsent(audio, deleteFile)
        val markerDeleted = deleteIfAbsent(marker, deleteFile)
        if (hadArtifact && audioDeleted && markerDeleted) removed += 1
    }

    managedFiles.forEach { file ->
        when {
            file.name.endsWith(".wav") && file.absolutePath !in expectedAudioPaths && canDeleteManagedFile(file) -> {
                val marker = markerFor(file)
                val hadArtifact = file.exists() || marker.exists()
                val audioDeleted = deleteIfAbsent(file, deleteFile)
                val markerDeleted = deleteIfAbsent(marker, deleteFile)
                if (hadArtifact && audioDeleted && markerDeleted) removed += 1
            }
            file.name.endsWith(".wav.meta") && canDeleteManagedFile(file) -> {
                val audio = File(file.parentFile, file.name.removeSuffix(".meta"))
                val hadMarker = file.exists()
                if (audio.absolutePath !in expectedAudioPaths && hadMarker && deleteIfAbsent(file, deleteFile)) {
                    removed += 1
                }
            }
        }
    }
    return removed
}

private fun deleteIfAbsent(file: File, deleteFile: (File) -> Boolean): Boolean =
    !file.exists() || (deleteFile(file) && !file.exists())
