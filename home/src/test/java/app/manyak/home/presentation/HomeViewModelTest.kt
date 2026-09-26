package app.manyak.home.presentation

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.home.entity.StoryListFilter
import app.manyak.home.entity.StoryListQuery
import app.manyak.home.entity.StoryListSort
import app.manyak.home.entity.StoryPage
import app.manyak.home.testing.FakeStoryRepository
import app.manyak.home.testing.sampleStories
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `진입 시 전체·최신순 첫 페이지를 조회해 서버 순서 그대로 상태에 담는다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val viewModel = HomeViewModel(storyRepository = repository, analytics = NoOpAnalytics)
            advanceUntilIdle()

            assertEquals(listOf(StoryListQuery(StoryListFilter.ALL, StoryListSort.LATEST) to null), repository.requests)
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.loadFailed)
            assertEquals(sampleStories(), state.stories)
        }

    @Test
    fun `빈 목록도 실패가 아니다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            repository.queuedResults += DomainResult.Success(StoryPage(emptyList(), nextCursor = null))
            val viewModel = HomeViewModel(storyRepository = repository, analytics = NoOpAnalytics)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.loadFailed)
            assertTrue(state.stories.isEmpty())
        }

    @Test
    fun `조회 실패는 실패 상태가 되고 재시도로 다시 조회한다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            repository.queuedResults += DomainResult.Failure(DomainError.Network)
            val viewModel = HomeViewModel(storyRepository = repository, analytics = NoOpAnalytics)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.loadFailed)
            assertFalse(viewModel.uiState.value.isLoading)

            viewModel.onIntent(HomeIntent.Retry)
            advanceUntilIdle()

            assertEquals(2, repository.requests.size)
            val state = viewModel.uiState.value
            assertFalse(state.loadFailed)
            assertEquals(sampleStories(), state.stories)
        }

    @Test
    fun `조회가 진행 중이면 재시도가 중복 호출하지 않는다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val gate = CompletableDeferred<Unit>()
            repository.inFlightGate = gate
            val viewModel = HomeViewModel(storyRepository = repository, analytics = NoOpAnalytics)
            advanceUntilIdle()

            // 진입 조회가 아직 응답을 기다리는 동안 재시도를 눌렀을 때다.
            viewModel.onIntent(HomeIntent.Retry)
            advanceUntilIdle()
            assertEquals(1, repository.requests.size)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(sampleStories(), viewModel.uiState.value.stories)
        }

    @Test
    fun `당겨서 새로고침은 골격 없이 목록을 다시 읽는다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val viewModel = HomeViewModel(storyRepository = repository, analytics = NoOpAnalytics)
            advanceUntilIdle()

            val gate = CompletableDeferred<Unit>()
            repository.inFlightGate = gate
            viewModel.onIntent(HomeIntent.Refresh)
            advanceUntilIdle()

            // 새로고침 중에도 목록이 남아 있어야 골격이 다시 깔리지 않는다.
            val refreshing = viewModel.uiState.value
            assertTrue(refreshing.isRefreshing)
            assertFalse(refreshing.isLoading)
            assertEquals(sampleStories(), refreshing.stories)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(2, repository.requests.size)
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertEquals(2, viewModel.uiState.value.firstPageVersion)
        }

    @Test
    fun `새로고침 실패는 목록을 그대로 두고 실패 안내를 보낸다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val viewModel = HomeViewModel(storyRepository = repository, analytics = NoOpAnalytics)
            advanceUntilIdle()

            repository.queuedResults += DomainResult.Failure(DomainError.Network)
            viewModel.onIntent(HomeIntent.Refresh)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isRefreshing)
            assertFalse(state.loadFailed)
            assertEquals(sampleStories(), state.stories)
            assertEquals(
                HomeEffect.ShowRefreshFailed,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `목록 끝에 닿으면 같은 조건에 커서를 실어 이어 붙이고 중복 스토리는 뺀다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val (first, second) = sampleStories()
            val third = first.copy(id = "story-3")
            repository.queuedResults += DomainResult.Success(StoryPage(listOf(first, second), nextCursor = "c1"))
            repository.queuedResults += DomainResult.Success(StoryPage(listOf(second, third), nextCursor = null))
            val viewModel = HomeViewModel(storyRepository = repository, analytics = NoOpAnalytics)
            advanceUntilIdle()

            viewModel.onIntent(HomeIntent.LoadMore)
            advanceUntilIdle()

            assertEquals(StoryListQuery() to "c1", repository.requests.last())
            val state = viewModel.uiState.value
            assertEquals(listOf(first, second, third), state.stories)
            assertFalse(state.hasMore)
            // 이어 붙인 페이지는 스크롤 위치를 그대로 잇는다.
            assertEquals(1, state.firstPageVersion)
        }

    @Test
    fun `다음 페이지 실패는 목록을 두고 다시 시도로 이어 읽는다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            repository.queuedResults += DomainResult.Success(StoryPage(sampleStories(), nextCursor = "c1"))
            repository.queuedResults += DomainResult.Failure(DomainError.Network)
            val viewModel = HomeViewModel(storyRepository = repository, analytics = NoOpAnalytics)
            advanceUntilIdle()

            viewModel.onIntent(HomeIntent.LoadMore)
            advanceUntilIdle()

            val failed = viewModel.uiState.value
            assertTrue(failed.loadMoreFailed)
            assertFalse(failed.loadFailed)
            assertEquals(sampleStories(), failed.stories)

            viewModel.onIntent(HomeIntent.LoadMore)
            advanceUntilIdle()

            assertEquals(StoryListQuery() to "c1", repository.requests.last())
            assertFalse(viewModel.uiState.value.loadMoreFailed)
        }

    @Test
    fun `필터·정렬을 바꾸면 첫 페이지부터 새 조건으로 다시 읽는다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            repository.queuedResults += DomainResult.Success(StoryPage(sampleStories(), nextCursor = "c1"))
            val viewModel = HomeViewModel(storyRepository = repository, analytics = NoOpAnalytics)
            advanceUntilIdle()

            viewModel.onIntent(HomeIntent.SelectFilter(StoryListFilter.ORIGINAL))
            advanceUntilIdle()
            viewModel.onIntent(HomeIntent.SelectSort(StoryListSort.CHATS))
            advanceUntilIdle()

            val expected = StoryListQuery(StoryListFilter.ORIGINAL, StoryListSort.CHATS)
            assertEquals(expected to null, repository.requests.last())
            val state = viewModel.uiState.value
            assertEquals(expected, state.query)
            assertEquals(sampleStories(), state.stories)
            assertFalse(state.hasMore)
        }

    @Test
    fun `조건을 바꾸면 새 첫 페이지가 올 때까지 보던 목록을 남기고 이어 읽지 않는다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            repository.queuedResults += DomainResult.Success(StoryPage(sampleStories(), nextCursor = "c1"))
            val viewModel = HomeViewModel(storyRepository = repository, analytics = NoOpAnalytics)
            advanceUntilIdle()

            val gate = CompletableDeferred<Unit>()
            repository.inFlightGate = gate
            viewModel.onIntent(HomeIntent.SelectFilter(StoryListFilter.ORIGINAL))
            advanceUntilIdle()

            val loading = viewModel.uiState.value
            assertTrue(loading.isLoading)
            assertEquals(sampleStories(), loading.stories)
            assertFalse(loading.hasMore)
            // 남겨 둔 목록은 보던 스크롤 위치를 그대로 쓴다.
            assertEquals(1, loading.firstPageVersion)

            val fresh = sampleStories().first().copy(id = "fresh")
            repository.queuedResults += DomainResult.Success(StoryPage(listOf(fresh), nextCursor = null))
            gate.complete(Unit)
            advanceUntilIdle()

            val loaded = viewModel.uiState.value
            assertFalse(loaded.isLoading)
            assertEquals(listOf(fresh), loaded.stories)
            // 새 목록은 새 스크롤 상태로 맨 위에서 시작한다.
            assertEquals(2, loaded.firstPageVersion)
        }

    @Test
    fun `이미 선택한 필터를 다시 고르면 다시 읽지 않는다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val viewModel = HomeViewModel(storyRepository = repository, analytics = NoOpAnalytics)
            advanceUntilIdle()

            viewModel.onIntent(HomeIntent.SelectFilter(StoryListFilter.ALL))
            advanceUntilIdle()

            assertEquals(1, repository.requests.size)
        }

    @Test
    fun `다음 페이지를 받는 중에 조건을 바꾸면 이전 조건의 페이지를 붙이지 않는다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            repository.queuedResults += DomainResult.Success(StoryPage(sampleStories(), nextCursor = "c1"))
            val viewModel = HomeViewModel(storyRepository = repository, analytics = NoOpAnalytics)
            advanceUntilIdle()

            val gate = CompletableDeferred<Unit>()
            repository.inFlightGate = gate
            viewModel.onIntent(HomeIntent.LoadMore)
            advanceUntilIdle()
            // 멈춰 있던 다음 페이지가 풀리면 이 결과를 받게 된다.
            val stale = sampleStories().first().copy(id = "stale")
            repository.queuedResults += DomainResult.Success(StoryPage(listOf(stale), nextCursor = null))
            repository.inFlightGate = null

            viewModel.onIntent(HomeIntent.SelectSort(StoryListSort.LIKES))
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(StoryListQuery(sort = StoryListSort.LIKES) to null, repository.requests.last())
            assertEquals(StoryListSort.LIKES, state.query.sort)
            assertEquals(listOf(stale), state.stories)
            assertFalse(state.isLoading)
            assertFalse(state.isLoadingMore)
        }
}
