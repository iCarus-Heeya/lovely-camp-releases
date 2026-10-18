package com.lovelyreader.video.ranking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingContractTest {
    private val now = 1_700_000_000_000L
    private val fixture = """
        {"schemaVersion":1,"requestId":"req-1","boardId":"popular","filters":{"period":"week","mediaType":"ALL","region":null,"year":null,"genre":null,"language":null,"limit":20,"cursor":null},"snapshotId":"snap-1","generatedAt":"2023-11-14T22:00:00Z","dataAsOf":"2023-11-14T21:00:00Z","expiresAt":"2023-11-15T22:00:00Z","staleUntil":"2023-11-16T22:00:00Z","freshness":"FRESH","availability":"AVAILABLE","methodologyVersion":"debug-v1","identityMapVersion":"debug-v1","coverage":{"sourceCount":2,"coveredSourceIds":["a","b"],"complete":true},"sourceStatuses":[{"sourceId":"a","status":"AVAILABLE"},{"sourceId":"b","status":"AVAILABLE"}],"items":[{"canonicalId":"tt-1","rank":1,"displayTitle":"示例片","originalTitle":"Example","aliases":["样例"],"mediaType":"MOVIE","releaseYear":2023,"countries":["CN"],"genres":["Drama"],"posterUrl":"https://img.example/poster.jpg","summary":"summary","aggregateScore":8.5,"sourceRatings":[{"sourceId":"a","value":8.5,"scaleMax":10.0,"voteCount":100}],"sourceRanks":[{"sourceId":"a","rank":1,"previousRank":2,"rankDelta":1,"isNew":false}],"coverage":{"sourceCount":1,"coveredSourceIds":["a"],"complete":true},"searchTitle":"示例片"}],"nextCursor":"next"}
    """.trimIndent()

    @Test fun `parses valid fixture and preserves optional values`() {
        val result = parseRankingSnapshot(fixture, now)
        assertTrue(result.isSuccess)
        val snapshot = result.getOrThrow()
        assertEquals("snap-1", snapshot.snapshotId)
        assertEquals("next", snapshot.nextCursor)
        assertEquals(null, snapshot.totalCount)
        assertEquals(8.5, snapshot.items.single().aggregateScore!!, 0.0)
        assertTrue(parseRankingSnapshot(fixture.replace(",\"originalTitle\":\"Example\"", "").replace(",\"summary\":\"summary\"", "").replace(",\"posterUrl\":\"https://img.example/poster.jpg\"", ""), now).isSuccess)
    }

    @Test fun `parses the score that actually controls rank order`() {
        val snapshot = parseRankingSnapshot(
            fixture.replace("\"aggregateScore\":8.5", "\"aggregateScore\":8.5,\"rankingScore\":28.3"),
            now
        ).getOrThrow()
        assertEquals(28.3, snapshot.items.single().rankingScore!!, 0.0)
    }

    @Test fun `parses optional full catalogue count`() {
        val snapshot = parseRankingSnapshot(
            fixture.replace("\"nextCursor\":\"next\"", "\"nextCursor\":\"next\",\"totalCount\":63681"),
            now
        ).getOrThrow()
        assertEquals(63681, snapshot.totalCount)
    }

    @Test fun `parses optional metadata coverage without making older snapshots invalid`() {
        val snapshot = parseRankingSnapshot(
            fixture.replace(
                "\"items\":[",
                "\"metadataCoverage\":{\"total\":200509,\"chineseTitle\":72049,\"summary\":10805,\"poster\":12352,\"region\":129197,\"rating\":200508},\"items\":["
            ),
            now
        ).getOrThrow()
        assertEquals(
            RankingMetadataCoverage(200509, 72049, 10805, 12352, 129197, 200508),
            snapshot.metadataCoverage
        )
        assertEquals(null, parseRankingSnapshot(fixture, now).getOrThrow().metadataCoverage)
    }

    @Test fun `parses versioned capabilities and exposes only declared filters`() {
        val result = parseRankingCapabilities(
            """
            {"schemaVersion":1,"boards":[{"id":"aggregate","label":"综合参考"}],"mediaTypes":["SERIES"],"periods":[{"id":"week","label":"本周"}],"regions":[{"id":"CN","label":"中国"}],"years":[2026,2024],"genres":[{"id":"drama","label":"剧情"}],"languages":[{"id":"zh-CN","label":"中文"}]}
            """.trimIndent()
        )
        assertTrue(result.isSuccess)
        val capabilities = result.getOrThrow()
        assertEquals(listOf(RankingBoardOption("aggregate", "综合参考")), capabilities.supportedBoards)
        assertEquals(listOf(RankingMediaType.SERIES), capabilities.supportedMediaTypes)
        assertEquals(listOf("week"), capabilities.supportedPeriods.map { it.id })
        assertEquals(listOf("CN"), capabilities.supportedRegions.map { it.id })
        assertEquals(listOf(2026, 2024), capabilities.supportedYears)
        assertEquals(listOf("drama"), capabilities.supportedGenres.map { it.id })
        assertEquals(listOf("zh-CN"), capabilities.supportedLanguages.map { it.id })
        assertTrue(capabilities.hasVerifiedFilters)
        assertTrue(capabilities.canLoadRanking)
    }

    @Test fun `rejects duplicate or unsupported capability declarations`() {
        assertTrue(
            parseRankingCapabilities(
                """{"schemaVersion":1,"boards":[{"id":"aggregate","label":"A"},{"id":"aggregate","label":"B"}],"mediaTypes":[]}"""
            ).isFailure
        )
        assertTrue(
            parseRankingCapabilities(
                """{"schemaVersion":1,"boards":[],"mediaTypes":["SERIES","SERIES"]}"""
            ).isFailure
        )
        assertTrue(parseRankingCapabilities("""{"schemaVersion":2,"boards":[],"mediaTypes":[]}""").isFailure)
        assertTrue(
            parseRankingCapabilities(
                """{"schemaVersion":1,"boards":[{"id":"aggregate","label":"综合"}],"mediaTypes":["ALL"],"regions":[{"id":"CN","label":"中"},{"id":"CN","label":"重复"}]}"""
            ).isFailure
        )
        assertTrue(
            parseRankingCapabilities(
                """{"schemaVersion":1,"boards":[{"id":"aggregate","label":"综合"}],"mediaTypes":["ALL"],"years":[2026,2201]}"""
            ).isFailure
        )
    }

    @Test fun `ranking posters remain placeholders until address bound redirect safe loading exists`() {
        listOf("https://img.example/poster.jpg", "https://8.8.8.8/poster.jpg", "https://[2001:4860:4860::8888]/poster.jpg").forEach { poster ->
            val snapshot = parseRankingSnapshot(fixture.replace("https://img.example/poster.jpg", poster), now).getOrThrow()
            assertEquals(null, snapshot.items.single().posterUrl)
            assertEquals("示例片", snapshot.items.single().displayTitle)
        }
    }

    @Test fun `approved metadata poster hosts remain available for release cards`() {
        val snapshot = parseRankingSnapshot(
            fixture.replace("https://img.example/poster.jpg", "https://static.tvmaze.com/uploads/images/medium_portrait/1/2.jpg"),
            now
        ).getOrThrow()
        assertEquals("https://static.tvmaze.com/uploads/images/medium_portrait/1/2.jpg", snapshot.items.single().posterUrl)

        val wikimedia = parseRankingSnapshot(
            fixture.replace("https://img.example/poster.jpg", "https://commons.wikimedia.org/wiki/Special:FilePath/poster.jpg"),
            now
        ).getOrThrow()
        assertEquals("https://commons.wikimedia.org/wiki/Special:FilePath/poster.jpg", wikimedia.items.single().posterUrl)

        val thumbnail = parseRankingSnapshot(
            fixture.replace("https://img.example/poster.jpg", "https://thumb.wikimedia.org/wikipedia/zh/thumb/a/ab/poster.jpg/330px-poster.jpg"),
            now
        ).getOrThrow()
        assertEquals("https://thumb.wikimedia.org/wikipedia/zh/thumb/a/ab/poster.jpg/330px-poster.jpg", thumbnail.items.single().posterUrl)
    }

    @Test fun `release poster asset paths remain available and traversal is rejected`() {
        val asset = "asset://ranking/posters/imdb_tt0111161.img"
        val snapshot = parseRankingSnapshot(fixture.replace("https://img.example/poster.jpg", asset), now).getOrThrow()
        assertEquals(asset, snapshot.items.single().posterUrl)
        assertTrue(parseRankingSnapshot(fixture.replace("https://img.example/poster.jpg", "asset://ranking/posters/../secret.img"), now).isFailure)
    }

    @Test fun `rejects private poster, oversized or non-string optional text`() {
        assertTrue(parseRankingSnapshot(fixture.replace("https://img.example/poster.jpg", "https://127.0.0.1/poster.jpg"), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("\"summary\":\"summary\"", "\"summary\":\"${"x".repeat(5001)}\""), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("\"summary\":\"summary\"", "\"summary\":42"), now).isFailure)
    }

    @Test fun `rejects numeric strings non integer ranks and non finite scores`() {
        assertTrue(parseRankingSnapshot(fixture.replace("\"rank\":1", "\"rank\":1.5"), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("\"aggregateScore\":8.5", "\"aggregateScore\":\"8.5\""), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("\"aggregateScore\":8.5", "\"aggregateScore\":null"), now).isSuccess)
        assertTrue(parseRankingSnapshot(fixture.replace("\"limit\":20", "\"limit\":51"), now).isFailure)
    }

    @Test fun `rejects bracketed or alternate private IPv4 poster hosts`() {
        assertTrue(parseRankingSnapshot(fixture.replace("https://img.example/poster.jpg", "https://[::1]/poster.jpg"), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("https://img.example/poster.jpg", "https://[fd00::1]/poster.jpg"), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("https://img.example/poster.jpg", "https://127.1/poster.jpg"), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("https://img.example/poster.jpg", "https://2130706433/poster.jpg"), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("https://img.example/poster.jpg", "https://[::ffff:127.0.0.1]/poster.jpg"), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("https://img.example/poster.jpg", "https://[::FFFF:7f00:1]/poster.jpg"), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("https://img.example/poster.jpg", "https://[0:0:0:0:0:ffff:7f00:1]/poster.jpg"), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("https://img.example/poster.jpg", "https://10.65536/poster.jpg"), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("https://img.example/poster.jpg", "https://[192.0.2.1::]/poster.jpg"), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("https://img.example/poster.jpg", "https://[2001:4860:4860::8888]/poster.jpg"), now).isSuccess)
        assertTrue(parseRankingSnapshot(fixture.replace("https://img.example/poster.jpg", "https://[2001:db8::1]/poster.jpg"), now).isFailure)
    }

    @Test fun `rejects integer fields that overflow Int`() {
        assertTrue(parseRankingSnapshot(fixture.replace("\"rank\":1", "\"rank\":2147483648"), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("\"sourceCount\":2", "\"sourceCount\":2147483648"), now).isFailure)
    }

    @Test fun `ignores unknown fields`() {
        assertTrue(parseRankingSnapshot(fixture.replaceFirst("{", "{\"futureField\":true,"), now).isSuccess)
    }

    @Test fun `rejects duplicate canonical ids and ranks`() {
        val second = fixture.replace("\"items\":[{", "\"items\":[{")
            .replace("}],\"nextCursor\"", "},{\"canonicalId\":\"tt-1\",\"rank\":2,\"displayTitle\":\"Second\",\"aliases\":[],\"mediaType\":\"MOVIE\",\"countries\":[],\"genres\":[],\"coverage\":{\"sourceCount\":0,\"coveredSourceIds\":[],\"complete\":true},\"searchTitle\":\"Second\"}],\"nextCursor\"")
        assertTrue(parseRankingSnapshot(second, now).isFailure)
        val duplicateRank = second.replace("\"canonicalId\":\"tt-1\",\"rank\":2", "\"canonicalId\":\"tt-2\",\"rank\":1")
        assertTrue(parseRankingSnapshot(duplicateRank, now).isFailure)
    }

    @Test fun `rejects missing fields invalid poster stale future schema and limit`() {
        assertTrue(parseRankingSnapshot(fixture.replace("\"requestId\":\"req-1\",", ""), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("https://img.example/poster.jpg", "http://img.example/poster.jpg"), now).isFailure)
        assertTrue(
            parseRankingSnapshot(
                fixture.replace("\"freshness\":\"FRESH\"", "\"freshness\":\"STALE\"")
                    .replace("2023-11-15T22:00:00Z", "2023-11-14T23:00:00Z"),
                now + 3_600_000L
            ).isSuccess
        )
        assertTrue(parseRankingSnapshot(fixture.replace("2023-11-14T22:00:00Z", "2030-11-14T22:00:00Z"), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("\"schemaVersion\":1", "\"schemaVersion\":2"), now).isFailure)
        assertTrue(validateRankingRequest(RankingRequest("x", null, RankingMediaType.ALL, null, null, null, null, 51)).isFailure)
    }

    @Test fun `rejects inconsistent source coverage ratings ranks and scores`() {
        assertTrue(
            parseRankingSnapshot(
                fixture.replace("\"coveredSourceIds\":[\"a\",\"b\"]", "\"coveredSourceIds\":[\"a\",\"a\"]"),
                now
            ).isFailure
        )
        assertTrue(
            parseRankingSnapshot(
                fixture.replace("\"sourceId\":\"a\",\"status\":\"AVAILABLE\"},{\"sourceId\":\"b\",\"status\":\"AVAILABLE\"", "\"sourceId\":\"a\",\"status\":\"AVAILABLE\"},{\"sourceId\":\"a\",\"status\":\"DEGRADED\"") ,
                now
            ).isFailure
        )
        assertTrue(
            parseRankingSnapshot(
                fixture.replace("\"sourceRatings\":[{\"sourceId\":\"a\",\"value\":8.5,\"scaleMax\":10.0,\"voteCount\":100}]", "\"sourceRatings\":[{\"sourceId\":\"a\",\"value\":8.5,\"scaleMax\":10.0,\"voteCount\":100},{\"sourceId\":\"a\",\"value\":7.5,\"scaleMax\":10.0,\"voteCount\":50}]") ,
                now
            ).isFailure
        )
        assertTrue(parseRankingSnapshot(fixture.replace("\"aggregateScore\":8.5", "\"aggregateScore\":10.1"), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("\"methodologyVersion\":\"debug-v1\",", ""), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("\"rankDelta\":1", "\"rankDelta\":2"), now).isFailure)
        assertTrue(
            parseRankingSnapshot(
                fixture.replace(
                    "\"sourceId\":\"a\",\"status\":\"AVAILABLE\"",
                    "\"sourceId\":\"a\",\"status\":\"AVAILABLE\",\"errorCode\":\"UNKNOWN\""
                ),
                now
            ).isFailure
        )
    }

    @Test fun `rejects freshness values that disagree with local clock`() {
        assertTrue(parseRankingSnapshot(fixture.replace("\"freshness\":\"FRESH\"", "\"freshness\":\"STALE\""), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("\"freshness\":\"FRESH\"", "\"freshness\":\"EXPIRED\""), now).isFailure)
    }

    @Test fun `rejects availability states that contradict entries or source health`() {
        assertTrue(parseRankingSnapshot(fixture.replace("\"availability\":\"AVAILABLE\"", "\"availability\":\"EMPTY\""), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("\"availability\":\"AVAILABLE\"", "\"availability\":\"PARTIAL\""), now).isFailure)
        assertTrue(parseRankingSnapshot(fixture.replace("\"availability\":\"AVAILABLE\"", "\"availability\":\"UNAVAILABLE\""), now).isFailure)
        val empty = fixture.substringBefore("\"items\":[")
            .replace("\"availability\":\"AVAILABLE\"", "\"availability\":\"EMPTY\"") +
            "\"items\":[],\"nextCursor\":null}"
        assertTrue(parseRankingSnapshot(empty, now).isSuccess)
    }

    @Test fun `requires error codes only for unavailable source statuses`() {
        assertTrue(
            parseRankingSnapshot(
                fixture.replace("\"sourceId\":\"a\",\"status\":\"AVAILABLE\"", "\"sourceId\":\"a\",\"status\":\"AVAILABLE\",\"errorCode\":\"NO_SNAPSHOT\"") ,
                now
            ).isFailure
        )
        val missingErrorCode = fixture
            .replace("\"sourceId\":\"b\",\"status\":\"AVAILABLE\"", "\"sourceId\":\"b\",\"status\":\"UNAVAILABLE\"")
            .replace("\"availability\":\"AVAILABLE\"", "\"availability\":\"PARTIAL\"")
            .replace("\"coveredSourceIds\":[\"a\",\"b\"]", "\"coveredSourceIds\":[\"a\"]")
            .replace("\"complete\":true", "\"complete\":false")
        assertTrue(parseRankingSnapshot(missingErrorCode, now).isFailure)
    }

    @Test fun `rejects missing or inconsistent coverage source declarations`() {
        assertTrue(parseRankingSnapshot(fixture.replace("\"coveredSourceIds\":[\"a\",\"b\"]", "\"coveredSourceIds\":null"), now).isFailure)
        val emptyWithoutSources = fixture.substringBefore("\"items\":[")
            .replace("\"availability\":\"AVAILABLE\"", "\"availability\":\"EMPTY\"")
            .replace("\"sourceCount\":2,\"coveredSourceIds\":[\"a\",\"b\"],\"complete\":true", "\"sourceCount\":1,\"coveredSourceIds\":[\"a\"],\"complete\":true")
            .replace("\"sourceStatuses\":[{\"sourceId\":\"a\",\"status\":\"AVAILABLE\"},{\"sourceId\":\"b\",\"status\":\"AVAILABLE\"}]", "\"sourceStatuses\":[]") +
            "\"items\":[],\"nextCursor\":null}"
        assertTrue(parseRankingSnapshot(emptyWithoutSources, now).isFailure)
    }

    @Test fun `rejects partial coverage that claims an unavailable source contributed`() {
        val partial = fixture
            .replace("\"availability\":\"AVAILABLE\"", "\"availability\":\"PARTIAL\"")
            .replace("\"sourceId\":\"b\",\"status\":\"AVAILABLE\"", "\"sourceId\":\"b\",\"status\":\"UNAVAILABLE\",\"errorCode\":\"NO_SNAPSHOT\"")
            .replace("\"coveredSourceIds\":[\"a\",\"b\"]", "\"coveredSourceIds\":[\"a\",\"b\"]")
            .replace("\"complete\":true", "\"complete\":false")
        assertTrue(parseRankingSnapshot(partial, now).isFailure)
    }
}
