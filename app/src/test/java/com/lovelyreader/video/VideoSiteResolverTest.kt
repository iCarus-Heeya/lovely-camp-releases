package com.lovelyreader.video

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoSiteResolverTest {
    @Test
    fun `resolver uses first external HTTPS link in document order`() = runTest {
        val fetcher = FakePageFetcher(
            responses = mapOf(
                ENTRY_URL to """
                    <html><body>
                      <a href="https://rentry.la/another">entry mirror</a>
                      <a href="https://first.example/path">first site</a>
                      <a href="https://second.example">second site</a>
                    </body></html>
                """.trimIndent(),
                "https://first.example/path" to cataloguePage()
            )
        )

        val resolution = VideoSiteResolver(fetcher, InMemoryVideoRootStore()).resolve(ENTRY_URL)

        assertEquals("https://first.example/path", resolution.root?.url)
        assertEquals(VideoRootResolutionStatus.RESOLVED, resolution.status)
    }

    @Test
    fun `resolver rejects rentry self and HTTP links before selecting HTTPS candidate`() = runTest {
        val fetcher = FakePageFetcher(
            responses = mapOf(
                ENTRY_URL to """
                    <a href="https://www.rentry.la/not-allowed">rentry</a>
                    <a href="https://rentry.la/88ys/next">self</a>
                    <a href="http://insecure.example">insecure</a>
                    <a href="https://video.example">candidate</a>
                """.trimIndent(),
                "https://video.example" to cataloguePage()
            )
        )

        val resolution = VideoSiteResolver(fetcher, InMemoryVideoRootStore()).resolve(ENTRY_URL)

        assertEquals("https://video.example", resolution.root?.url)
        assertEquals(listOf(ENTRY_URL, "https://video.example"), fetcher.requestedUrls)
    }

    @Test
    fun `resolver only saves a candidate after its health probe succeeds`() = runTest {
        val store = InMemoryVideoRootStore()
        val fetcher = FakePageFetcher(
            responses = mapOf(
                ENTRY_URL to "<a href=\"https://video.example\">candidate</a>",
                "https://video.example" to ""
            )
        )

        val resolution = VideoSiteResolver(fetcher, store).resolve(ENTRY_URL)

        assertNull(resolution.root)
        assertEquals(VideoRootResolutionStatus.UNAVAILABLE, resolution.status)
        assertNull(store.load())
    }

    @Test
    fun `resolver uses cached root when refresh fails`() = runTest {
        val cached = VideoSiteRoot("https://previous.example", validatedAtMillis = 10)
        val store = InMemoryVideoRootStore(cached)
        val fetcher = FakePageFetcher(failures = setOf(ENTRY_URL))

        val resolution = VideoSiteResolver(fetcher, store).resolve(ENTRY_URL)

        assertEquals(cached, resolution.root)
        assertEquals(VideoRootResolutionStatus.USING_CACHED_ROOT, resolution.status)
    }

    @Test
    fun `resolver ignores anchors in comments script and template blocks`() = runTest {
        val fetcher = FakePageFetcher(
            responses = mapOf(
                ENTRY_URL to """
                    <!-- <a href="https://comment.example">comment</a> -->
                    <script>const html = '<a href="https://script.example">script</a>';</script>
                    <template><a href="https://template.example">template</a></template>
                    <a href="https://visible.example">visible</a>
                """.trimIndent(),
                "https://visible.example" to cataloguePage()
            )
        )

        val resolution = VideoSiteResolver(fetcher, InMemoryVideoRootStore()).resolve(ENTRY_URL)

        assertEquals("https://visible.example", resolution.root?.url)
    }

    @Test
    fun `resolver ignores blank and invisible anchor content`() = runTest {
        val fetcher = FakePageFetcher(
            responses = mapOf(
                ENTRY_URL to """
                    <a href="https://blank.example">   </a>
                    <a href="https://comment-content.example"><!-- hidden text --></a>
                    <a href="https://display-none.example" style="display: none">hidden</a>
                    <a href="https://visibility-hidden.example"><span style="visibility:hidden">hidden</span></a>
                    <a href="https://aria-hidden.example" aria-hidden="true">hidden</a>
                    <a href="https://visible.example">visible</a>
                """.trimIndent(),
                "https://visible.example" to cataloguePage()
            )
        )

        val resolution = VideoSiteResolver(fetcher, InMemoryVideoRootStore()).resolve(ENTRY_URL)

        assertEquals("https://visible.example", resolution.root?.url)
    }

    @Test
    fun `resolver ignores anchors inside hidden ancestors`() = runTest {
        val fetcher = FakePageFetcher(
            responses = mapOf(
                ENTRY_URL to """
                    <section hidden><a href="https://hidden.example">hidden</a></section>
                    <div style="display:none"><a href="https://also-hidden.example">hidden</a></div>
                    <a href="https://visible.example">visible</a>
                """.trimIndent(),
                "https://visible.example" to cataloguePage()
            )
        )

        val resolution = VideoSiteResolver(fetcher, InMemoryVideoRootStore()).resolve(ENTRY_URL)

        assertEquals("https://visible.example", resolution.root?.url)
    }

    @Test
    fun `resolver selects first visible image-only anchor before a later text link`() = runTest {
        val fetcher = FakePageFetcher(
            responses = mapOf(
                ENTRY_URL to """
                    <a href="https://image-first.example"><img src="https://cdn.example/cover.jpg" alt="cover"></a>
                    <a href="https://text-second.example">text link</a>
                """.trimIndent(),
                "https://image-first.example" to cataloguePage()
            )
        )

        val resolution = VideoSiteResolver(fetcher, InMemoryVideoRootStore()).resolve(ENTRY_URL)

        assertEquals("https://image-first.example", resolution.root?.url)
        assertEquals(listOf(ENTRY_URL, "https://image-first.example"), fetcher.requestedUrls)
    }

    @Test
    fun `resolver falls back when candidate health probe fails`() = runTest {
        val cached = VideoSiteRoot("https://previous.example", validatedAtMillis = 10)
        val store = InMemoryVideoRootStore(cached)
        val fetcher = FakePageFetcher(
            responses = mapOf(
                ENTRY_URL to "<a href=\"https://candidate.example\">candidate</a>",
                "https://candidate.example" to ""
            )
        )

        val resolution = VideoSiteResolver(fetcher, store).resolve(ENTRY_URL)

        assertEquals(cached, resolution.root)
        assertEquals(VideoRootResolutionStatus.USING_CACHED_ROOT, resolution.status)
        assertEquals(cached, store.load())
    }

    @Test
    fun `resolver does not probe unrelated later links when the first provider is unreachable`() = runTest {
        val fetcher = FakePageFetcher(
            responses = mapOf(
                ENTRY_URL to """
                    <a href="https://first-provider.example">first provider</a>
                    <a href="https://www.alidns.com/knowledge">DNS instructions</a>
                    <a href="https://dudns.baidu.com/index.html">DNS instructions</a>
                """.trimIndent(),
            ),
            failures = setOf("https://first-provider.example")
        )

        val resolution = VideoSiteResolver(fetcher, InMemoryVideoRootStore()).resolve(ENTRY_URL)

        assertNull(resolution.root)
        assertEquals(VideoRootResolutionStatus.UNAVAILABLE, resolution.status)
        assertEquals(
            listOf(ENTRY_URL, "https://first-provider.example"),
            fetcher.requestedUrls
        )
    }

    @Test
    fun `resolver rejects a reachable app download page when it has no catalogue search form`() = runTest {
        val fetcher = FakePageFetcher(
            responses = mapOf(
                ENTRY_URL to """
                    <a href="https://offline-provider.example">latest provider</a>
                    <a href="https://www.88ys.app">app download</a>
                """.trimIndent(),
                "https://www.88ys.app" to "<html><h1>APP 下载</h1><a href=\"/download\">立即下载</a></html>"
            ),
            failures = setOf("https://offline-provider.example")
        )

        val resolution = VideoSiteResolver(fetcher, InMemoryVideoRootStore()).resolve(ENTRY_URL)

        assertNull(resolution.root)
        assertEquals(VideoRootResolutionStatus.UNAVAILABLE, resolution.status)
    }

    @Test
    fun `resolver accepts a reachable catalogue page with an ordinary search form`() = runTest {
        val fetcher = FakePageFetcher(
            responses = mapOf(
                ENTRY_URL to "<a href=\"https://catalogue.example\">catalogue</a>",
                "https://catalogue.example" to "<form action=\"/search/\" method=\"post\"><input name=\"wd\" type=\"search\"></form>"
            )
        )

        val resolution = VideoSiteResolver(fetcher, InMemoryVideoRootStore()).resolve(ENTRY_URL)

        assertEquals("https://catalogue.example", resolution.root?.url)
    }

    @Test
    fun `resolver replaces an unsafe HTTP cached root with the safe bootstrap when refresh fails`() = runTest {
        val store = InMemoryVideoRootStore(VideoSiteRoot("http://previous.example", validatedAtMillis = 10))
        val resolution = VideoSiteResolver(
            FakePageFetcher(failures = setOf(ENTRY_URL)),
            store
        ).resolve(ENTRY_URL)

        assertEquals("https://www.88ystv.com", resolution.root?.url)
        assertEquals(VideoRootResolutionStatus.USING_BOOTSTRAP_ROOT, resolution.status)
    }

    @Test
    fun `resolver replaces an unsafe rentry cached root with the safe bootstrap when refresh fails`() = runTest {
        val store = InMemoryVideoRootStore(VideoSiteRoot("https://rentry.la/previous", validatedAtMillis = 10))
        val resolution = VideoSiteResolver(
            FakePageFetcher(failures = setOf(ENTRY_URL)),
            store
        ).resolve(ENTRY_URL)

        assertEquals("https://www.88ystv.com", resolution.root?.url)
        assertEquals(VideoRootResolutionStatus.USING_BOOTSTRAP_ROOT, resolution.status)
    }

    @Test
    fun `first install uses the bundled safe root when the mutable entry cannot be reached`() = runTest {
        val store = InMemoryVideoRootStore()
        val resolution = VideoSiteResolver(
            FakePageFetcher(failures = setOf(ENTRY_URL)),
            store
        ).resolve(ENTRY_URL)

        assertEquals("https://www.88ystv.com", resolution.root?.url)
        assertEquals(VideoRootResolutionStatus.USING_BOOTSTRAP_ROOT, resolution.status)
        assertNull(store.load())
    }

    private class FakePageFetcher(
        private val responses: Map<String, String> = emptyMap(),
        private val failures: Set<String> = emptySet()
    ) : VideoPageFetcher {
        val requestedUrls = mutableListOf<String>()

        override suspend fun get(url: String): String {
            requestedUrls += url
            if (url in failures) error("Network unavailable for $url")
            return responses[url] ?: error("No response for $url")
        }
    }

    private class InMemoryVideoRootStore(initial: VideoSiteRoot? = null) : VideoRootStore {
        private var root = initial

        override fun load(): VideoSiteRoot? = root

        override fun save(root: VideoSiteRoot) {
            this.root = root
        }
    }

    private companion object {
        const val ENTRY_URL = "https://rentry.la/88ys"
        fun cataloguePage(): String = "<form action=\"/search/\" method=\"post\"><input name=\"wd\" type=\"search\"></form>"
    }
}
