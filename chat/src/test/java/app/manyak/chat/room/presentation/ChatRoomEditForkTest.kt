package app.manyak.chat.room.presentation

import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.chat.entity.ChatStreamEvent
import app.manyak.chat.entity.ChatTurn
import app.manyak.chat.entity.GeneratingTurn
import app.manyak.chat.testing.FakeAttendanceClaimer
import app.manyak.chat.testing.FakeChatPreferencesRepository
import app.manyak.chat.testing.FakeChatRepository
import app.manyak.chat.testing.FakeCreditPolicyRepository
import app.manyak.chat.testing.FakeCreditTransactionReader
import app.manyak.chat.testing.FakeReportRepository
import app.manyak.chat.testing.FakeTrialsRepository
import app.manyak.chat.testing.FakeUserProfileRepository
import app.manyak.chat.testing.sampleChatDetail
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
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
class ChatRoomEditForkTest {
    private val dispatcher = StandardTestDispatcher()
    private val tracked = mutableListOf<AnalyticsEvent>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `입력 수정 버튼은 원래 입력으로 편집기를 열고 클릭을 그 턴 번호로 센다`() =
        runTest(dispatcher) {
            val viewModel = loaded(FakeChatRepository(), twoTurns())

            viewModel.onIntent(ChatRoomIntent.EditRequested(turnId = 2))
            advanceUntilIdle()

            assertEquals(TurnEditing(turnId = 2, draft = "더 나아간다."), viewModel.uiState.value.editing)
            assertTrue(AnalyticsEvent.ChatEditButtonClicked("chat-1", turnNumber = 2) in tracked)
        }

    @Test
    fun `원래 입력과 같거나 공백만 있으면 확인 창을 열지 않는다`() =
        runTest(dispatcher) {
            val viewModel = editing(FakeChatRepository(), twoTurns(), turnId = 1)

            viewModel.onIntent(ChatRoomIntent.EditDraftChanged("  문을 연다.  "))
            viewModel.onIntent(ChatRoomIntent.EditSubmitted)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.turnConfirm)

            viewModel.onIntent(ChatRoomIntent.EditDraftChanged("   "))
            viewModel.onIntent(ChatRoomIntent.EditSubmitted)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.turnConfirm)
        }

    @Test
    fun `취소하면 요청 없이 편집기를 닫는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = editing(repository, twoTurns(), turnId = 1)

            viewModel.onIntent(ChatRoomIntent.EditDraftChanged("문을 두드린다."))
            viewModel.onIntent(ChatRoomIntent.EditCancelled)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.editing)
            assertEquals(emptyList<Triple<Long, String, Boolean>>(), repository.editRequests)
        }

    @Test
    fun `뒤에 턴이 있으면 사라질 턴 수를 알리고 진행 중에는 대상 턴까지만 보인다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = editing(repository, twoTurns(), turnId = 1)

            viewModel.onIntent(ChatRoomIntent.EditDraftChanged("  문을 두드린다.  "))
            viewModel.onIntent(ChatRoomIntent.EditSubmitted)
            advanceUntilIdle()
            assertEquals(
                TurnConfirm.Edit(turnId = 1, userInput = "문을 두드린다.", laterTurnCount = 1),
                viewModel.uiState.value.turnConfirm,
            )

            viewModel.onIntent(ChatRoomIntent.TurnConfirmAccepted)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(Triple(1L, "문을 두드린다.", false)), repository.editRequests)
            assertEquals(1L, state.replacingTurnId)
            assertTrue(state.hidesLaterTurns)
            assertEquals("문을 두드린다.", state.streaming?.userInput)
            // 입력 수정은 메시지 전송이 아니다.
            assertTrue(tracked.none { event -> event is AnalyticsEvent.MessageInputSubmitted })
        }

    @Test
    fun `완료되면 고친 턴부터 새 턴 하나로 바뀌고 편집기를 닫는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = editStreaming(repository)

            repository.queuedChatDetailResults += DomainResult.Success(sampleChatDetail(turns = listOf(newTurn())))
            repository.editEvents.send(ChatStreamEvent.Started("gen-1"))
            repository.editEvents.send(ChatStreamEvent.Completed)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(3L), state.turns.map { turn -> turn.id })
            assertNull(state.editing)
            assertNull(state.replacingTurnId)
            assertFalse(state.hidesLaterTurns)
            assertFalse(state.isStreaming)
        }

    @Test
    fun `서버가 실패로 끝내면 숨긴 턴과 고치던 입력이 돌아온다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = editStreaming(repository)

            repository.editEvents.send(ChatStreamEvent.Started("gen-1"))
            repository.editEvents.send(ChatStreamEvent.Failed(DomainError.Unknown, null))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(1L, 2L), state.turns.map { turn -> turn.id })
            assertFalse(state.hidesLaterTurns)
            assertEquals(TurnEditing(turnId = 1, draft = EDITED_INPUT), state.editing)
            assertEquals(ChatRoomEffect.ShowStreamFailure(null), viewModel.uiEffect.first())
        }

    @Test
    fun `이프 부족으로 거절되면 시트를 열고 고치던 입력을 둔다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = editStreaming(repository)

            repository.editEvents.send(
                ChatStreamEvent.Failed(DomainError.Server(status = 402, code = null, requestId = null), null),
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.creditShortage != null)
            assertEquals(TurnEditing(turnId = 1, draft = EDITED_INPUT), state.editing)
            assertEquals(2, state.turns.size)
        }

    @Test
    fun `끝내 받지 못해도 대상 턴이 숨겨졌으면 실패 안내 없이 완료다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = editStreaming(repository)

            repository.queuedChatDetailResults += DomainResult.Success(sampleChatDetail(turns = listOf(newTurn())))
            repository.editEvents.send(ChatStreamEvent.Interrupted)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(3L), state.turns.map { turn -> turn.id })
            assertNull(state.editing)
            assertNull(withTimeoutOrNull(100) { viewModel.uiEffect.first() })
        }

    @Test
    fun `끝내 받지 못하고 대상 턴이 남아 있으면 실패를 알리고 편집기를 둔다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = editStreaming(repository)

            repository.editEvents.send(ChatStreamEvent.Interrupted)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(1L, 2L), state.turns.map { turn -> turn.id })
            assertEquals(TurnEditing(turnId = 1, draft = EDITED_INPUT), state.editing)
            assertEquals(ChatRoomEffect.ShowStreamFailure(null), viewModel.uiEffect.first())
        }

    @Test
    fun `마지막 턴을 고치면 응답만 바뀐다고 확인한다`() =
        runTest(dispatcher) {
            val viewModel = editing(FakeChatRepository(), twoTurns(), turnId = 2)

            viewModel.onIntent(ChatRoomIntent.EditDraftChanged("물러난다."))
            viewModel.onIntent(ChatRoomIntent.EditSubmitted)
            advanceUntilIdle()

            assertEquals(
                TurnConfirm.Edit(turnId = 2, userInput = "물러난다.", laterTurnCount = 0),
                viewModel.uiState.value.turnConfirm,
            )
        }

    @Test
    fun `들어온 방에서 입력 수정이 생성 중이면 대상 자리에서 이어받고 뒤 턴을 숨긴다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val generating = GeneratingTurn("gen-9", userInput = EDITED_INPUT, targetTurnId = 1, isEdit = true)
            repository.queuedChatDetailResults +=
                DomainResult.Success(sampleChatDetail(turns = twoTurns()).copy(generatingTurn = generating))
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf("gen-9"), repository.resumedGenerationIds)
            assertEquals(1L, state.replacingTurnId)
            assertTrue(state.hidesLaterTurns)
            assertEquals(EDITED_INPUT, state.streaming?.userInput)
        }

    @Test
    fun `포크는 확인 뒤 새 채팅으로 이동하고 잠금을 푼다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = loaded(repository, twoTurns())

            viewModel.onIntent(ChatRoomIntent.ForkRequested(turnId = 1))
            advanceUntilIdle()
            assertEquals(TurnConfirm.Fork(turnId = 1), viewModel.uiState.value.turnConfirm)
            assertTrue(AnalyticsEvent.ChatForkButtonClicked("chat-1", turnNumber = 1) in tracked)
            assertEquals(emptyList<Long>(), repository.forkedTurnIds)

            viewModel.onIntent(ChatRoomIntent.TurnConfirmAccepted)
            advanceUntilIdle()

            assertEquals(listOf(1L), repository.forkedTurnIds)
            assertEquals(ChatRoomEffect.OpenChat("chat-forked"), viewModel.uiEffect.first())
            assertFalse(viewModel.uiState.value.isForking)
            // 원래 채팅은 그대로다.
            assertEquals(2, viewModel.uiState.value.turns.size)
        }

    @Test
    fun `포크가 실패하면 지금 채팅에 머물고 알린다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            repository.queuedForkResults +=
                DomainResult.Failure(DomainError.Server(status = 404, code = null, requestId = null))
            val viewModel = loaded(repository, twoTurns())

            viewModel.onIntent(ChatRoomIntent.ForkRequested(turnId = 2))
            advanceUntilIdle()
            viewModel.onIntent(ChatRoomIntent.TurnConfirmAccepted)
            advanceUntilIdle()

            assertEquals(ChatRoomEffect.ShowForkFailed, viewModel.uiEffect.first())
            assertFalse(viewModel.uiState.value.isForking)
        }

    @Test
    fun `생성 중에는 입력 수정과 포크를 받지 않는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = editStreaming(repository)

            viewModel.onIntent(ChatRoomIntent.EditRequested(turnId = 2))
            viewModel.onIntent(ChatRoomIntent.ForkRequested(turnId = 1))
            advanceUntilIdle()

            assertEquals(TurnEditing(turnId = 1, draft = EDITED_INPUT), viewModel.uiState.value.editing)
            assertNull(viewModel.uiState.value.turnConfirm)
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
            creditTransactions = FakeCreditTransactionReader(),
            analytics =
                object : Analytics {
                    override fun track(event: AnalyticsEvent) {
                        tracked += event
                    }
                },
        )

    private fun TestScope.loaded(
        repository: FakeChatRepository,
        turns: List<ChatTurn>,
    ): ChatRoomViewModel {
        repository.queuedChatDetailResults += DomainResult.Success(sampleChatDetail(turns = turns))
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        return viewModel
    }

    private fun TestScope.editing(
        repository: FakeChatRepository,
        turns: List<ChatTurn>,
        turnId: Long,
    ): ChatRoomViewModel {
        val viewModel = loaded(repository, turns)
        viewModel.onIntent(ChatRoomIntent.EditRequested(turnId))
        advanceUntilIdle()
        return viewModel
    }

    /** 두 턴 중 첫 턴의 입력을 고쳐 보낸 방. */
    private fun TestScope.editStreaming(repository: FakeChatRepository): ChatRoomViewModel {
        val viewModel = editing(repository, twoTurns(), turnId = 1)
        viewModel.onIntent(ChatRoomIntent.EditDraftChanged(EDITED_INPUT))
        viewModel.onIntent(ChatRoomIntent.EditSubmitted)
        advanceUntilIdle()
        viewModel.onIntent(ChatRoomIntent.TurnConfirmAccepted)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isStreaming)
        return viewModel
    }

    private fun twoTurns(): List<ChatTurn> =
        listOf(
            ChatTurn(id = 1, userInput = "문을 연다.", aiOutput = "문이 열리자 태엽 소리가 쏟아진다."),
            ChatTurn(id = 2, userInput = "더 나아간다.", aiOutput = "복도가 길어진다."),
        )

    private fun newTurn(): ChatTurn = ChatTurn(id = 3, userInput = EDITED_INPUT, aiOutput = "문 너머에서 대답이 온다.")
}

private const val EDITED_INPUT = "문을 두드린다."
