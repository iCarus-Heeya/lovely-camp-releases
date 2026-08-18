package com.lovelyreader.ui.video

import java.net.URI

/** The provider player may open a separate HTTPS/HTTP media host, never local app schemes. */
internal fun isAllowedSitePlayerNavigation(destination: String): Boolean {
    val uri = runCatching { URI(destination) }.getOrNull() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)
}

/** A visible player must be a separate, external HTTP(S) frame, never the catalogue page or an app scheme. */
internal fun visibleProviderPlayerUrl(candidate: String?, episodePageUrl: String): String? {
    val value = candidate?.trim().orEmpty()
    if (!isAllowedSitePlayerNavigation(value)) return null
    val player = runCatching { URI(value) }.getOrNull() ?: return null
    val episode = runCatching { URI(episodePageUrl) }.getOrNull() ?: return null
    val isSameOrigin = player.scheme.equals(episode.scheme, true) &&
        player.host.equals(episode.host, true) && player.port == episode.port
    return value.takeUnless { isSameOrigin }
}

internal fun isSitePlayerEntryPage(currentUrl: String, episodePageUrl: String): Boolean {
    val current = runCatching { URI(currentUrl) }.getOrNull() ?: return false
    val episode = runCatching { URI(episodePageUrl) }.getOrNull() ?: return false
    return current.scheme.equals(episode.scheme, true) &&
        current.host.equals(episode.host, true) &&
        current.port == episode.port && current.path == episode.path
}

internal fun shouldRevealConfirmedProviderPlayer(currentUrl: String, confirmedPlayerUrl: String?): Boolean =
    confirmedPlayerUrl != null && currentUrl == confirmedPlayerUrl && isAllowedSitePlayerNavigation(currentUrl)

/** A provider frame can redirect itself before the first page-finished callback. */
internal fun shouldRevealConfirmedProviderFrame(
    confirmedPlayerUrl: String?,
    episodePageUrl: String
): Boolean = visibleProviderPlayerUrl(confirmedPlayerUrl, episodePageUrl) != null

/** A same-origin or script-created player still has a safe, visible container on the episode page. */
internal fun shouldRevealProviderPlayerContainer(hasPlayerContainer: Boolean): Boolean = hasPlayerContainer

/** Provider iframe URLs carry an opaque `url` parameter; ordinary CDN assets do not. */
internal fun isProviderFrameRequest(candidate: String, episodePageUrl: String): Boolean {
    val player = visibleProviderPlayerUrl(candidate, episodePageUrl) ?: return false
    return runCatching { URI(player).query.orEmpty().split('&') }
        .getOrDefault(emptyList())
        .any { it.substringBefore('=').equals("url", ignoreCase = true) }
}

/** The provider frame relies on the site page's browsing context and referrer. */
internal fun shouldNavigateToProviderFrame(): Boolean = false

internal fun isProviderAnnouncementOverlay(visibleText: String): Boolean =
    visibleText.contains("紧急公告") && visibleText.contains("永久地址")

/** Page chrome that belongs to the catalogue site, not to its embedded provider player. */
internal fun providerSiteChromeSelectors(): List<String> = listOf(
    ".header-all",
    ".topone",
    ".download",
    ".channel-hide",
    ".footer",
    ".global_notice_wrapper",
    ".ptitle"
)

/** Keep the catalogue page hidden once it has handed off to a provider player. */
internal fun shouldLoadSitePlayerEntry(
    currentUrl: String?,
    episodePageUrl: String,
    confirmedPlayerUrl: String?
): Boolean = currentUrl != episodePageUrl && confirmedPlayerUrl == null
