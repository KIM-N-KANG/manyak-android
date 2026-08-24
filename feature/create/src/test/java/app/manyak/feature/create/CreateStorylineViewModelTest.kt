package app.manyak.feature.create

import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `생성 결과가 도착하면 목록이 채워진다`() =
        runTest(dispatcher) {
            val (_, _, viewModel) = loadedViewModel()

            assertEquals(3, viewModel.uiState.value.storylines.size)
            assertFalse(viewModel.uiState.value.hasGenerationError)
            assertEquals(
                "첫 번째 스토리라인",
                viewModel.uiState.value.activeStoryline
                    ?.storyline,
            )
        }

    @Test
    fun `생성 실패는 빈 목록과 인라인 오류 상태가 된다`() =
        runTest(dispatcher) {
            val repository = FakeStoryCreationRepository()
            repository.queuedGenerationResults += DomainResult.Failure(DomainError.Network)
            val store = StorylineGenerationStore(repository)
            store.generate(sampleGenerationInput())
            val viewModel = CreateStorylineViewModel(store)
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.storylines
                    .isEmpty(),
            )
            assertTrue(viewModel.uiState.value.hasGenerationError)
        }

    @Test
    fun `같은 평가를 다시 누르면 해제되고 다른 평가를 누르면 바뀐다`() =
        runTest(dispatcher) {
            val (_, _, viewModel) = loadedViewModel()

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
            val (_, _, viewModel) = loadedViewModel()

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
            val (_, _, viewModel) = loadedViewModel()

            viewModel.onIntent(CreateStorylineIntent.SelectStoryline(2))
            advanceUntilIdle()
            viewModel.onIntent(CreateStorylineIntent.ConfirmSelection)
            advanceUntilIdle()

            val effect = withTimeoutOrNull(1_000) { viewModel.uiEffect.first() }
            assertEquals(CreateStorylineEffect.NavigateToAdditionalInfo(storylineIndex = 2), effect)
        }

    @Test
    fun `다시 만들기는 재생성을 요청하고 새 결과에서 선택과 평가를 초기화한다`() =
        runTest(dispatcher) {
            val (repository, _, viewModel) = loadedViewModel()

            viewModel.onIntent(CreateStorylineIntent.SelectStoryline(2))
            advanceUntilIdle()
            viewModel.onIntent(CreateStorylineIntent.ToggleRating(StorylineRating.GOOD))
            advanceUntilIdle()
            viewModel.onIntent(CreateStorylineIntent.Regenerate)
            advanceUntilIdle()

            assertEquals(2, repository.generationCommands.size)
            assertTrue(repository.generationCommands[1].isRegenerated)
            assertEquals(0, viewModel.uiState.value.activeIndex)
            assertTrue(
                viewModel.uiState.value.ratings
                    .isEmpty(),
            )
        }

    @Test
    fun `재생성 실패는 직전 결과를 유지한 채 인라인 오류를 켠다`() =
        runTest(dispatcher) {
            val (repository, _, viewModel) = loadedViewModel()

            repository.queuedGenerationResults += DomainResult.Failure(DomainError.Network)
            viewModel.onIntent(CreateStorylineIntent.Regenerate)
            advanceUntilIdle()

            assertEquals(3, viewModel.uiState.value.storylines.size)
            assertTrue(viewModel.uiState.value.hasGenerationError)
        }

    /** 생성 성공까지 끝낸 스토어를 관찰하는 ViewModel 을 만든다. */
    private suspend fun TestScope.loadedViewModel(): Triple<
        FakeStoryCreationRepository,
        StorylineGenerationStore,
        CreateStorylineViewModel,
    > {
        val repository = FakeStoryCreationRepository()
        val store = StorylineGenerationStore(repository)
        store.generate(sampleGenerationInput())
        val viewModel = CreateStorylineViewModel(store)
        advanceUntilIdle()
        return Triple(repository, store, viewModel)
    }
}
