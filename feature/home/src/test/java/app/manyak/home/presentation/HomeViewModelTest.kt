package app.manyak.home.presentation

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.home.entity.HomeSection
import app.manyak.home.testing.FakeStoryRepository
import app.manyak.home.testing.sampleStories
import app.manyak.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private fun viewModel(repository: FakeStoryRepository) = HomeViewModel(repository, NoOpAnalytics)

    @Test
    fun `카드가 없는 섹션은 빼고 서버 순서 그대로 담는다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val popular = HomeSection(id = null, name = "인기 스토리", stories = sampleStories())
            val original = HomeSection(id = null, name = "오리지널 스토리", stories = sampleStories().reversed())
            val empty = HomeSection(id = "s-empty", name = "빈 섹션", stories = emptyList())
            repository.queuedHomeResults += DomainResult.Success(listOf(popular, empty, original))
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals(listOf(popular, original), state.sections)
        }

    @Test
    fun `모든 섹션이 비면 실패가 아닌 빈 홈이다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            repository.queuedHomeResults +=
                DomainResult.Success(listOf(HomeSection(id = null, name = "인기 스토리", emptyList())))
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.loadFailed)
            assertTrue(state.sections.isEmpty())
        }

    @Test
    fun `조회 실패는 실패 상태가 되고 재시도로 다시 조회한다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            repository.queuedHomeResults += DomainResult.Failure(DomainError.Network)
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.loadFailed)

            viewModel.onIntent(HomeIntent.Retry)
            advanceUntilIdle()

            assertEquals(2, repository.homeRequests)
            assertFalse(viewModel.uiState.value.loadFailed)
            assertEquals(1, viewModel.uiState.value.sections.size)
        }

    @Test
    fun `조회가 진행 중이면 재시도가 중복 호출하지 않는다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val gate = CompletableDeferred<Unit>()
            repository.inFlightGate = gate
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.onIntent(HomeIntent.Retry)
            advanceUntilIdle()
            assertEquals(1, repository.homeRequests)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.sections.size)
        }

    @Test
    fun `새로고침이 실패하면 보던 섹션을 두고 토스트만 알린다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            val shown = viewModel.uiState.value.sections

            repository.queuedHomeResults += DomainResult.Failure(DomainError.Network)
            val effect = backgroundScope.async { viewModel.uiEffect.first() }
            viewModel.onIntent(HomeIntent.Refresh)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(shown, state.sections)
            assertFalse(state.isRefreshing)
            assertFalse(state.loadFailed)
            assertEquals(HomeEffect.ShowRefreshFailed, effect.await())
        }
}
