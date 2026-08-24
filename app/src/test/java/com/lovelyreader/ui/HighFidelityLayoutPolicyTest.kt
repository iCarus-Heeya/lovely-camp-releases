package com.lovelyreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HighFidelityLayoutPolicyTest {

    @Test
    fun `experience switch follows page specific concept placement`() {
        assertEquals(HighFidelityChromePlacement.BelowHeader, highFidelityChromePlacement(BookPage.Shelf))
        assertEquals(HighFidelityChromePlacement.HeaderTrailing, highFidelityChromePlacement(BookPage.Search))
        assertEquals(HighFidelityChromePlacement.BelowHeader, highFidelityChromePlacement(BookPage.Detail))
        assertEquals(HighFidelityChromePlacement.Hidden, highFidelityChromePlacement(BookPage.Reader))
    }
    @Test
    fun bookshelfHasOneFindBookEntryAndKeepsTheSharedChrome() {
        val layout = highFidelityBookLayout(BookPage.Shelf)

        assertEquals(1, layout.findBookEntryCount)
        assertTrue(layout.showsContinueReadingSection)
        assertTrue(layout.showsSharedAppChrome)
        assertTrue(layout.showsBottomNavigation)
        assertTrue(layout.showsPaperDecoration)
    }

    @Test
    fun searchUsesOneDiscoveryTabRowAndKeepsTheSharedChrome() {
        val layout = highFidelityBookLayout(BookPage.Search)

        assertEquals(listOf("搜索", "首页精选", "随便看看"), layout.discoveryTabs)
        assertTrue(layout.showsSharedAppChrome)
        assertTrue(layout.showsPaperDecoration)
    }

    @Test
    fun detailUsesScrollableSummaryAndOnlyInAppShelfAction() {
        val layout = highFidelityBookLayout(BookPage.Detail)

        assertTrue(layout.scrollableContent)
        assertEquals(listOf("加入书架"), layout.primaryActions)
        assertFalse(layout.primaryActions.contains("打开原站"))
    }

    @Test
    fun bookCoversKeepTheirFullSourceArtworkAcrossFixedCards() {
        assertEquals(BookCoverScalePolicy.Fit, highFidelityBookCoverScalePolicy())
    }

    @Test
    fun bookshelfTitlesReserveTwoCompactLines() {
        assertEquals(2, highFidelityShelfBookTitleMaxLines())
        assertEquals(14, highFidelityShelfBookTitleSizeSp())
    }

    @Test
    fun detailUsesCompactExperienceChromeAboveTheHeroHeader() {
        val chrome = highFidelityDetailChrome()

        assertTrue(chrome.aboveHeader)
        assertEquals(40, chrome.switchWidthDp)
        assertEquals(24, chrome.switchHeightDp)
        assertEquals(24, chrome.horizontalPaddingDp)
    }

    @Test
    fun readerIsFocusedAndHidesSharedChromeAndBottomNavigation() {
        val layout = highFidelityBookLayout(BookPage.Reader)

        assertFalse(layout.showsSharedAppChrome)
        assertFalse(layout.showsBottomNavigation)
        assertTrue(layout.showsPaperDecoration)
        assertEquals(60, layout.readerContentTopInsetDp)
        assertEquals(24, layout.readerContentBottomInsetDp)
        assertEquals(36, layout.readerTextStartPaddingDp)
        assertEquals(36, layout.readerTextEndPaddingDp)
    }

    @Test
    fun settingsKeepsUpdateActionsAndVersionHistoryVisuallySeparated() {
        val layout = highFidelitySettingsLayout()

        assertTrue(layout.showsRoseMark)
        assertTrue(layout.showsUpdateCard)
        assertTrue(layout.historyIsSeparateSection)
    }

    @Test
    fun playerUsesConceptDarkChromeAndDedicatedControlSurface() {
        val layout = highFidelityPlayerLayout()

        assertTrue(layout.darkSurface)
        assertTrue(layout.showsProgressAndFullscreenControls)
        assertTrue(layout.showsSourceAndEpisodeSurface)
        assertTrue(layout.usesCustomControlSurface)
        assertTrue(layout.usesZoomedVideoSurface)
        assertTrue(layout.usesPlainTextBackControl)
        assertTrue(layout.keepsCastActionInline)
        assertTrue(layout.posterPreviewShowsCastUnavailableNotice)
        assertTrue(layout.showsFullscreenLabel)
        assertTrue(layout.showsCastIcon)
        assertTrue(layout.showsInfoIcon)
        assertEquals(490, layout.previewMediaHeightDp)
    }

    @Test
    fun playerFixtureUsesAStaticPosterPreviewWhenNoMediaIsAvailable() {
        assertTrue(highFidelityPlayerFixtureUsesPosterPreview())
    }

    @Test
    fun dramaHomeKeepsConceptSectionOrderAndMovesDebugOutOfMainContent() {
        val layout = highFidelityDramaHomeLayout()

        assertEquals(listOf("标题", "搜索", "继续观看", "下载列表", "找到的剧集"), layout.sectionOrder)
        assertEquals(HighFidelityDebugControlPlacement.HeaderAction, layout.debugControlPlacement)
        assertTrue(layout.usesSeparateContinueHeading)
        assertFalse(layout.showsSubtitle)
    }

    @Test
    fun experienceSwitchUsesOneUnifiedSegmentedSurface() {
        assertTrue(highFidelityUsesUnifiedExperienceSwitch())
    }

    @Test
    fun conceptUsesAStablePhoneGridInsteadOfMaterialDefaultSpacing() {
        val metrics = highFidelityPhoneMetrics()

        assertEquals(16, metrics.pageHorizontalPaddingDp)
        assertEquals(18, metrics.sectionGapDp)
        assertEquals(18, metrics.cardCornerRadiusDp)
        assertEquals(68, metrics.bottomNavigationHeightDp)
        assertEquals(90, metrics.searchResultCoverWidthDp)
        assertEquals(112, metrics.searchResultCoverHeightDp)
        assertEquals(132, metrics.searchResultCardHeightDp)
        assertEquals(148, metrics.detailCoverWidthDp)
        assertEquals(220, metrics.detailCoverHeightDp)
        assertEquals(236, metrics.dramaDetailPosterHeightDp)
        assertEquals(132, metrics.compactExperienceSwitchWidthDp)
        assertEquals(228, metrics.shelfExperienceSwitchWidthDp)
        assertEquals(56, metrics.shelfExperienceSwitchStartPaddingDp)
        assertEquals(210, metrics.dramaHomeExperienceSwitchWidthDp)
        assertEquals(65, metrics.dramaDetailExperienceSwitchWidthDp)
        assertEquals(132, metrics.inlineExperienceSwitchWidthDp)
    }

    @Test
    fun highFidelityPagesUseTheSamePaperShellAndReadableTypeScale() {
        val metrics = highFidelityPhoneMetrics()

        assertTrue(metrics.usesSharedPaperShell)
        assertEquals(24, metrics.displayTitleSizeSp)
        assertEquals(14, metrics.bodyTextSizeSp)
        assertEquals(1.42f, metrics.bodyLineHeightMultiplier, 0.001f)
    }

    @Test
    fun paperDecorationUsesTheQuietConceptContrast() {
        val decoration = highFidelityPaperDecoration()

        assertEquals(0.10f, decoration.branchAlpha, 0.001f)
        assertEquals(0.14f, decoration.blossomAlpha, 0.001f)
        assertEquals(0.22f, decoration.mountainAlpha, 0.001f)
        assertEquals(HighFidelityPaperDecorationStyle.Willow, highFidelityPaperDecorationStyle(HighFidelityPaperSurface.Settings))
        assertEquals(HighFidelityPaperDecorationStyle.Willow, highFidelityPaperDecorationStyle(HighFidelityPaperSurface.Downloads))
        assertEquals(HighFidelityPaperDecorationStyle.Plum, highFidelityPaperDecorationStyle(HighFidelityPaperSurface.BookShelf))
    }

    @Test
    fun conceptRootSurfacesKeepTheConceptBottomNavigation() {
        assertTrue(highFidelityConceptUsesBottomNavigation())
    }

    @Test
    fun updateCardPrefersProvidedCoverArtAndKeepsAFallbackForNoCover() {
        assertTrue(highFidelityUpdateCardUsesCover("fixture://book-jiulong.png"))
        assertFalse(highFidelityUpdateCardUsesCover(null))
        assertFalse(highFidelityUpdateCardUsesCover(""))
    }

    @Test
    fun updatePanelUsesCompactConceptGeometryAndUserFacingDescription() {
        val layout = highFidelitySettingsLayout()

        assertEquals(96, layout.updateCoverWidthDp)
        assertEquals(100, layout.updateCoverHeightDp)
        assertEquals(44, layout.updateActionHeightDp)
        assertEquals(12, layout.updateDescriptionTextSizeSp)
        assertEquals(20, layout.updateDescriptionLineHeightSp)
        assertEquals(180, layout.historyTopSpacingDp)
        assertEquals("应用会在已验证网络下每天自动检查一次；下载与安装始终由你确认", highFidelityUpdateDescription())
    }

    @Test
    fun shelfSortRowMatchesConceptPrimaryAndSecondaryLabels() {
        val layout = highFidelityShelfLayout()

        assertEquals("默认", layout.primarySortLabel)
        assertEquals(listOf("进度", "书名"), layout.secondarySortLabels)
    }

    @Test
    fun continueReadingCardUsesCompactConceptStatusCopy() {
        val layout = highFidelityShelfLayout()

        assertEquals("阅读进度", layout.progressLabel)
        assertEquals("已下载", layout.readyDownloadLabel)
    }

    @Test
    fun searchResultCardUsesCapabilityAndDetailActions() {
        assertEquals(listOf("能力状态", "查看详情"), highFidelitySearchResultActionLabels())
        assertTrue(highFidelitySearchResultActionsInline())
    }

    @Test
    fun dramaFixtureMatchesConceptPlaybackAndDownloadStates() {
        val policy = highFidelityDramaFixturePolicy()

        assertEquals(6, policy.defaultEpisodeNumber)
        assertEquals(listOf(1, 2, 3, 4, 5), policy.detailEpisodeNumbers)
        assertTrue(policy.previewHidesUnavailableMediaMessage)
        assertTrue(policy.keepsCastEntry)
        assertEquals(listOf("等待下载", "正在下载", "已下载完成", "下载没有完成"), policy.downloadStatusLabels)
    }

    @Test
    fun dramaFixtureUsesConceptArtworkForTheDetailAndSearchCards() {
        assertEquals("fixture://drama-detail-cover.png", highFidelityDramaDetailFixturePoster())
        assertEquals(
            listOf(
                "fixture://book-search-nine.png",
                "fixture://book-search-night.png",
                "fixture://book-search-spring.png",
                "fixture://book-search-mirror.png"
            ),
            highFidelityFixtureSearchCoverUrls()
        )
    }

    @Test
    fun visualGateUsesOneApprovedConceptAssetPerPage() {
        assertEquals(
            listOf(
                "concept/bookshelf-home-9x16-1080x1920.png",
                "concept/book-search-9x16-1080x1920.png",
                "concept/book-detail-9x16-1080x1920.png",
                "concept/reader-9x16-1080x1920.png",
                "concept/drama-home-9x16-1080x1920.png",
                "concept/drama-detail-9x16-1080x1920.png",
                "concept/video-player-9x16-1080x1920.png",
                "concept/drama-downloads-9x16-1080x1920.png",
                "concept/settings-update-9x16-1080x1920.png"
            ),
            HighFidelityConceptPage.entries.map(::highFidelityConceptAsset)
        )
    }

    @Test
    fun conceptCropKeepsReaderChromeVisibleBelowTheSystemBar() {
        assertEquals(48, highFidelityConceptCropTopPx(HighFidelityConceptPage.Reader))
        assertEquals(72, highFidelityConceptCropTopPx(HighFidelityConceptPage.Shelf))
        assertEquals(48, highFidelityConceptCropTopPx(HighFidelityConceptPage.Player))
    }

    @Test
    fun shelfFillsTheConceptGridWithExplicitAddSlots() {
        assertEquals(2, highFidelityShelfPlaceholderSlots(bookCount = 1, columns = 3))
        assertEquals(1, highFidelityShelfPlaceholderSlots(bookCount = 2, columns = 3))
        assertEquals(0, highFidelityShelfPlaceholderSlots(bookCount = 3, columns = 3))
    }

    @Test
    fun readerFixtureCanShowTheConceptProgressWithoutChangingPagerState() {
        val fixture = highFidelityReaderFixturePolicy()

        assertEquals("88.7%", fixture.progressLabel)
        assertTrue(fixture.keepInitialContentVisible)
    }
}
