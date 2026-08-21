package com.lovelyreader.source

import com.lovelyreader.domain.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class DiscoveryCatalogTest {
    @Test
    fun romanceUsesVerifiedSecondLevelCategories() {
        assertEquals(
            listOf("现代言情", "古代言情", "穿越架空", "宫闱情仇", "浪漫言情", "菁菁校园", "爱在职场", "耽美纯爱"),
            DiscoveryCatalog.romanceCategories
        )
        assertTrue("灵异神怪" in DiscoveryCatalog.primaryCategories)
        assertFalse("灵异神怪" in DiscoveryCatalog.romanceCategories)
    }

    @Test
    fun rotationDoesNotRepeatDisplayedBooksUntilExplicitReset() {
        val rotation = DiscoveryRotation(batchSize = 2)
        val firstPool = listOf(result("甲"), result("乙"), result("丙"))

        val first = rotation.select("现代言情", firstPool, seed = 1)
        val second = rotation.select("现代言情", firstPool, seed = 2)
        val exhausted = rotation.select("现代言情", firstPool, seed = 3)

        assertEquals(2, first.size)
        assertEquals(1, second.size)
        assertTrue(first.map { it.title }.toSet().intersect(second.map { it.title }.toSet()).isEmpty())
        assertTrue(exhausted.isEmpty())

        rotation.reset("现代言情")
        assertTrue(rotation.select("现代言情", firstPool, seed = 4).isNotEmpty())
    }

    @Test
    fun pageCursorRotatesPerCategoryAndSource() {
        val rotation = DiscoveryRotation(batchSize = 12)

        assertEquals(1, rotation.pageFor("言情", "qinkan"))
        rotation.advancePage("言情", "qinkan")
        rotation.advancePage("言情", "qinkan")

        assertEquals(3, rotation.pageFor("言情", "qinkan"))
        assertEquals(1, rotation.pageFor("言情", "qisuwang"))
        assertEquals(1, rotation.pageFor("灵异", "qinkan"))

        rotation.reset("言情")
        assertEquals(1, rotation.pageFor("言情", "qinkan"))
        assertEquals(1, rotation.pageFor("言情", "qisuwang"))
    }

    @Test
    fun rotationTreatsTheSameTitleFromDifferentSourcesAsAlreadyDisplayed() {
        val rotation = DiscoveryRotation(batchSize = 1)

        assertEquals(listOf("同一本书"), rotation.select("言情", listOf(result("同一本书", "甲作者")), 1).map { it.title })
        assertTrue(rotation.select("言情", listOf(result("同一本书", "")), 2).isEmpty())
    }

    @Test
    fun coordinatorKeepsPartialSuccessAndDoesNotAdvanceFailedSource() = runBlocking {
        val rotation = DiscoveryRotation(batchSize = 12)
        val coordinator = DiscoveryCoordinator(rotation)
        val outcome = coordinator.load(
            category = "言情",
            sources = listOf(
                DiscoveryEndpoint("ok") { _, _ -> CategoryBrowseResult.Success(listOf(result("新书")), hasMore = true) },
                DiscoveryEndpoint("failed") { _, _ -> CategoryBrowseResult.Failure("timeout") }
            ),
            seenTitles = emptySet(),
            seed = 1
        )

        assertEquals(DiscoveryLoadStatus.PARTIAL_SUCCESS, outcome.status)
        assertEquals(listOf("新书"), outcome.items.map { it.title })
        assertEquals(2, rotation.pageFor("言情", "ok"))
        assertEquals(1, rotation.pageFor("言情", "failed"))
    }

    @Test
    fun coordinatorDoesNotResetHistoryOrCursorWhenAllSourcesFail() = runBlocking {
        val rotation = DiscoveryRotation(batchSize = 1)
        rotation.select("言情", listOf(result("旧书")), seed = 1)
        rotation.advancePage("言情", "failed")
        val coordinator = DiscoveryCoordinator(rotation)

        val outcome = coordinator.load(
            "言情",
            listOf(DiscoveryEndpoint("failed") { _, _ -> CategoryBrowseResult.Failure("timeout") }),
            emptySet(),
            2
        )

        assertEquals(DiscoveryLoadStatus.FAILURE, outcome.status)
        assertEquals(2, rotation.pageFor("言情", "failed"))
        assertTrue(rotation.select("言情", listOf(result("旧书")), seed = 3).isEmpty())
    }

    @Test
    fun coordinatorReportsExhaustedWithoutAutomaticallyResetting() = runBlocking {
        val rotation = DiscoveryRotation(batchSize = 1)
        val coordinator = DiscoveryCoordinator(rotation)
        var calls = 0
        val endpoint = DiscoveryEndpoint("end") { _, _ ->
            calls++
            CategoryBrowseResult.Success(listOf(result("末页书")), hasMore = false)
        }

        assertEquals(DiscoveryLoadStatus.SUCCESS, coordinator.load("灵异", listOf(endpoint), emptySet(), 1).status)
        val exhausted = coordinator.load("灵异", listOf(endpoint), emptySet(), 2)

        assertEquals(DiscoveryLoadStatus.EXHAUSTED, exhausted.status)
        assertEquals(1, calls)
        assertEquals(1, rotation.pageFor("灵异", "end"))
        assertTrue(rotation.select("灵异", listOf(result("末页书")), 3).isEmpty())
    }

    @Test
    fun coordinatorRunsSourcesConcurrently(): Unit = runBlocking {
        val coordinator = DiscoveryCoordinator(DiscoveryRotation())
        val started = mutableSetOf<String>()
        val sources = listOf("a", "b").map { id ->
            DiscoveryEndpoint(id) { _, _ ->
                synchronized(started) { started += id }
                delay(50)
                assertEquals(setOf("a", "b"), synchronized(started) { started.toSet() })
                CategoryBrowseResult.Success(emptyList(), hasMore = false)
            }
        }

        coordinator.load("现代都市", sources, emptySet(), 1)
    }

    @Test
    fun requestGateRejectsStaleCompletion() {
        val gate = DiscoveryRequestGate()
        val old = gate.begin()
        val current = gate.begin()

        assertFalse(gate.isCurrent(old))
        assertTrue(gate.isCurrent(current))
    }

    @Test
    fun staleCoordinatorResultDoesNotAdvanceCursorOrRecordHistory() = runBlocking {
        val rotation = DiscoveryRotation(batchSize = 1)
        val coordinator = DiscoveryCoordinator(rotation)

        val outcome = coordinator.load(
            category = "言情",
            sources = listOf(
                DiscoveryEndpoint("slow") { _, _ ->
                    CategoryBrowseResult.Success(listOf(result("不应提交")), hasMore = true)
                }
            ),
            seenTitles = emptySet(),
            seed = 1,
            isCurrent = { false }
        )

        assertEquals(DiscoveryLoadStatus.STALE, outcome.status)
        assertEquals(1, rotation.pageFor("言情", "slow"))
        assertEquals(listOf("不应提交"), rotation.select("言情", listOf(result("不应提交")), 2).map { it.title })
    }

    @Test
    fun coordinatorNeverReportsExhaustedWhenAnySourcePartiallyFails() = runBlocking {
        val outcome = DiscoveryCoordinator(DiscoveryRotation()).load(
            "言情",
            listOf(
                DiscoveryEndpoint("partial") { _, _ ->
                    CategoryBrowseResult.Success(emptyList(), hasMore = false, partialFailure = true)
                },
                DiscoveryEndpoint("failed") { _, _ -> CategoryBrowseResult.Failure("timeout") }
            ),
            emptySet(),
            1
        )

        assertEquals(DiscoveryLoadStatus.PARTIAL_SUCCESS, outcome.status)
    }

    @Test
    fun rotationBuffersEveryUnselectedCandidateBeforeFetchingAnotherPage() {
        val rotation = DiscoveryRotation(batchSize = 2)
        val first = rotation.select("言情", listOf(result("甲"), result("乙"), result("丙")), 1)
        val buffered = rotation.select("言情", emptyList(), 2)

        assertEquals(2, first.size)
        assertEquals(1, buffered.size)
        assertEquals(setOf("甲", "乙", "丙"), (first + buffered).map { it.title }.toSet())
    }

    @Test
    fun coordinatorConsumesPendingBeforeCallingSourceAgain() = runBlocking {
        val rotation = DiscoveryRotation(batchSize = 2)
        val coordinator = DiscoveryCoordinator(rotation)
        var calls = 0
        val endpoint = DiscoveryEndpoint("source") { _, _ ->
            calls++
            CategoryBrowseResult.Success(listOf(result("甲"), result("乙"), result("丙")), hasMore = true)
        }

        assertEquals(2, coordinator.load("言情", listOf(endpoint), emptySet(), 1).items.size)
        assertEquals(1, coordinator.load("言情", listOf(endpoint), emptySet(), 2).items.size)
        assertEquals(1, calls)
        assertEquals(2, rotation.pageFor("言情", "source"))
    }

    @Test
    fun sameTitleDifferentNonEmptyAuthorsRemainDistinctButMissingAuthorMergesCautiously() {
        val a = result("同名书", "甲作者")
        val b = result("同名书", "乙作者")
        val missing = result("同名书", "")

        assertEquals(2, SearchResultMerger.merge(listOf(a, b)).size)
        assertEquals(1, SearchResultMerger.merge(listOf(a, missing)).size)
        assertEquals(3, DiscoveryRotation(batchSize = 3).select("言情", listOf(a, b, missing), 1).size)
    }

    @Test
    fun safeBrowseTurnsThrownProviderErrorIntoFailure() = runBlocking {
        val result = safeCategoryBrowse(timeoutMillis = 100) { error("provider broke") }

        assertTrue(result is CategoryBrowseResult.Failure)
    }

    @Test
    fun stalePendingFastPathDoesNotConsumeOrMutateBufferedCandidates() = runBlocking {
        val rotation = DiscoveryRotation(batchSize = 1)
        rotation.select("言情", listOf(result("第一本"), result("第二本")), seed = 1)
        val coordinator = DiscoveryCoordinator(rotation)

        val stale = coordinator.load("言情", emptyList(), emptySet(), seed = 2) { false }
        val current = coordinator.load("言情", emptyList(), emptySet(), seed = 2) { true }

        assertEquals(DiscoveryLoadStatus.STALE, stale.status)
        assertEquals(1, current.items.size)
    }

    @Test
    fun pendingFastPathRechecksGateImmediatelyBeforeMutation() = runBlocking {
        val rotation = DiscoveryRotation(batchSize = 1)
        rotation.select("言情", listOf(result("第一本"), result("第二本")), seed = 1)
        val coordinator = DiscoveryCoordinator(rotation)
        var gateChecks = 0

        val stale = coordinator.load("言情", emptyList(), emptySet(), seed = 2) {
            gateChecks++
            gateChecks == 1
        }
        val current = coordinator.load("言情", emptyList(), emptySet(), seed = 2) { true }

        assertEquals(DiscoveryLoadStatus.STALE, stale.status)
        assertEquals(1, current.items.size)
    }

    @Test
    fun pendingFastPathRechecksCurrentSeenTitlesBeforeReturningNextBatch() = runBlocking {
        val rotation = DiscoveryRotation(batchSize = 1)
        val coordinator = DiscoveryCoordinator(rotation)
        val first = coordinator.load(
            category = "言情",
            sources = listOf(
                DiscoveryEndpoint("source") { _, _ ->
                    CategoryBrowseResult.Success(
                        listOf(result("第一本"), result("第二本")),
                        hasMore = false
                    )
                }
            ),
            seenTitles = emptySet(),
            seed = 1
        )
        val pendingTitle = setOf("第一本", "第二本") - first.items.map { it.title }.toSet()

        val next = coordinator.load(
            category = "言情",
            sources = emptyList(),
            seenTitles = pendingTitle,
            seed = 2
        )

        assertTrue(pendingTitle.isNotEmpty())
        assertTrue(next.items.isEmpty())
    }

    @Test
    fun authorAwareSeenBooksDoNotLetLegacyTitleEraseAnotherAuthor() = runBlocking {
        val coordinator = DiscoveryCoordinator(DiscoveryRotation(batchSize = 12))
        val seenAuthor = NormalizedBookIdentity(normalizedTitleKey("同名书"), normalizedAuthorKey("甲作者"))

        val outcome = coordinator.load(
            category = "言情",
            sources = listOf(
                DiscoveryEndpoint("source") { _, _ ->
                    CategoryBrowseResult.Success(
                        listOf(result("同名书", "甲作者"), result("同名书", "乙作者")),
                        hasMore = false
                    )
                }
            ),
            seenTitles = setOf("同名书"),
            seed = 1,
            seenBooks = listOf(seenAuthor)
        )

        assertEquals(listOf("乙作者"), outcome.items.map { it.author })
    }

    @Test
    fun legacyRandomBrowseApiIsNotExposedByBrowsableSource() {
        assertFalse(BrowsableNovelSource::class.java.methods.any { it.name == "randomBrowse" })
    }

    private fun result(title: String, author: String = "作者") = SearchResult(
        sourceId = "test",
        title = title,
        author = author,
        bookUrl = "https://example.com/$title",
        summary = "",
        capabilities = emptySet()
    )
}
