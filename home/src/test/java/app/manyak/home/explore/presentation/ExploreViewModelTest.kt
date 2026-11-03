package app.manyak.home.explore.presentation

import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.home.entity.StoryPage
import app.manyak.home.testing.FakeStoryRepository
import app.manyak.home.testing.sampleStories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val first = sampleStories().first()
    private val second = sampleStories().last()

    @Test
    fun `탭 진입 값이 바뀔 때만 커서 없이 새로 섞고 같은 값은 보던 목록을 남긴다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val viewModel = ExploreViewModel(repository)

            viewModel.onIntent(ExploreIntent.Enter(1))
            advanceUntilIdle()
            // 상세 복귀와 구성 변경은 같은 값을 다시 보낸다.
            viewModel.onIntent(ExploreIntent.Enter(1))
            advanceUntilIdle()
            assertEquals(listOf<String?>(null), repository.exploreRequests)
            val firstEntry = viewModel.uiState.value.entryVersion

            viewModel.onIntent(ExploreIntent.Enter(2))
            advanceUntilIdle()

            assertEquals(listOf<String?>(null, null), repository.exploreRequests)
            assertEquals(firstEntry + 1, viewModel.uiState.value.entryVersion)
            assertEquals(sampleStories(), viewModel.uiState.value.stories)
        }

    @Test
    fun `다음 페이지는 받은 커서로 같은 순서를 잇고 커서가 없으면 멈춘다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            repository.queuedExploreResults += DomainResult.Success(StoryPage(listOf(first), nextCursor = "c1"))
            repository.queuedExploreResults += DomainResult.Success(StoryPage(listOf(second), nextCursor = null))
            val viewModel = ExploreViewModel(repository)
            viewModel.onIntent(ExploreIntent.Enter(1))
            advanceUntilIdle()

            viewModel.onIntent(ExploreIntent.LoadMore)
            advanceUntilIdle()
            viewModel.onIntent(ExploreIntent.LoadMore)
            advanceUntilIdle()

            assertEquals(listOf(null, "c1"), repository.exploreRequests)
            assertEquals(listOf(first, second), viewModel.uiState.value.stories)
            assertFalse(viewModel.uiState.value.hasMore)
        }

    @Test
    fun `새로고침은 커서 없는 첫 페이지 하나로 바꾸고 칸 자리를 지킨다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            repository.queuedExploreResults += DomainResult.Success(StoryPage(listOf(first), nextCursor = "c1"))
            repository.queuedExploreResults += DomainResult.Success(StoryPage(listOf(second), nextCursor = "c2"))
            repository.queuedExploreResults += DomainResult.Success(StoryPage(listOf(second), nextCursor = "c3"))
            val viewModel = ExploreViewModel(repository)
            viewModel.onIntent(ExploreIntent.Enter(1))
            advanceUntilIdle()
            viewModel.onIntent(ExploreIntent.LoadMore)
            advanceUntilIdle()
            val entryVersion = viewModel.uiState.value.entryVersion

            viewModel.onIntent(ExploreIntent.Refresh)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(null, "c1", null), repository.exploreRequests)
            assertEquals(listOf(second), state.stories)
            assertEquals("c3", state.nextCursor)
            assertEquals(entryVersion, state.entryVersion)
        }

    @Test
    fun `다음 페이지 실패는 받은 목록을 지우지 않는다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            repository.queuedExploreResults += DomainResult.Success(StoryPage(listOf(first), nextCursor = "c1"))
            repository.queuedExploreResults += DomainResult.Failure(DomainError.Network)
            val viewModel = ExploreViewModel(repository)
            viewModel.onIntent(ExploreIntent.Enter(1))
            advanceUntilIdle()

            viewModel.onIntent(ExploreIntent.LoadMore)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.loadMoreFailed)
            assertEquals(listOf(first), state.stories)
            assertEquals("c1", state.nextCursor)
        }
}
