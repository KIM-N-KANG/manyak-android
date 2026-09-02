package app.manyak.feature.chat

import app.manyak.core.domain.chat.ChatStreamEvent
import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.feature.chat.composer.InputBlockType
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRoomDeleteTest {
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
    fun `삭제를 확정하면 진행 중인 스트림을 끊고 지운다`() =
        runTest(dispatcher) {
            // 지운 채팅에 턴을 계속 붙이면 그 실패 안내가 삭제 안내와 겹쳐 뜬다.
            val repository = FakeChatRepository()
            val viewModel = streaming(repository)

            viewModel.onIntent(ChatRoomIntent.DeleteConfirmed)
            advanceUntilIdle()

            assertEquals(listOf("chat-1"), repository.deletedChatIds)
            assertEquals(ChatRoomEffect.ChatDeleted, withTimeoutOrNull(1_000) { viewModel.uiEffect.first() })
        }

    @Test
    fun `삭제하는 동안 확인 버튼을 잠근다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = loaded(repository)

            viewModel.onIntent(ChatRoomIntent.DeleteConfirmed)
            advanceUntilIdle()

            // 응답 뒤에도 화면은 이동 중이라 잠금을 그대로 둔다.
            assertTrue(viewModel.uiState.value.isDeleting)
        }

    @Test
    fun `삭제에 실패하면 잠금을 풀고 안내한다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            repository.queuedDeleteResults += DomainResult.Failure(DomainError.Network)
            val viewModel = loaded(repository)

            viewModel.onIntent(ChatRoomIntent.DeleteConfirmed)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isDeleting)
            assertEquals(
                ChatRoomEffect.ShowDeleteFailed,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `연타해도 삭제 요청이 하나다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = loaded(repository)

            viewModel.onIntent(ChatRoomIntent.DeleteConfirmed)
            viewModel.onIntent(ChatRoomIntent.DeleteConfirmed)
            advanceUntilIdle()

            assertEquals(listOf("chat-1"), repository.deletedChatIds)
        }

    @Test
    fun `402 는 낙관적 밴드를 걷고 이프 안내를 올린다`() =
        runTest(dispatcher) {
            // 앱은 로그인 필수라 402 의 사유가 이프 부족 하나뿐이다.
            val repository = FakeChatRepository()
            val viewModel = streaming(repository)

            repository.streamEvents.send(
                ChatStreamEvent.Failed(DomainError.Server(status = 402, code = null, requestId = null), null),
            )
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.streaming)
            assertFalse(viewModel.uiState.value.isStreaming)
            assertEquals(
                ChatRoomEffect.ShowCreditRequired,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    private fun viewModel(repository: FakeChatRepository) =
        ChatRoomViewModel(
            chatId = "chat-1",
            chatRepository = repository,
            storyRepository = FakeStoryRepository(),
            preferences = FakeChatPreferencesRepository(),
        )

    private fun kotlinx.coroutines.test.TestScope.loaded(repository: FakeChatRepository): ChatRoomViewModel {
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        return viewModel
    }

    private fun kotlinx.coroutines.test.TestScope.streaming(repository: FakeChatRepository): ChatRoomViewModel {
        val viewModel = loaded(repository)
        viewModel.onIntent(ChatRoomIntent.BlockAdded(InputBlockType.DIALOGUE))
        viewModel.onIntent(ChatRoomIntent.BlockValueChanged(id = 3, value = "문을 연다"))
        advanceUntilIdle()
        viewModel.onIntent(ChatRoomIntent.Sent)
        advanceUntilIdle()
        return viewModel
    }
}
