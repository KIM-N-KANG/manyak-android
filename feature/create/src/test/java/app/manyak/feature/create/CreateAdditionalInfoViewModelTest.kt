package app.manyak.feature.create

import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.CompletedStory
import app.manyak.core.domain.story.CreationProgress
import app.manyak.core.domain.story.CreationRequestSnapshot
import app.manyak.core.domain.story.PendingStoryCreation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        val pendingStore = FakePendingStoryCreationStore()
        return CreateAdditionalInfoViewModel(
            StorylineGenerationStore(repository, pendingStore),
            repository,
            FakeChatRepository(),
            pendingStore,
        )
    }

    private class LoadedFixture(
        val repository: FakeStoryCreationRepository,
        val chatRepository: FakeChatRepository,
        val pendingStore: FakePendingStoryCreationStore,
        val viewModel: CreateAdditionalInfoViewModel,
    )

    /** 스토리라인 생성 성공 결과를 스냅숏한 ViewModel 을 만든다. */
    private suspend fun loadedViewModel(): LoadedFixture {
        val repository = FakeStoryCreationRepository()
        val chatRepository = FakeChatRepository()
        val pendingStore = FakePendingStoryCreationStore()
        val store = StorylineGenerationStore(repository, pendingStore)
        store.generate(sampleGenerationInput())
        return LoadedFixture(
            repository = repository,
            chatRepository = chatRepository,
            pendingStore = pendingStore,
            viewModel = CreateAdditionalInfoViewModel(store, repository, chatRepository, pendingStore),
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
    fun `완성 요청은 시작 전에 영속되고 채팅 진입 후 레코드가 지워진다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel()
            val viewModel = fixture.viewModel

            viewModel.onIntent(CreateAdditionalInfoIntent.CompleteStory(storylineIndex = 0))
            advanceUntilIdle()

            val written = fixture.pendingStore.writes.last() as PendingStoryCreation.CompletingStory
            val sentCommand = fixture.repository.completionCommands.single()
            assertEquals(sentCommand.requestId, written.command.requestId)
            assertEquals(null, fixture.pendingStore.current)
        }

    @Test
    fun `완성 재시도 409 는 복구 폴링으로 결과를 되찾아 채팅 생성으로 잇는다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel()
            val repository = fixture.repository
            val viewModel = fixture.viewModel
            repository.queuedCompletionResults +=
                DomainResult.Failure(DomainError.Server(status = 409, code = null, requestId = null))

            viewModel.onIntent(CreateAdditionalInfoIntent.CompleteStory(storylineIndex = 0))
            advanceUntilIdle()
            // 409 는 실패 화면이 아니라 로딩을 유지한 채 복구 폴링 대상이 된다.
            assertTrue(viewModel.uiState.value.isCompletingStory)

            repository.queuedCreationRequestResults +=
                DomainResult.Success(CreationRequestSnapshot.StoryReady(CompletedStory(id = "story-7", title = "복구")))
            val recovery = launch { viewModel.driveCompletionRecovery() }
            advanceUntilIdle()

            assertEquals(listOf("story-7"), fixture.chatRepository.createChatStoryIds)
            assertEquals(null, fixture.pendingStore.current)
            assertEquals(
                CreateAdditionalInfoEffect.EnterChatAfterCompletion(chatId = "chat-1"),
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
            recovery.cancel()
        }

    @Test
    fun `임시 저장본 재개는 스토어 복원 스냅숏으로 입력과 추천 선택을 되살린다`() =
        runTest(dispatcher) {
            val repository = FakeStoryCreationRepository()
            val pendingStore =
                FakePendingStoryCreationStore(
                    initial =
                        PendingStoryCreation.Draft(
                            generationCommand = null,
                            generation = sampleStorylineGeneration(),
                            progress =
                                CreationProgress(
                                    selectedStorylineIndex = 0,
                                    activeStorylineIndex = 0,
                                    additionalInfoInputs = listOf("배경은 서울", ""),
                                    selectedRecommendations = listOf("폐허를 자세히 그려줘", "사라진 추천"),
                                ),
                        ),
                )
            val store = StorylineGenerationStore(repository, pendingStore)
            val viewModel =
                CreateAdditionalInfoViewModel(store, repository, FakeChatRepository(), pendingStore)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(10L, state.simpleCreationId)
            assertEquals(listOf("배경은 서울", ""), state.additionalInfos.map { it.value })
            // 현재 생성 결과에 없는 추천 선택은 복원하지 않는다 — 완성 요청에 섞여 실리지 않게 한다.
            assertEquals(setOf("폐허를 자세히 그려줘"), state.selectedRecommendations)
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
