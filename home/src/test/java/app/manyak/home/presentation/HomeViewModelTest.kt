package app.manyak.home.presentation

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
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
    fun `진입 시 오리지널 목록을 조회해 서버 순서 그대로 상태에 담는다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val viewModel = HomeViewModel(storyRepository = repository, analytics = NoOpAnalytics)
            advanceUntilIdle()

            assertEquals(1, repository.originalStoriesCallCount)
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.loadFailed)
            assertEquals(sampleStories(), state.stories)
        }

    @Test
    fun `빈 목록도 실패가 아니다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            repository.queuedResults += DomainResult.Success(emptyList())
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

            assertEquals(2, repository.originalStoriesCallCount)
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
            assertEquals(1, repository.originalStoriesCallCount)

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

            assertEquals(2, repository.originalStoriesCallCount)
            assertFalse(viewModel.uiState.value.isRefreshing)
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
}
