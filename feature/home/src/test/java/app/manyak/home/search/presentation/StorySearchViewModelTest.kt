package app.manyak.home.search.presentation

import androidx.lifecycle.SavedStateHandle
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.home.entity.StoryPage
import app.manyak.home.search.domain.RecentSearchRepository
import app.manyak.home.search.domain.prependRecentSearch
import app.manyak.home.testing.FakeStoryRepository
import app.manyak.home.testing.sampleStories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorySearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeStoryRepository()
    private val recent = FakeRecentSearches()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        StorySearchViewModel(repository, recent, saved)

    @Test
    fun `앞뒤 공백을 뺀 2자 미만은 요청하지 않고 안내만 한다`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val effect = backgroundScope.async { viewModel.uiEffect.first() }
            runCurrent()

            viewModel.onIntent(StorySearchIntent.Submit("  가 "))
            advanceUntilIdle()

            assertEquals(StorySearchEffect.ShowQueryTooShort, effect.await())
            assertTrue(repository.searchRequests.isEmpty())
            assertTrue(recent.terms.value.isEmpty())
            assertNull(viewModel.uiState.value.query)
        }

    @Test
    fun `제출한 검색어를 다듬어 찾고 최근 검색어 맨 앞에 남긴다`() =
        runTest(dispatcher) {
            recent.terms.value = listOf("판타지", "로맨스")
            val viewModel = viewModel()

            viewModel.onIntent(StorySearchIntent.Submit(" 로맨스 "))
            advanceUntilIdle()

            assertEquals(listOf("로맨스" to null), repository.searchRequests)
            assertEquals(listOf("로맨스", "판타지"), viewModel.uiState.value.recentTerms)
            assertEquals(sampleStories(), viewModel.uiState.value.stories)
        }

    @Test
    fun `빈 페이지는 다음 페이지가 있는 동안 이어 받고 끝까지 비면 결과 없음이다`() =
        runTest(dispatcher) {
            repository.queuedSearchResults += DomainResult.Success(StoryPage(emptyList(), nextCursor = "c1"))
            repository.queuedSearchResults += DomainResult.Success(StoryPage(emptyList(), nextCursor = null))
            val viewModel = viewModel()

            viewModel.onIntent(StorySearchIntent.Submit("시계공"))
            advanceUntilIdle()

            assertEquals(listOf("시계공" to null, "시계공" to "c1"), repository.searchRequests)
            assertTrue(
                viewModel.uiState.value.stories
                    .isEmpty(),
            )
            assertEquals(false, viewModel.uiState.value.loadFailed)
        }

    @Test
    fun `첫 조회 실패 뒤 다시 시도는 같은 검색어로 처음부터 찾는다`() =
        runTest(dispatcher) {
            repository.queuedSearchResults += DomainResult.Failure(DomainError.Network)
            val viewModel = viewModel()

            viewModel.onIntent(StorySearchIntent.Submit("시계공"))
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.loadFailed)

            viewModel.onIntent(StorySearchIntent.Retry)
            advanceUntilIdle()

            assertEquals(listOf("시계공" to null, "시계공" to null), repository.searchRequests)
            assertEquals(sampleStories(), viewModel.uiState.value.stories)
        }

    @Test
    fun `지우면 검색 전 화면으로 돌아가고 저장된 검색어로 다시 열면 같은 검색을 연다`() =
        runTest(dispatcher) {
            val saved = SavedStateHandle()
            val viewModel = viewModel(saved)
            viewModel.onIntent(StorySearchIntent.Submit("시계공"))
            advanceUntilIdle()

            val restored = viewModel(SavedStateHandle(mapOf("query" to "시계공")))
            advanceUntilIdle()
            assertEquals("시계공", restored.uiState.value.query)

            viewModel.onIntent(StorySearchIntent.Clear)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.query)
            assertTrue(
                viewModel.uiState.value.stories
                    .isEmpty(),
            )
        }

    @Test
    fun `최근 검색어는 같은 검색어를 맨 앞으로 옮기고 10개까지 남긴다`() {
        val terms = (1..10).map { "검색$it" }

        assertEquals(listOf("검색3", "검색1", "검색2"), listOf("검색1", "검색2", "검색3").prependRecentSearch("검색3"))
        assertEquals(listOf("새 검색") + terms.take(9), terms.prependRecentSearch("새 검색"))
    }
}

private class FakeRecentSearches : RecentSearchRepository {
    override val terms = MutableStateFlow<List<String>>(emptyList())

    override suspend fun add(term: String) {
        terms.value = terms.value.prependRecentSearch(term)
    }

    override suspend fun remove(term: String) {
        terms.value = terms.value - term
    }

    override suspend fun clear() {
        terms.value = emptyList()
    }
}
