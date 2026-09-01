package app.manyak.feature.chat

import app.manyak.core.domain.chat.ChatDetail
import app.manyak.core.domain.chat.ChatStreamEvent
import app.manyak.core.domain.chat.ChatTurn
import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.feature.chat.composer.InputBlockType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val ORIGINAL_OUTPUT = "문이 열리자 태엽 소리가 쏟아진다."

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRoomRegenerateTest {
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
    fun `대상 턴 자리를 진행 블록이 대신하고 쓰던 초안은 그대로 남는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = loaded(repository)
            viewModel.type("이어서 쓰던 문장")
            advanceUntilIdle()

            viewModel.onIntent(ChatRoomIntent.RegenerateRequested(turnId = 1))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(1L), repository.regeneratedTurnIds)
            assertEquals(1L, state.regeneratingTurnId)
            // 사용자 입력은 그 턴의 것을 그대로 쓴다 — AI 출력 자리만 바뀐다.
            assertEquals("문을 연다.", state.streaming?.userInput)
            // 재생성은 이어쓰기와 달리 컴포저를 비우지 않는다.
            assertEquals(
                "이어서 쓰던 문장",
                viewModel.uiState.value.composer
                    .toUserInput(),
            )
        }

    @Test
    fun `오류로 실패하면 기존 본문이 돌아온다`() =
        runTest(dispatcher) {
            // 서버가 교체하지 않았음이 보장되는 실패라 다시 읽지 않는다.
            val repository = FakeChatRepository()
            val viewModel = regenerating(repository)
            val readsBefore = repository.chatDetailIds.size

            repository.regenerateEvents.send(
                ChatStreamEvent.Failed(DomainError.Unknown, "생성에 실패했어요"),
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.streaming)
            assertNull(state.regeneratingTurnId)
            assertFalse(state.isStreaming)
            assertEquals(ORIGINAL_OUTPUT, state.turns.single().aiOutput)
            assertEquals(readsBefore, repository.chatDetailIds.size)
        }

    @Test
    fun `재생성 실패는 앞서 보낸 문장을 되돌리지 않는다`() =
        runTest(dispatcher) {
            // 되돌릴 입력은 방금 열지 못한 턴의 것뿐이다 — 이미 보낸 문장이 되살아나면 안 된다.
            val repository = FakeChatRepository()
            val viewModel = loaded(repository)
            viewModel.type("문을 연다")
            advanceUntilIdle()
            viewModel.onIntent(ChatRoomIntent.Sent)
            advanceUntilIdle()
            repository.streamEvents.send(ChatStreamEvent.Completed)
            advanceUntilIdle()

            viewModel.onIntent(ChatRoomIntent.RegenerateRequested(turnId = 1))
            advanceUntilIdle()
            repository.regenerateEvents.send(ChatStreamEvent.Failed(DomainError.Unknown, null))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.composer.hasInput)
        }

    @Test
    fun `409 면 복원하지 않고 확정 상태를 다시 읽는다`() =
        runTest(dispatcher) {
            // 이미 새 턴이 붙은 낡은 화면이라 되살릴 기존 본문이 정본이 아니다.
            val repository = FakeChatRepository()
            val viewModel = regenerating(repository)
            val readsBefore = repository.chatDetailIds.size
            repository.queuedChatDetailResults += DomainResult.Success(detail(twoTurns()))

            repository.regenerateEvents.send(
                ChatStreamEvent.Failed(DomainError.Server(status = 409, code = null, requestId = null), null),
            )
            advanceUntilIdle()

            assertTrue(repository.chatDetailIds.size > readsBefore)
            assertEquals(2, viewModel.uiState.value.turns.size)
            assertNull(viewModel.uiState.value.regeneratingTurnId)
        }

    @Test
    fun `종단 사건 없이 끊기면 확정 상태를 다시 읽는다`() =
        runTest(dispatcher) {
            // 교체 여부가 불명이라 임의 복원 대신 서버 확정본을 쓴다.
            val repository = FakeChatRepository()
            val viewModel = regenerating(repository)
            val readsBefore = repository.chatDetailIds.size
            repository.queuedChatDetailResults += DomainResult.Success(detail(twoTurns()))

            repository.regenerateEvents.send(ChatStreamEvent.Interrupted)
            advanceUntilIdle()

            assertTrue(repository.chatDetailIds.size > readsBefore)
            assertEquals(2, viewModel.uiState.value.turns.size)
            assertNull(viewModel.uiState.value.streaming)
        }

    @Test
    fun `완료되면 서버 확정본으로 바뀐다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = regenerating(repository)

            repository.regenerateEvents.send(ChatStreamEvent.Token("다시 쓴 이야기"))
            repository.queuedChatDetailResults +=
                DomainResult.Success(detail(listOf(turn(aiOutput = "다시 쓴 이야기."))))
            repository.regenerateEvents.send(ChatStreamEvent.Completed)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("다시 쓴 이야기.", state.turns.single().aiOutput)
            assertNull(state.streaming)
            assertNull(state.regeneratingTurnId)
        }

    @Test
    fun `낡은 턴으로 요청하면 아무것도 하지 않는다`() =
        runTest(dispatcher) {
            // 화면이 본 마지막 턴과 지금 마지막 턴이 다른 클릭이다.
            val repository = FakeChatRepository()
            val viewModel = loaded(repository)

            viewModel.onIntent(ChatRoomIntent.RegenerateRequested(turnId = 99))
            advanceUntilIdle()

            assertEquals(emptyList<Long>(), repository.regeneratedTurnIds)
            assertNull(viewModel.uiState.value.streaming)
        }

    @Test
    fun `엔딩 이름은 화면 상태로 올라온다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            repository.queuedChatDetailResults +=
                DomainResult.Success(detail(listOf(turn(reachedEnding = "멈춘 도시"))))
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            assertEquals(
                "멈춘 도시",
                viewModel.uiState.value.turns
                    .single()
                    .reachedEnding,
            )
        }

    private fun viewModel(repository: FakeChatRepository) =
        ChatRoomViewModel(
            chatId = "chat-1",
            chatRepository = repository,
            preferences = FakeChatPreferencesRepository(),
        )

    private fun TestScope.loaded(repository: FakeChatRepository): ChatRoomViewModel {
        repository.queuedChatDetailResults += DomainResult.Success(detail(listOf(turn())))
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        return viewModel
    }

    private fun TestScope.regenerating(repository: FakeChatRepository): ChatRoomViewModel {
        val viewModel = loaded(repository)
        viewModel.onIntent(ChatRoomIntent.RegenerateRequested(turnId = 1))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.streaming)
        return viewModel
    }

    /** 블럭 하나에 문장을 넣는다. 기본 모드가 블럭이라 첫 칸이 상황이다. */
    private fun ChatRoomViewModel.type(text: String) {
        onIntent(ChatRoomIntent.BlockAdded(InputBlockType.DIALOGUE))
        onIntent(ChatRoomIntent.BlockValueChanged(id = 3, value = text))
    }

    private fun detail(turns: List<ChatTurn>): ChatDetail = sampleChatDetail(turns = turns)

    private fun turn(
        aiOutput: String = ORIGINAL_OUTPUT,
        reachedEnding: String? = null,
    ): ChatTurn =
        ChatTurn(
            id = 1,
            userInput = "문을 연다.",
            aiOutput = aiOutput,
            reachedEnding = reachedEnding,
        )

    private fun twoTurns(): List<ChatTurn> =
        listOf(
            turn(),
            ChatTurn(id = 2, userInput = "더 나아간다.", aiOutput = "복도가 길어진다."),
        )
}
