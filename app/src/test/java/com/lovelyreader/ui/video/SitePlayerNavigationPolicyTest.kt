package com.lovelyreader.ui.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SitePlayerNavigationPolicyTest {
    @Test
    fun `site player user agent follows browser shape instead of Android WebView marker`() {
        val webViewAgent = "Mozilla/5.0 (Linux; Android 12; Pixel 6 Build/SQ3A) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.0.0 Mobile Safari/537.36 wv"

        val browserAgent = browserCompatibleSitePlayerUserAgent(webViewAgent)

        assertFalse(browserAgent.contains("; wv", ignoreCase = true))
        assertFalse(browserAgent.contains("Version/4.0", ignoreCase = true))
        assertTrue(browserAgent.contains("Chrome/120.0.0.0", ignoreCase = true))
        assertTrue(browserAgent.contains("Mobile", ignoreCase = true))
    }

    @Test
    fun `allows web player navigation but rejects local application schemes`() {
        assertTrue(isAllowedSitePlayerNavigation("https://vip.example/player?episode=1"))
        assertTrue(isAllowedSitePlayerNavigation("http://legacy-media.example/player"))
        assertFalse(isAllowedSitePlayerNavigation("file:///sdcard/movie.mp4"))
        assertFalse(isAllowedSitePlayerNavigation("content://media/external/video/1"))
        assertFalse(isAllowedSitePlayerNavigation("intent://open/#Intent;scheme=https;end"))
        assertFalse(isAllowedSitePlayerNavigation("javascript:alert(1)"))
    }

    @Test
    fun `accepts only an external HTTP player frame for visible playback`() {
        assertEquals(
            "https://vip.example/player?episode=1",
            visibleProviderPlayerUrl(
                "https://vip.example/player?episode=1",
                "https://www.88ystv.com/vod-play-id-1.html"
            )
        )
        assertNull(visibleProviderPlayerUrl("https://www.88ystv.com/notice.html", "https://www.88ystv.com/vod-play-id-1.html"))
        assertNull(visibleProviderPlayerUrl("javascript:alert(1)", "https://www.88ystv.com/vod-play-id-1.html"))
    }

    @Test
    fun `treats mobile and www catalogue hosts as the same site`() {
        assertNull(
            visibleProviderPlayerUrl(
                "https://m.88ystv.com/vod-play-id-1.html",
                "https://www.88ystv.com/vod-play-id-1.html"
            )
        )
        assertEquals(
            "https://vip.example/player?episode=1",
            visibleProviderPlayerUrl(
                "https://vip.example/player?episode=1",
                "https://m.88ystv.com/vod-play-id-1.html"
            )
        )
    }

    @Test
    fun `only the episode entry page may look for the provider frame`() {
        val episode = "https://www.88ystv.com/vod-play-id-1.html"

        assertTrue(isSitePlayerEntryPage(episode, episode))
        assertFalse(isSitePlayerEntryPage("https://vip.example/player?episode=1", episode))
        assertFalse(isSitePlayerEntryPage("https://www.88ystv.com/notice.html", episode))
    }

    @Test
    fun `reveals only the provider frame explicitly confirmed by the entry page`() {
        val confirmed = "https://vip.example/player?episode=1"

        assertTrue(shouldRevealConfirmedProviderPlayer(confirmed, confirmed))
        assertFalse(shouldRevealConfirmedProviderPlayer("https://www.88ystv.com/notice.html", confirmed))
        assertFalse(shouldRevealConfirmedProviderPlayer("https://vip.example/other", confirmed))
        assertFalse(shouldRevealConfirmedProviderPlayer(confirmed, null))
    }

    @Test
    fun `never reloads the catalogue entry after a provider frame is confirmed`() {
        val entry = "https://www.88ystv.com/vod-play-id-1.html"
        val provider = "https://vip.example/player?episode=1"

        assertFalse(shouldLoadSitePlayerEntry(entry, entry, null))
        assertTrue(shouldLoadSitePlayerEntry("about:blank", entry, null))
        assertFalse(shouldLoadSitePlayerEntry(provider, entry, provider))
    }

    @Test
    fun `shows a confirmed external provider frame before it redirects for playback`() {
        assertTrue(
            shouldRevealConfirmedProviderFrame(
                "https://vip.example/player?episode=1",
                "https://www.88ystv.com/vod-play-id-1.html"
            )
        )
        assertFalse(
            shouldRevealConfirmedProviderFrame(
                "https://www.88ystv.com/notice.html",
                "https://www.88ystv.com/vod-play-id-1.html"
            )
        )
    }

    @Test
    fun `allows an external provider frame that is added after the page has loaded`() {
        val player = "https://zj.jsjinfu.com:8443?url=opaque"
        val episode = "https://www.88ystv.com/vod-play-id-275962-src-1-num-1.html"

        assertEquals(player, visibleProviderPlayerUrl(player, episode))
    }

    @Test
    fun `recognizes an external provider frame request but not an external image`() {
        val episode = "https://www.88ystv.com/vod-play-id-275962-src-1-num-1.html"

        assertTrue(isProviderFrameRequest("https://zj.jsjinfu.com:8443?url=opaque", episode))
        assertFalse(isProviderFrameRequest("https://cdn.example/poster.jpg?width=640", episode))
    }

    @Test
    fun `recognizes same-origin provider subresources after the frame is confirmed`() {
        val episode = "https://www.88ystv.com/vod-play-id-275962-src-1-num-1.html"
        val provider = "https://zj.jsjinfu.com:8443?url=opaque"

        assertTrue(isProviderResourceRequest("https://zj.jsjinfu.com:8443/player.js", episode, provider))
        assertTrue(isProviderResourceRequest("https://zj.jsjinfu.com:8443/media/segment.ts", episode, provider))
        assertFalse(isProviderResourceRequest("https://cdn.example/player.js", episode, provider))
        assertFalse(isProviderResourceRequest("https://zj.jsjinfu.com/player.js", episode, provider))
    }

    @Test
    fun `does not treat optional provider resources as fatal player failures`() {
        val episode = "https://www.88ystv.com/vod-play-id-275962-src-1-num-1.html"
        val provider = "https://zj.jsjinfu.com:8443?url=opaque"

        assertTrue(isFatalSitePlayerRequest(provider, episode))
        assertFalse(isFatalSitePlayerRequest("https://zj.jsjinfu.com:8443/player.js", episode))
        assertFalse(isFatalSitePlayerRequest("https://zj.jsjinfu.com:8443/media/segment.ts", episode))
    }

    @Test
    fun `keeps the confirmed provider frame inside the catalogue page`() {
        assertFalse(shouldNavigateToProviderFrame())
    }

    @Test
    fun `suppresses only the provider emergency announcement overlay`() {
        assertTrue(isProviderAnnouncementOverlay("紧急公告\n永久地址：88ys.cn"))
        assertFalse(isProviderAnnouncementOverlay("正在播放：第1期"))
    }
    @Test
    fun `player-only mode hides site chrome but never the provider player`() {
        val selectors = providerSiteChromeSelectors()

        assertTrue(selectors.contains(".header-all"))
        assertTrue(selectors.contains(".download"))
        assertTrue(selectors.contains(".ptitle"))
        assertFalse(selectors.contains(".MacPlayer"))
    }

    @Test
    fun `switching an episode reloads the site-player entry page`() {
        assertTrue(
            shouldLoadSitePlayerEntry(
                currentUrl = "https://www.88ystv.com/vod-play-id-1.html",
                episodePageUrl = "https://www.88ystv.com/vod-play-id-2.html",
                confirmedPlayerUrl = null
            )
        )
    }

    @Test
    fun `reveals the isolated player container even when no external iframe was reported`() {
        assertTrue(shouldRevealProviderPlayerContainer(true))
        assertFalse(shouldRevealProviderPlayerContainer(false))
    }
}
