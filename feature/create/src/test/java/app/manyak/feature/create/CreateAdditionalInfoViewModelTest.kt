package app.manyak.feature.create

import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateAdditionalInfoViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): CreateAdditionalInfoViewModel {
        val repository = FakeStoryCreationRepository()
        return CreateAdditionalInfoViewModel(StorylineGenerationStore(repository), repository)
    }

    /** 스토리라인 생성 성공 결과를 스냅숏한 ViewModel 을 만든다. */
    private suspend fun loadedViewModel(): Pair<FakeStoryCreationRepository, CreateAdditionalInfoViewModel> {
        val repository = FakeStoryCreationRepository()
        val store = StorylineGenerationStore(repository)
        store.generate(sampleGenerationInput())
        return repository to CreateAdditionalInfoViewModel(store, repository)
    }

    @Test
    fun `생성 결과의 본문과 추천 추가 정보가 화면 상태로 스냅숏된다`() =
        runTest(dispatcher) {
            val (_, viewModel) = loadedViewModel()

            assertEquals(10L, viewModel.uiState.value.simpleCreationId)
            assertEquals(
                listOf("첫 번째 스토리라인", "두 번째 스토리라인", "세 번째 스토리라인"),
                viewModel.uiState.value.storylines
                    .map { it.text },
            )
            assertEquals(
                listOf("폐허를 자세히 그려줘"),
                viewModel.uiState.value.storylines
                    .first()
                    .recommendedInfos,
            )
        }

    @Test
    fun `스토리 완성 요청은 추천 채택분을 앞세우고 빈 자유 입력을 뺀다`() =
        runTest(dispatcher) {
            val (repository, viewModel) = loadedViewModel()

            viewModel.onIntent(CreateAdditionalInfoIntent.ToggleRecommendation("폐허를 자세히 그려줘"))
            advanceUntilIdle()
            val inputId =
                viewModel.uiState.value.additionalInfos
                    .first()
                    .id
            viewModel.onIntent(CreateAdditionalInfoIntent.ChangeInput(inputId, " 배경은 현대의 서울로 해줘 "))
            advanceUntilIdle()
            viewModel.onIntent(CreateAdditionalInfoIntent.CompleteStory(storylineIndex = 0))
            advanceUntilIdle()

            val command = repository.completionCommands.single()
            assertEquals(10L, command.simpleCreationId)
            assertEquals(1L, command.storylineId)
            assertEquals(listOf("폐허를 자세히 그려줘", "배경은 현대의 서울로 해줘"), command.additionalInfos)
            assertEquals(
                CreateAdditionalInfoEffect.ExitFunnelAfterCompletion,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `완성 실패는 입력 화면으로 복귀하고 같은 페이로드 재시도는 요청 ID 를 재사용한다`() =
        runTest(dispatcher) {
            val (repository, viewModel) = loadedViewModel()
            repository.queuedCompletionResults += DomainResult.Failure(DomainError.Network)

            viewModel.onIntent(CreateAdditionalInfoIntent.CompleteStory(storylineIndex = 0))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isCompletingStory)
            assertEquals(CompletionFailure.GENERAL, viewModel.uiState.value.completionFailure)

            viewModel.onIntent(CreateAdditionalInfoIntent.CompleteStory(storylineIndex = 0))
            advanceUntilIdle()

            val (first, second) = repository.completionCommands
            assertEquals(first.requestId, second.requestId)
            assertEquals(
                CreateAdditionalInfoEffect.ExitFunnelAfterCompletion,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `페이로드가 바뀐 재시도는 새 요청 ID 를 쓴다`() =
        runTest(dispatcher) {
            val (repository, viewModel) = loadedViewModel()
            repository.queuedCompletionResults += DomainResult.Failure(DomainError.Network)

            viewModel.onIntent(CreateAdditionalInfoIntent.CompleteStory(storylineIndex = 0))
            advanceUntilIdle()
            val inputId =
                viewModel.uiState.value.additionalInfos
                    .first()
                    .id
            viewModel.onIntent(CreateAdditionalInfoIntent.ChangeInput(inputId, "새로운 정보"))
            advanceUntilIdle()
            viewModel.onIntent(CreateAdditionalInfoIntent.CompleteStory(storylineIndex = 0))
            advanceUntilIdle()

            val (first, second) = repository.completionCommands
            assertNotEquals(first.requestId, second.requestId)
        }

    @Test
    fun `크레딧 부족 402 는 실패 사유를 구분한다`() =
        runTest(dispatcher) {
            val (repository, viewModel) = loadedViewModel()
            repository.queuedCompletionResults +=
                DomainResult.Failure(DomainError.Server(status = 402, code = null, requestId = null))

            viewModel.onIntent(CreateAdditionalInfoIntent.CompleteStory(storylineIndex = 0))
            advanceUntilIdle()

            assertEquals(CompletionFailure.CREDIT, viewModel.uiState.value.completionFailure)
        }

    @Test
    fun `추천 추가 정보는 다시 누르면 해제된다`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.onIntent(CreateAdditionalInfoIntent.ToggleRecommendation("배경을 자세히 그려줘"))
            advanceUntilIdle()
            assertEquals(setOf("배경을 자세히 그려줘"), viewModel.uiState.value.selectedRecommendations)

            viewModel.onIntent(CreateAdditionalInfoIntent.ToggleRecommendation("배경을 자세히 그려줘"))
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.selectedRecommendations
                    .isEmpty(),
            )
        }

    @Test
    fun `입력은 상한까지만 추가되고 삭제한 입력은 목록에서 빠진다`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            repeat(CreateAdditionalInfoUiState.INPUT_MAX_COUNT) {
                viewModel.onIntent(CreateAdditionalInfoIntent.AddInput)
                advanceUntilIdle()
            }
            assertEquals(
                CreateAdditionalInfoUiState.INPUT_MAX_COUNT,
                viewModel.uiState.value.additionalInfos.size,
            )
            assertFalse(viewModel.uiState.value.canAddInput)

            val firstId =
                viewModel.uiState.value.additionalInfos
                    .first()
                    .id
            viewModel.onIntent(CreateAdditionalInfoIntent.RemoveInput(firstId))
            advanceUntilIdle()

            assertEquals(
                CreateAdditionalInfoUiState.INPUT_MAX_COUNT - 1,
                viewModel.uiState.value.additionalInfos.size,
            )
            assertTrue(
                viewModel.uiState.value.additionalInfos
                    .none { it.id == firstId },
            )
        }

    @Test
    fun `입력값은 최대 길이까지만 반영된다`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val inputId =
                viewModel.uiState.value.additionalInfos
                    .first()
                    .id
            val overflow = "가".repeat(CreateAdditionalInfoUiState.INPUT_MAX_LENGTH + 10)

            viewModel.onIntent(CreateAdditionalInfoIntent.ChangeInput(inputId, overflow))
            advanceUntilIdle()

            assertEquals(
                CreateAdditionalInfoUiState.INPUT_MAX_LENGTH,
                viewModel.uiState.value.additionalInfos
                    .first()
                    .value.length,
            )
        }
}
