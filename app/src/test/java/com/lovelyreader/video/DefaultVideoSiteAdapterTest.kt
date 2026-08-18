package com.lovelyreader.video

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultVideoSiteAdapterTest {
    private val root = VideoSiteRoot("https://video.example", validatedAtMillis = 1)

    @Test
    fun `search reads title cards from the root exposed GET search form`() = runTest {
        val fetcher = FakePageFetcher(
            mapOf(
                root.url to """
                    <form class="search" action="/find" method="get">
                      <input type="search" name="q">
                    </form>
                """.trimIndent(),
                "https://video.example/find?q=warm+drama" to """
                    <a class="video-title" href="/title/one" data-title="Warm Drama"
                       data-poster="/images/one.jpg" data-summary="A gentle story">Warm Drama</a>
                    <a class="video-title" href="https://outside.example/title/two" data-title="Outside">Outside</a>
                """.trimIndent()
            )
        )

        val titles = DefaultVideoSiteAdapter(fetcher).search(root, "warm drama")

        assertEquals(
            listOf(
                VideoTitle(
                    id = "https://video.example/title/one",
                    name = "Warm Drama",
                    detailUrl = "https://video.example/title/one",
                    posterUrl = "https://video.example/images/one.jpg",
                    summary = "A gentle story"
                )
            ),
            titles
        )
        assertEquals(listOf(root.url, "https://video.example/find?q=warm+drama"), fetcher.requestedUrls)
    }

    @Test
    fun `88ystv search keeps card cover and labelled metadata`() = runTest {
        val siteRoot = VideoSiteRoot("https://www.88ystv.com", validatedAtMillis = 1)
        val fetcher = FakePageFetcher(
            mapOf(
                siteRoot.url to """
                    <form action="/search/" method="post"><input name="wd" type="text"></form>
                """.trimIndent()
            ),
            formResponses = mapOf(
                FormRequest("https://www.88ystv.com/search/", mapOf("wd" to "南部档案馆")) to """
                    <ul><li class="p1">
                      <a href="/guochanju/nanbudanganguan/" title="南部档案馆"><img src="/upload/nanbu.jpg"><p class="name">南部档案馆</p></a>
                      <p>上映：2024</p><p>主演：张三 / 李四</p><p>类型：国产剧 · 悬疑</p><p>更新：更新至 24 集</p>
                    </li></ul>
                """.trimIndent()
            )
        )

        val title = DefaultVideoSiteAdapter(fetcher).search(siteRoot, "南部档案馆").single()

        assertEquals("https://www.88ystv.com/upload/nanbu.jpg", title.posterUrl)
        assertEquals("2024", title.releaseInfo)
        assertEquals("张三 / 李四", title.castInfo)
        assertEquals("国产剧 · 悬疑", title.categoryInfo)
        assertEquals("更新至 24 集", title.updateInfo)
    }

    @Test
    fun `88ystv current search card uses lazy cover and class based metadata`() = runTest {
        val siteRoot = VideoSiteRoot("https://www.88ystv.com", validatedAtMillis = 1)
        val fetcher = FakePageFetcher(
            mapOf(siteRoot.url to """<form action="/search/" method="post"><input name="wd" type="text"></form>"""),
            formResponses = mapOf(
                FormRequest("https://www.88ystv.com/search/", mapOf("wd" to "九门")) to """
                    <ul><li class="p1 m1">
                      <a href="/guochanju/202607/275970.html" title="九门" class="link-hover">
                        <img data-original="https://cdn.aqdstatic.com:966/poseidon/upload/vod/2026-07/178538652627261575.jpg"
                             src="https://p1.bdxiguaimg.com/origin/placeholder" class="lazy">
                        <span class="lzbz"><p class="name">九门</p>
                          <p class="actor">陈伟霆,陈瑶,曾舜晞,王茂蕾</p>
                          <p class="actor">未知</p><p class="actor">2026/中国大陆</p>
                        </span><p class="other"><i>更新至26集</i></p>
                      </a>
                    </li></ul>
                """.trimIndent()
            )
        )

        val title = DefaultVideoSiteAdapter(fetcher).search(siteRoot, "九门").single()

        assertEquals("https://cdn.aqdstatic.com:966/poseidon/upload/vod/2026-07/178538652627261575.jpg", title.posterUrl)
        assertEquals("陈伟霆,陈瑶,曾舜晞,王茂蕾", title.castInfo)
        assertEquals("2026/中国大陆", title.releaseInfo)
        assertEquals("更新至26集", title.updateInfo)
        assertNull(title.categoryInfo)
    }

    @Test
    fun `88ystv search posts wd and reads title cards`() = runTest {
        val siteRoot = VideoSiteRoot("https://www.88ystv.com", validatedAtMillis = 1)
        val fetcher = FakePageFetcher(
            mapOf(
                siteRoot.url to """
                    <form action="/search/" method="post"><input name="wd" type="text"></form>
                """.trimIndent()
            ),
            formResponses = mapOf(
                FormRequest("https://www.88ystv.com/search/", mapOf("wd" to "南部档案馆")) to """
                    <ul><li class="p1"><a href="/guochanju/nanbudanganguan/" title="南部档案馆">
                      <img src="/upload/nanbu.jpg"><p class="name">南部档案馆</p>
                    </a></li></ul>
                """.trimIndent()
            )
        )

        val titles = DefaultVideoSiteAdapter(fetcher).search(siteRoot, "南部档案馆")

        assertEquals(listOf("南部档案馆"), titles.map(VideoTitle::name))
        assertEquals("https://www.88ystv.com/guochanju/nanbudanganguan/", titles.single().detailUrl)
        assertEquals(listOf(FormRequest("https://www.88ystv.com/search/", mapOf("wd" to "南部档案馆"))), fetcher.formRequests)
    }

    @Test
    fun `88ystv detail exposes every source and selected playlist episodes`() = runTest {
        val siteRoot = VideoSiteRoot("https://www.88ystv.com", validatedAtMillis = 1)
        val detailUrl = "https://www.88ystv.com/guochanju/nanbudanganguan/"
        val page = """
            <h1>南部档案馆</h1>
            <div class=playfrom><li id=tab81>线路一</li><li id=tab82>线路二</li></div>
            <div id=stab81 class=playlist><div id=vlink_1><a title=第1集 href=/vod-play-id-81-1.html>第1集</a></div><a href=/vod-play-id-81-2.html>第2集</a></div>
            <div id=stab82 class=playlist><a href=/vod-play-id-82-1.html>第1集</a></div>
        """.trimIndent()
        val fetcher = FakePageFetcher(mapOf(detailUrl to page))
        val adapter = DefaultVideoSiteAdapter(fetcher)

        val detail = adapter.loadDetail(siteRoot, detailUrl)
        val lineOne = detail!!.sources[0]
        val lineTwo = detail.sources[1]
        val firstEpisodes = adapter.loadEpisodes(siteRoot, lineOne)
        val secondEpisodes = adapter.loadEpisodes(siteRoot, lineTwo)

        assertEquals(listOf("线路一", "线路二"), detail.sources.map(VideoSource::label))
        assertEquals(listOf("第1集", "第2集"), firstEpisodes.map(VideoEpisode::label))
        assertEquals(listOf("第1集"), secondEpisodes.map(VideoEpisode::label))
        assertTrue(lineOne.url!!.endsWith("#stab81"))
        assertTrue(lineTwo.url!!.endsWith("#stab82"))
    }

    @Test
    fun `detail groups site exposed sources in document order`() = runTest {
        val detailUrl = "https://video.example/title/one"
        val fetcher = FakePageFetcher(
            mapOf(
                detailUrl to """
                    <h1 data-video-title="Warm Drama">Warm Drama</h1>
                    <a class="video-source" data-source="Cloud One" href="/title/one?source=cloud-one">Cloud One</a>
                    <a class="video-source" data-source="Cloud Two" href="/title/one?source=cloud-two">Cloud Two</a>
                    <a class="video-source" data-source="Outside" href="https://outside.example/source">Outside</a>
                """.trimIndent()
            )
        )

        val detail = DefaultVideoSiteAdapter(fetcher).loadDetail(root, detailUrl)

        assertEquals(
            listOf("Cloud One", "Cloud Two"),
            detail?.sources?.map(VideoSource::label)
        )
        assertEquals(
            listOf(
                "https://video.example/title/one?source=cloud-one",
                "https://video.example/title/one?source=cloud-two"
            ),
            detail?.sources?.map(VideoSource::url)
        )
    }

    @Test
    fun `detail refuses an off-origin page without requesting it`() = runTest {
        val fetcher = FakePageFetcher()

        val detail = DefaultVideoSiteAdapter(fetcher).loadDetail(root, "https://outside.example/title/one")

        assertNull(detail)
        assertTrue(fetcher.requestedUrls.isEmpty())
    }

    @Test
    fun `episodes stay ordered and source switching fetches only the selected source page`() = runTest {
        val cloudOne = VideoSource(
            id = "cloud-one",
            titleId = "https://video.example/title/one",
            label = "Cloud One",
            url = "https://video.example/title/one?source=cloud-one"
        )
        val cloudTwo = cloudOne.copy(
            id = "cloud-two",
            label = "Cloud Two",
            url = "https://video.example/title/one?source=cloud-two"
        )
        val fetcher = FakePageFetcher(
            mapOf(
                cloudOne.url!! to """
                    <a class="video-episode" href="/play/2" data-episode="Episode 2">Episode 2</a>
                    <a class="video-episode" href="/play/10" data-episode="Episode 10">Episode 10</a>
                """.trimIndent(),
                cloudTwo.url!! to """
                    <a class="video-episode" href="/play/special" data-episode="Special">Special</a>
                """.trimIndent()
            )
        )
        val adapter = DefaultVideoSiteAdapter(fetcher)

        val firstSourceEpisodes = adapter.loadEpisodes(root, cloudOne)
        val secondSourceEpisodes = adapter.loadEpisodes(root, cloudTwo)

        assertEquals(listOf("Episode 2", "Episode 10"), firstSourceEpisodes.map(VideoEpisode::label))
        assertEquals(listOf(0, 1), firstSourceEpisodes.map(VideoEpisode::position))
        assertEquals(listOf("Special"), secondSourceEpisodes.map(VideoEpisode::label))
        assertEquals(
            listOf(cloudOne.url, cloudTwo.url),
            fetcher.requestedUrls
        )
    }

    @Test
    fun `media extraction keeps HTTPS playback but exposes direct URL only for MP4`() = runTest {
        val episode = VideoEpisode(
            id = "episode-1",
            sourceId = "cloud-one",
            label = "Episode 1",
            url = "https://video.example/play/1",
            position = 0
        )
        val fetcher = FakePageFetcher(
            mapOf(
                episode.url to "<video src=\"https://cdn.example/stream/master.m3u8\"></video>"
            )
        )

        val media = DefaultVideoSiteAdapter(fetcher).loadMedia(root, episode)

        assertEquals("https://cdn.example/stream/master.m3u8", media?.playbackUrl)
        assertNull(media?.directMp4Url)
    }

    @Test
    fun `media extraction accepts an explicitly exposed public stream in player configuration`() = runTest {
        val episode = VideoEpisode(
            id = "episode-configured",
            sourceId = "cloud-one",
            label = "Episode configured",
            url = "https://video.example/play/configured",
            position = 0
        )
        val fetcher = FakePageFetcher(
            mapOf(
                episode.url to """
                    <script>
                      window.player = { url: "https://cdn.example/streams/episode-1/master.m3u8?token=public" };
                    </script>
                """.trimIndent()
            )
        )

        val media = DefaultVideoSiteAdapter(fetcher).loadMedia(root, episode)

        assertEquals("https://cdn.example/streams/episode-1/master.m3u8?token=public", media?.playbackUrl)
        assertNull(media?.directMp4Url)
    }

    @Test
    fun `episode with a site player shell uses only its site player`() = runTest {
        val episode = VideoEpisode(
            id = "episode-embedded",
            sourceId = "cloud-one",
            label = "Episode embedded",
            url = "https://video.example/vod-play-123.html",
            position = 0
        )
        val fetcher = FakePageFetcher(
            mapOf(
                episode.url to """
                    <div id="player" class="player-container"></div>
                    <script>var mac_url = 'obfuscated-site-player-value';</script>
                """.trimIndent()
            )
        )

        val media = DefaultVideoSiteAdapter(fetcher).loadMedia(root, episode)

        assertEquals(VideoPlaybackMode.SITE_PLAYER, media?.playbackMode)
        assertEquals(episode.url, media?.playbackUrl)
    }

    @Test
    fun `direct MP4 requires HTTPS and mp4 path`() {
        val adapter = DefaultVideoSiteAdapter(FakePageFetcher())

        assertTrue(adapter.isDirectMp4("https://cdn.example/video-01.mp4"))
        assertTrue(adapter.isDirectMp4("https://cdn.example/video-01.MP4?token=abc"))
        assertFalse(adapter.isDirectMp4("https://cdn.example/master.m3u8"))
        assertFalse(adapter.isDirectMp4("https://cdn.example/manifest.mpd"))
        assertFalse(adapter.isDirectMp4("blob:https://video.example/opaque"))
        assertFalse(adapter.isDirectMp4("http://cdn.example/video-01.mp4"))
        assertFalse(adapter.isDirectMp4("https://cdn.example/video-01.mp4.m3u8"))
    }

    private class FakePageFetcher(
        private val responses: Map<String, String> = emptyMap(),
        private val formResponses: Map<FormRequest, String> = emptyMap()
    ) : VideoPageFetcher {
        val requestedUrls = mutableListOf<String>()
        val formRequests = mutableListOf<FormRequest>()

        override suspend fun get(url: String): String {
            requestedUrls += url
            return responses[url] ?: error("No response for $url")
        }

        override suspend fun postForm(url: String, fields: Map<String, String>): String {
            val request = FormRequest(url, fields)
            formRequests += request
            return formResponses[request] ?: error("No form response for $request")
        }
    }

    private data class FormRequest(val url: String, val fields: Map<String, String>)
}
