package app.manyak.chat.room.presentation

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.chat.entity.ChatDetail
import app.manyak.chat.entity.ChatStreamEvent
import app.manyak.chat.entity.ChatTurn
import app.manyak.chat.entity.GeneratingTurn
import app.manyak.chat.room.presentation.composer.InputBlockType
import app.manyak.chat.testing.FakeAttendanceClaimer
import app.manyak.chat.testing.FakeChatPreferencesRepository
import app.manyak.chat.testing.FakeChatRepository
import app.manyak.chat.testing.FakeCreditPolicyRepository
import app.manyak.chat.testing.FakeReportRepository
import app.manyak.chat.testing.FakeTrialsRepository
import app.manyak.chat.testing.FakeUserProfileRepository
import app.manyak.chat.testing.sampleChatDetail
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.designsystem.text.PassageSegment
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

/** 끊긴 생성과 나갔다 들어온 방의 생성 중인 턴을 이어받는다. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRoomResumeTest {
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
    fun `보낼 때 화면의 마지막 턴을 싣고 첫 턴이면 null 을 싣는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            sending(repository)

            val empty = FakeChatRepository()
            empty.queuedChatDetailResults += DomainResult.Success(sampleChatDetail(turns = emptyList()))
            sending(empty)

            assertEquals(listOf<Long?>(1), repository.streamedLastTurnIds)
            assertEquals(listOf<Long?>(null), empty.streamedLastTurnIds)
        }

    @Test
    fun `끊기면 받은 생성 ID 로 바로 다시 붙고 본문을 처음부터 다시 쌓는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = sending(repository)
            repository.streamEvents.send(ChatStreamEvent.Started("gen-1"))
            repository.streamEvents.send(ChatStreamEvent.Token("문이 "))
            repository.streamEvents.send(ChatStreamEvent.Interrupted)
            repository.resumeEvents.send(ChatStreamEvent.Started("gen-1"))
            repository.resumeEvents.send(ChatStreamEvent.Token("문이 열린다"))
            advanceUntilIdle()

            assertEquals(listOf("gen-1"), repository.resumedGenerationIds)
            assertTrue(viewModel.uiState.value.isStreaming)
            assertEquals(
                listOf(PassageSegment.Text("문이 열린다")),
                viewModel.uiState.value.streaming
                    ?.segments,
            )

            repository.queuedChatDetailResults += DomainResult.Success(detail(twoTurns()))
            repository.resumeEvents.send(ChatStreamEvent.Completed)
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.turns.size)
            assertNull(viewModel.uiState.value.streaming)
            assertNoEffect(viewModel)
        }

    @Test
    fun `다시 붙지 못하거나 상세를 받지 못해도 끝난 것으로 보지 않고 상세의 생성 ID 를 다시 찾는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = sending(repository)
            repository.streamEvents.send(ChatStreamEvent.Started("gen-1"))
            repository.streamEvents.send(ChatStreamEvent.Interrupted)
            repository.resumeEvents.send(ChatStreamEvent.Failed(DomainError.Server(503, null, null)))
            repository.queuedChatDetailResults += DomainResult.Failure(DomainError.Network)
            repository.queuedChatDetailResults += DomainResult.Success(detail(generating = continuing("gen-1")))
            advanceUntilIdle()

            assertEquals(listOf("gen-1", "gen-1"), repository.resumedGenerationIds)
            assertTrue(viewModel.uiState.value.isStreaming)
        }

    @Test
    fun `끝내 받지 못해도 새 턴이 저장됐으면 안내 없이 완료로 보인다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = sending(repository)
            repository.streamEvents.send(ChatStreamEvent.Interrupted)
            repository.queuedChatDetailResults += DomainResult.Success(detail(twoTurns()))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, state.turns.size)
            assertNull(state.streaming)
            assertFalse(state.isStreaming)
            assertNoEffect(viewModel)
        }

    @Test
    fun `시도를 다 쓴 생성은 다시 따라가지 않고 실패를 알린다`() =
        runTest(dispatcher) {
            // 아직 생성 중이어도 곧바로 또 따라가면 끝없이 반복한다. 재진입이나 다시 보내기로 이어받는다.
            val repository = FakeChatRepository()
            val viewModel = sending(repository)
            repository.queuedChatDetailResults += DomainResult.Success(detail(generating = continuing("gen-1")))
            repository.streamEvents.send(ChatStreamEvent.Started("gen-1"))
            repository.streamEvents.send(ChatStreamEvent.Interrupted)
            repeat(MAX_GENERATION_RECONNECTS) {
                repository.resumeEvents.send(ChatStreamEvent.Failed(DomainError.Server(404, null, null)))
            }
            advanceUntilIdle()

            assertEquals(MAX_GENERATION_RECONNECTS, repository.resumedGenerationIds.size)
            assertNull(viewModel.uiState.value.streaming)
            assertFalse(viewModel.uiState.value.isStreaming)
            assertEquals(ChatRoomEffect.ShowStreamFailure(null), viewModel.uiEffect.first())
        }

    @Test
    fun `들어온 방에 이어쓰기가 생성 중이면 보낸 입력과 함께 잠그고 이어받는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            repository.queuedChatDetailResults += DomainResult.Success(detail(generating = continuing("gen-9")))
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf("gen-9"), repository.resumedGenerationIds)
            assertTrue(state.isStreaming)
            assertEquals("문을 민다", state.streaming?.userInput)
            assertNull(state.regeneratingTurnId)
            // 생성 중인 턴이 있으면 선택지를 만들지 않는다.
            assertEquals(emptyList<Long>(), repository.generatedChoiceTurnIds)

            repository.queuedChatDetailResults += DomainResult.Success(detail(twoTurns()))
            repository.resumeEvents.send(ChatStreamEvent.Started("gen-9"))
            repository.resumeEvents.send(ChatStreamEvent.Completed)
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.turns.size)
            assertFalse(viewModel.uiState.value.isStreaming)
        }

    @Test
    fun `들어온 방에 재생성이 생성 중이면 그 턴 자리에서 이어받는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            repository.queuedChatDetailResults +=
                DomainResult.Success(
                    detail(generating = GeneratingTurn("gen-9", userInput = "문을 연다.", regeneratingTurnId = 1)),
                )
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            assertEquals(1L, viewModel.uiState.value.regeneratingTurnId)
            assertTrue(viewModel.uiState.value.isStreaming)
        }

    @Test
    fun `다른 곳에서 생성 중이라 거절되면 입력을 되돌리고 그 생성을 이어받는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = sending(repository)
            repository.queuedChatDetailResults += DomainResult.Success(detail(generating = continuing("gen-3")))
            repository.streamEvents.send(ChatStreamEvent.Failed(DomainError.Server(409, null, null)))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("문을 연다", state.composer.toUserInput())
            assertEquals(listOf("gen-3"), repository.resumedGenerationIds)
            assertEquals("문을 민다", state.streaming?.userInput)
            assertTrue(state.isStreaming)
            assertNoEffect(viewModel)
        }

    @Test
    fun `생성을 시작하지 못하면 입력을 되돌리고 실패를 알린다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = sending(repository)
            repository.streamEvents.send(ChatStreamEvent.Failed(DomainError.Server(503, null, null)))
            advanceUntilIdle()

            assertEquals(
                "문을 연다",
                viewModel.uiState.value.composer
                    .toUserInput(),
            )
            assertFalse(viewModel.uiState.value.isStreaming)
            assertEquals(ChatRoomEffect.ShowStreamFailure(null), viewModel.uiEffect.first())
        }

    @Test
    fun `백그라운드로 가면 수신을 멈추고 돌아오면 아직 생성 중인 생성을 다시 받는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = sending(repository)
            repository.streamEvents.send(ChatStreamEvent.Started("gen-1"))
            advanceUntilIdle()

            viewModel.onIntent(ChatRoomIntent.Backgrounded)
            advanceUntilIdle()
            // 멈춘 수신은 끊김을 받아도 다시 붙지 않는다.
            repository.streamEvents.send(ChatStreamEvent.Interrupted)
            advanceUntilIdle()
            assertEquals(emptyList<String>(), repository.resumedGenerationIds)

            repository.queuedChatDetailResults += DomainResult.Success(detail(generating = continuing("gen-1")))
            viewModel.onIntent(ChatRoomIntent.Foregrounded)
            advanceUntilIdle()

            assertEquals(listOf("gen-1"), repository.resumedGenerationIds)
            assertTrue(viewModel.uiState.value.isStreaming)
            assertNoEffect(viewModel)
        }

    @Test
    fun `백그라운드에 있는 동안 끝났으면 돌아와서 안내 없이 저장된 턴을 보인다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = sending(repository)
            repository.streamEvents.send(ChatStreamEvent.Started("gen-1"))
            advanceUntilIdle()
            viewModel.onIntent(ChatRoomIntent.Backgrounded)
            advanceUntilIdle()

            repository.queuedChatDetailResults += DomainResult.Success(detail(twoTurns()))
            viewModel.onIntent(ChatRoomIntent.Foregrounded)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, state.turns.size)
            assertNull(state.streaming)
            assertFalse(state.isStreaming)
            assertNoEffect(viewModel)
        }

    @Test
    fun `받는 중이 아니면 백그라운드를 오가도 아무것도 하지 않는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            val reads = repository.chatDetailIds.size

            viewModel.onIntent(ChatRoomIntent.Backgrounded)
            viewModel.onIntent(ChatRoomIntent.Foregrounded)
            advanceUntilIdle()

            assertEquals(reads, repository.chatDetailIds.size)
        }

    private suspend fun assertNoEffect(viewModel: ChatRoomViewModel) {
        assertNull(withTimeoutOrNull(100) { viewModel.uiEffect.first() })
    }

    private fun viewModel(repository: FakeChatRepository) =
        ChatRoomViewModel(
            chatId = "chat-1",
            chatRepository = repository,
            reportRepository = FakeReportRepository(),
            preferences = FakeChatPreferencesRepository(),
            trialsRepository = FakeTrialsRepository(),
            creditPolicyRepository = FakeCreditPolicyRepository(),
            profileRepository = FakeUserProfileRepository(),
            attendance = FakeAttendanceClaimer(),
            analytics = NoOpAnalytics,
        )

    /** "문을 연다"를 보내 스트림이 열린 상태. */
    private fun TestScope.sending(repository: FakeChatRepository): ChatRoomViewModel {
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.onIntent(ChatRoomIntent.BlockAdded(InputBlockType.DIALOGUE))
        viewModel.onIntent(ChatRoomIntent.BlockValueChanged(id = 3, value = "문을 연다"))
        advanceUntilIdle()
        viewModel.onIntent(ChatRoomIntent.Sent)
        advanceUntilIdle()
        return viewModel
    }

    private fun continuing(generationId: String) =
        GeneratingTurn(generationId, userInput = "문을 민다", regeneratingTurnId = null)

    private fun detail(
        turns: List<ChatTurn> = sampleChatDetail().turns,
        generating: GeneratingTurn? = null,
    ): ChatDetail = sampleChatDetail(turns = turns).copy(generatingTurn = generating)

    private fun twoTurns(): List<ChatTurn> =
        sampleChatDetail().turns + ChatTurn(id = 2, userInput = "문을 연다", aiOutput = "문이 열린다")
}
