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
        return CreateAdditionalInfoViewModel(StorylineGenerationStore(repository), repository, FakeChatRepository())
    }

    private class LoadedFixture(
        val repository: FakeStoryCreationRepository,
        val chatRepository: FakeChatRepository,
        val viewModel: CreateAdditionalInfoViewModel,
    )

    /** 스토리라인 생성 성공 결과를 스냅숏한 ViewModel 을 만든다. */
    private suspend fun loadedViewModel(): LoadedFixture {
        val repository = FakeStoryCreationRepository()
        val chatRepository = FakeChatRepository()
        val store = StorylineGenerationStore(repository)
        store.generate(sampleGenerationInput())
        return LoadedFixture(
            repository = repository,
            chatRepository = chatRepository,
            viewModel = CreateAdditionalInfoViewModel(store, repository, chatRepository),
        )
    }

    @Test
    fun `생성 결과의 본문과 추천 추가 정보가 화면 상태로 스냅숏된다`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel().viewModel

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
            val fixture = loadedViewModel()
            val repository = fixture.repository
            val viewModel = fixture.viewModel

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
            // 완성 성공은 완성된 스토리로 채팅을 만들어 채팅방 진입 효과를 낸다.
            assertEquals(listOf("story-1"), fixture.chatRepository.createChatStoryIds)
            assertEquals(
                CreateAdditionalInfoEffect.EnterChatAfterCompletion(chatId = "chat-1"),
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `완성 실패는 입력 화면으로 복귀하고 같은 페이로드 재시도는 요청 ID 를 재사용한다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel()
            val repository = fixture.repository
            val viewModel = fixture.viewModel
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
                CreateAdditionalInfoEffect.EnterChatAfterCompletion(chatId = "chat-1"),
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `채팅 생성 실패 재시도는 스토리 완성을 건너뛰고 채팅 생성만 재호출한다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel()
            val viewModel = fixture.viewModel
            fixture.chatRepository.queuedCreateChatResults += DomainResult.Failure(DomainError.Network)

            viewModel.onIntent(CreateAdditionalInfoIntent.CompleteStory(storylineIndex = 0))
            advanceUntilIdle()

            // 채팅 생성 실패는 완성 실패와 같은 인라인 오류로 안내한다.
            assertFalse(viewModel.uiState.value.isCompletingStory)
            assertEquals(CompletionFailure.GENERAL, viewModel.uiState.value.completionFailure)

            viewModel.onIntent(CreateAdditionalInfoIntent.CompleteStory(storylineIndex = 0))
            advanceUntilIdle()

            assertEquals(1, fixture.repository.completionCommands.size)
            assertEquals(listOf("story-1", "story-1"), fixture.chatRepository.createChatStoryIds)
            assertEquals(
                CreateAdditionalInfoEffect.EnterChatAfterCompletion(chatId = "chat-1"),
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `페이로드가 바뀐 재시도는 새 요청 ID 를 쓴다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel()
            val repository = fixture.repository
            val viewModel = fixture.viewModel
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
            val fixture = loadedViewModel()
            val repository = fixture.repository
            val viewModel = fixture.viewModel
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
