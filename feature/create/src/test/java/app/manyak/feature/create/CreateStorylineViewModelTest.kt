package app.manyak.feature.create

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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateStorylineViewModelTest {
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
    fun `같은 평가를 다시 누르면 해제되고 다른 평가를 누르면 바뀐다`() =
        runTest(dispatcher) {
            val viewModel = CreateStorylineViewModel()

            viewModel.onIntent(CreateStorylineIntent.ToggleRating(StorylineRating.GOOD))
            advanceUntilIdle()
            assertEquals(StorylineRating.GOOD, viewModel.uiState.value.activeRating)

            viewModel.onIntent(CreateStorylineIntent.ToggleRating(StorylineRating.BAD))
            advanceUntilIdle()
            assertEquals(StorylineRating.BAD, viewModel.uiState.value.activeRating)

            viewModel.onIntent(CreateStorylineIntent.ToggleRating(StorylineRating.BAD))
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.activeRating)
        }

    @Test
    fun `평가는 스토리라인별로 따로 보관된다`() =
        runTest(dispatcher) {
            val viewModel = CreateStorylineViewModel()

            viewModel.onIntent(CreateStorylineIntent.ToggleRating(StorylineRating.GOOD))
            advanceUntilIdle()
            viewModel.onIntent(CreateStorylineIntent.SelectStoryline(1))
            advanceUntilIdle()
            viewModel.onIntent(CreateStorylineIntent.ToggleRating(StorylineRating.BAD))
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.activeIndex)
            assertEquals(
                mapOf(0 to StorylineRating.GOOD, 1 to StorylineRating.BAD),
                viewModel.uiState.value.ratings,
            )
        }

    @Test
    fun `선택하기는 활성 스토리라인 순번을 실은 추가 정보 전환 효과를 낸다`() =
        runTest(dispatcher) {
            val viewModel = CreateStorylineViewModel()

            viewModel.onIntent(CreateStorylineIntent.SelectStoryline(2))
            advanceUntilIdle()
            viewModel.onIntent(CreateStorylineIntent.ConfirmSelection)
            advanceUntilIdle()

            val effect = withTimeoutOrNull(1_000) { viewModel.uiEffect.first() }
            assertEquals(CreateStorylineEffect.NavigateToAdditionalInfo(storylineIndex = 2), effect)
        }
}
