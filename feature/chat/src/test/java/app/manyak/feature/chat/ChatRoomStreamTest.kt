package app.manyak.feature.chat

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.common.domain.chat.ChatInputMode
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.chat.ChatDetail
import app.manyak.common.entity.chat.ChatStreamEvent
import app.manyak.common.entity.chat.ChatTurn
import app.manyak.feature.chat.composer.InputBlockType
import app.manyak.feature.chat.message.ChatMessageSegment
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRoomStreamTest {
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
    fun `보내면 컴포저를 비우고 낙관적 밴드를 붙인다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.type("문을 연다")
            advanceUntilIdle()
            viewModel.onIntent(ChatRoomIntent.Sent)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf("문을 연다"), repository.streamedInputs)
            assertTrue(state.isStreaming)
            assertEquals("문을 연다", state.streaming?.userInput)
            // 첫 표시 사건이 오기 전에는 조각이 없어 화면이 "작성 중"을 그린다.
            assertEquals(emptyList<ChatMessageSegment>(), state.streaming?.segments)
            assertFalse(state.composer.hasInput)
        }

    @Test
    fun `토큰은 배치 간격마다 한 번씩 이어 붙는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = startStreaming(repository)

            repository.streamEvents.send(ChatStreamEvent.Token("문이 "))
            repository.streamEvents.send(ChatStreamEvent.Token("열린다"))
            advanceUntilIdle()

            assertEquals(
                listOf(ChatMessageSegment.Text("문이 열린다")),
                viewModel.uiState.value.streaming
                    ?.segments,
            )
        }

    @Test
    fun `완료되면 확정 목록과 진행 블록이 한 번에 바뀐다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = startStreaming(repository)

            repository.streamEvents.send(ChatStreamEvent.Token("문이 열린다"))
            repository.queuedChatDetailResults += DomainResult.Success(detailWithTwoTurns())
            repository.streamEvents.send(ChatStreamEvent.Completed)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            // 두 사건으로 나뉘면 한 프레임 동안 화면이 수축한다.
            assertNull(state.streaming)
            assertFalse(state.isStreaming)
            assertEquals(2, state.turns.size)
        }

    @Test
    fun `완료했는데 목록을 못 읽으면 블록을 남기고 잠금만 푼다`() =
        runTest(dispatcher) {
            // 턴은 이미 저장됐으므로 블록을 지우면 방금 받은 이야기가 사라진다.
            val repository = FakeChatRepository()
            val viewModel = startStreaming(repository)

            repository.streamEvents.send(ChatStreamEvent.Token("문이 열린다"))
            repository.queuedChatDetailResults +=
                DomainResult.Failure(app.manyak.common.domain.error.DomainError.Network)
            repository.streamEvents.send(ChatStreamEvent.Completed)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNotNull(state.streaming)
            assertFalse(state.isStreaming)
        }

    @Test
    fun `오류 사건은 낙관적 밴드를 걷고 서버 문구를 올린다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = startStreaming(repository)

            repository.streamEvents.send(
                ChatStreamEvent.Failed(app.manyak.common.domain.error.DomainError.Unknown, "생성에 실패했어요"),
            )
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.streaming)
            assertFalse(viewModel.uiState.value.isStreaming)
            assertEquals(
                ChatRoomEffect.ShowStreamFailure("생성에 실패했어요"),
                withTimeoutOrNull(1_000) { viewModel.uiEffect() },
            )
        }

    @Test
    fun `실패하면 전송하며 비운 입력을 되돌린다`() =
        runTest(dispatcher) {
            // 턴이 열리지 못한 실패라 사용자가 쓴 문장을 없앨 이유가 없다.
            val repository = FakeChatRepository()
            val viewModel = startStreaming(repository)

            repository.streamEvents.send(
                ChatStreamEvent.Failed(app.manyak.common.domain.error.DomainError.Network, null),
            )
            advanceUntilIdle()

            assertEquals(
                "문을 연다",
                viewModel.uiState.value.composer
                    .toUserInput(),
            )
        }

    @Test
    fun `종단 사건 없이 끊기면 블록을 걷고 확정 상태를 다시 읽는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = startStreaming(repository)

            repository.queuedChatDetailResults += DomainResult.Success(detailWithTwoTurns())
            repository.streamEvents.send(ChatStreamEvent.Interrupted)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.streaming)
            assertFalse(state.isStreaming)
            // 서버 저장 여부가 불명이라 임의 복원 대신 다시 읽은 결과를 쓴다. 보낸 문장도
            // 이미 붙었을 수 있어 컴포저로 되돌리지 않는다.
            assertEquals(2, state.turns.size)
            assertFalse(state.composer.hasInput)
        }

    @Test
    fun `보내는 중에 다시 보내도 요청이 하나다`() =
        runTest(dispatcher) {
            // 버튼 잠금에만 기대면 연타와 접근성 서비스의 반복 클릭을 막지 못한다.
            val repository = FakeChatRepository()
            val viewModel = startStreaming(repository)

            viewModel.type("한 번 더")
            advanceUntilIdle()
            viewModel.onIntent(ChatRoomIntent.Sent)
            advanceUntilIdle()

            assertEquals(listOf("문을 연다"), repository.streamedInputs)
        }

    @Test
    fun `입력 모드를 바꾸면 저장하고 쓰던 내용을 옮긴다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val preferences = FakeChatPreferencesRepository()
            val viewModel = viewModel(repository, preferences)
            advanceUntilIdle()

            viewModel.type("*문이 열린다* 누구세요?")
            advanceUntilIdle()
            viewModel.onIntent(ChatRoomIntent.InputModeChanged(ChatInputMode.PLAIN))
            advanceUntilIdle()

            assertEquals(listOf(ChatInputMode.PLAIN), preferences.savedModes)
            assertEquals("*문이 열린다* 누구세요?", viewModel.uiState.value.composer.plainText)
        }

    @Test
    fun `응답 생성 중에 잠긴 입력창을 누르면 안내 효과를 낸다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = startStreaming(repository)

            viewModel.onIntent(ChatRoomIntent.LockedComposerTapped)
            advanceUntilIdle()

            assertEquals(ChatRoomEffect.ShowComposerLocked, viewModel.uiEffect())
        }

    @Test
    fun `생성 중이 아니면 입력창 탭은 아무 효과도 내지 않는다`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeChatRepository())
            advanceUntilIdle()

            viewModel.onIntent(ChatRoomIntent.LockedComposerTapped)
            advanceUntilIdle()

            assertNull(withTimeoutOrNull(100) { viewModel.uiEffect() })
        }

    private fun viewModel(
        repository: FakeChatRepository,
        preferences: FakeChatPreferencesRepository = FakeChatPreferencesRepository(),
    ) = ChatRoomViewModel(
        chatId = "chat-1",
        chatRepository = repository,
        reportRepository = FakeReportRepository(),
        preferences = preferences,
        analytics = NoOpAnalytics,
    )

    /** 블럭 하나에 문장을 넣는다. 기본 모드가 블럭이라 첫 칸이 상황이다. */
    private fun ChatRoomViewModel.type(text: String) {
        onIntent(ChatRoomIntent.BlockAdded(InputBlockType.DIALOGUE))
        onIntent(ChatRoomIntent.BlockValueChanged(id = 3, value = text))
    }

    private suspend fun ChatRoomViewModel.uiEffect(): ChatRoomEffect = uiEffect.first()

    private fun kotlinx.coroutines.test.TestScope.startStreaming(repository: FakeChatRepository): ChatRoomViewModel {
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.type("문을 연다")
        advanceUntilIdle()
        viewModel.onIntent(ChatRoomIntent.Sent)
        advanceUntilIdle()
        return viewModel
    }

    private fun detailWithTwoTurns(): ChatDetail =
        sampleChatDetail().copy(
            turns =
                listOf(
                    ChatTurn(id = 1, userInput = "문을 연다.", aiOutput = "문이 열리자 태엽 소리가 쏟아진다."),
                    ChatTurn(id = 2, userInput = "문을 연다", aiOutput = "문이 열린다"),
                ),
        )
}
