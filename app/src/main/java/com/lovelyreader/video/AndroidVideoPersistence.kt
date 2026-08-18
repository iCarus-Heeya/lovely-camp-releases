package com.lovelyreader.video

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** SharedPreferences implementation that stores only viewing and download metadata. */
class AndroidVideoPersistence(context: Context) : VideoLibraryPersistence {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): VideoLibrarySnapshot? {
        val encoded = preferences.getString(SNAPSHOT_KEY, null) ?: return null
        return runCatching { decode(JSONObject(encoded)) }.getOrNull()
    }

    override suspend fun save(snapshot: VideoLibrarySnapshot) = withContext(Dispatchers.IO) {
        preferences.edit().putString(SNAPSHOT_KEY, encode(snapshot).toString()).apply()
    }

    private fun encode(snapshot: VideoLibrarySnapshot): JSONObject = JSONObject().apply {
        snapshot.recentViewing?.let { recent ->
            put("recent", JSONObject().apply {
                put("titleId", recent.titleId)
                put("titleName", recent.titleName)
                put("sourceId", recent.sourceId)
                put("episodeId", recent.episodeId)
                put("titleDetailUrl", recent.titleDetailUrl)
                put("positionMillis", recent.positionMillis)
                put("viewedAtMillis", recent.viewedAtMillis)
            })
        }
        put("selectedSources", JSONObject().apply {
            snapshot.selectedSources.forEach { (titleId, sourceId) -> put(titleId, sourceId) }
        })
        put("downloads", JSONArray().apply {
            snapshot.downloads.forEach { task ->
                put(JSONObject().apply {
                    put("id", task.id)
                    put("titleId", task.titleId)
                    put("sourceId", task.sourceId)
                    put("episodeId", task.episodeId)
                    put("directUrlHash", task.directUrlHash)
                    task.systemDownloadId?.let { put("systemDownloadId", it) }
                    put("status", task.status.name)
                    task.localUri?.let { put("localUri", it) }
                })
            }
        })
    }

    private fun decode(root: JSONObject): VideoLibrarySnapshot {
        val selectedSources = root.optJSONObject("selectedSources")?.let { selected ->
            selected.keys().asSequence().associateWith { titleId -> selected.optString(titleId) }
        }.orEmpty()
        val downloads = root.optJSONArray("downloads")?.let { array ->
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.toTaskOrNull() }
        }.orEmpty()
        return VideoLibrarySnapshot(
            recentViewing = root.optJSONObject("recent")?.toRecentViewingOrNull(),
            selectedSources = selectedSources,
            downloads = downloads
        )
    }

    private fun JSONObject.toRecentViewingOrNull(): VideoRecentViewing? {
        val titleId = optString("titleId").trim()
        val titleName = optString("titleName").trim()
        val sourceId = optString("sourceId").trim()
        val episodeId = optString("episodeId").trim()
        if (titleId.isEmpty() || titleName.isEmpty() || sourceId.isEmpty() || episodeId.isEmpty()) return null
        return VideoRecentViewing(
            titleId = titleId,
            titleName = titleName,
            sourceId = sourceId,
            episodeId = episodeId,
            titleDetailUrl = optString("titleDetailUrl").trim(),
            positionMillis = optLong("positionMillis").coerceAtLeast(0L),
            viewedAtMillis = optLong("viewedAtMillis").coerceAtLeast(0L)
        )
    }

    private fun JSONObject.toTaskOrNull(): VideoDownloadTask? {
        val id = optString("id").trim()
        val titleId = optString("titleId").trim()
        val sourceId = optString("sourceId").trim()
        val episodeId = optString("episodeId").trim()
        val directUrlHash = optString("directUrlHash").trim()
        val status = runCatching { VideoDownloadStatus.valueOf(optString("status")) }.getOrNull()
        if (id.isEmpty() || titleId.isEmpty() || sourceId.isEmpty() || episodeId.isEmpty() || directUrlHash.isEmpty() || status == null) {
            return null
        }
        return VideoDownloadTask(
            id = id,
            titleId = titleId,
            sourceId = sourceId,
            episodeId = episodeId,
            directUrlHash = directUrlHash,
            systemDownloadId = if (has("systemDownloadId")) optLong("systemDownloadId") else null,
            status = status,
            localUri = optString("localUri").trim().takeIf(String::isNotEmpty)
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "lovely_reader_video_library"
        const val SNAPSHOT_KEY = "snapshot_v1"
    }
}
