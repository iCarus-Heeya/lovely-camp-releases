package com.lovelyreader.video.ranking

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shipped catalogue itself, rather than only testing a hand-written
 * capabilities fixture. This catches the exact regression where the device
 * receives a broad year list but the parser rejects it and the UI falls back
 * to an empty/unavailable filter state.
 */
class BundledRankingCatalogAssetTest {
    @Test
    fun `bundled asset exposes current Chinese years and full scope`() {
        val asset = listOf(
            File("src/main/assets/ranking/catalog-index.json"),
            File("app/src/main/assets/ranking/catalog-index.json")
        ).firstOrNull(File::isFile) ?: error("bundled ranking catalog index asset not found")
        val root = JSONObject(asset.readText())
        val capabilities = parseRankingCapabilities(root.getJSONObject("capabilities").toString()).getOrThrow()

        assertTrue(capabilities.hasVerifiedFilters)
        assertEquals(
            listOf("aggregate", "source-imdb", "source-tvmaze"),
            capabilities.supportedBoards.map { it.id }
        )
        assertEquals(
            listOf("综合评分", "IMDb评分", "剧集评分"),
            capabilities.supportedBoards.map { it.label }
        )
        assertTrue(capabilities.supportedRegions.any { it.id == "CN" && it.label == "中国" })
        assertTrue(capabilities.supportedYears.containsAll(listOf(2024, 2025, 2026)))
        assertEquals(77, capabilities.supportedYears.size)
        val itemAsset = root.getString("itemAsset")
        assertEquals("ranking/items.ndjson", itemAsset)
        val rows = listOf(
            File("src/main/assets/$itemAsset"),
            File("app/src/main/assets/$itemAsset")
        ).firstOrNull(File::isFile) ?: error("bundled ranking rows asset not found")
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0
        rows.forEachLine { line ->
            assertTrue(line.isNotBlank())
            JSONObject(line)
            digest.update((line + "\n").toByteArray(Charsets.UTF_8))
            count += 1
        }
        assertEquals(root.getInt("itemCount"), count)
        assertEquals(root.getString("catalogFingerprint"), digest.digest().joinToString("") { "%02x".format(it) }.take(16))
        assertTrue(count >= 100_000)
        assertEquals("imdb-rated-scope-v7-preferred-genres-tvmaze-full-index", root.getString("methodologyVersion"))
        assertTrue(root.getJSONObject("provenance").getString("scope").contains("at least 100 votes"))
        val metadataCoverage = root.getJSONObject("metadataCoverage")
        assertEquals(count, metadataCoverage.getInt("total"))
        assertTrue(metadataCoverage.getInt("chineseTitle") > 70_000)
        assertTrue(metadataCoverage.getInt("summary") > 10_000)
        assertTrue(metadataCoverage.getInt("poster") > 12_000)
        assertTrue(metadataCoverage.getInt("region") > 129_000)
    }
}
