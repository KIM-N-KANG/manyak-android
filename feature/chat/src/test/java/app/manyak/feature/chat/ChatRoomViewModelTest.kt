package app.manyak.feature.chat

import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
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
class ChatRoomViewModelTest {
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
    fun `진입 시 상세를 조회해 제목·프롤로그·턴을 상태로 만든다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = ChatRoomViewModel(chatId = "chat-1", chatRepository = repository)
            advanceUntilIdle()

            assertEquals(listOf("chat-1"), repository.chatDetailIds)
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals("두 번째 시계공", state.storyTitle)
            assertEquals("*낡은 시계탑 아래.* 당신은 문 앞에 선다.", state.prologue)
            assertEquals(
                listOf(ChatRoomTurn(id = 1, userInput = "문을 연다.", aiOutput = "문이 열리자 태엽 소리가 쏟아진다.")),
                state.turns,
            )
        }

    @Test
    fun `조회 실패는 실패 상태가 되고 재시도로 다시 조회한다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            repository.queuedChatDetailResults += DomainResult.Failure(DomainError.Network)
            val viewModel = ChatRoomViewModel(chatId = "chat-1", chatRepository = repository)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.loadFailed)
            assertFalse(viewModel.uiState.value.isLoading)

            viewModel.onIntent(ChatRoomIntent.Retry)
            advanceUntilIdle()

            assertEquals(2, repository.chatDetailIds.size)
            assertFalse(viewModel.uiState.value.loadFailed)
            assertEquals("두 번째 시계공", viewModel.uiState.value.storyTitle)
        }
}
