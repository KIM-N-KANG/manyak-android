package app.manyak.feature.chat

import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
class ChatListViewModelTest {
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
    fun `화면이 보이면 조회해 서버가 준 순서 그대로 목록을 만든다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = ChatListViewModel(repository)

            viewModel.onIntent(ChatListIntent.ScreenShown)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.loadFailed)
            // 서버 순서를 다시 정렬하면 방금 진행한 채팅이 맨 위로 오지 않는다.
            assertEquals(listOf("chat-1", "chat-2"), state.chats.map { chat -> chat.id })
        }

    @Test
    fun `이미 그릴 목록이 있는 복귀 갱신은 골격을 다시 깔지 않는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = ChatListViewModel(repository)
            viewModel.onIntent(ChatListIntent.ScreenShown)
            advanceUntilIdle()

            // 두 번째 조회는 응답 전에 멈춰 세워, 그 사이의 상태를 본다.
            repository.myChatsGate = CompletableDeferred()
            viewModel.onIntent(ChatListIntent.ScreenShown)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertEquals(2, viewModel.uiState.value.chats.size)
        }

    @Test
    fun `복귀 갱신이 실패해도 보고 있던 목록을 지우지 않는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = ChatListViewModel(repository)
            viewModel.onIntent(ChatListIntent.ScreenShown)
            advanceUntilIdle()

            repository.queuedMyChatsResults += DomainResult.Failure(DomainError.Network)
            viewModel.onIntent(ChatListIntent.ScreenShown)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.loadFailed)
            assertEquals(2, state.chats.size)
        }

    @Test
    fun `첫 조회 실패는 재시도 화면이 되고 다시 시도로 복구한다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            repository.queuedMyChatsResults += DomainResult.Failure(DomainError.Network)
            val viewModel = ChatListViewModel(repository)

            viewModel.onIntent(ChatListIntent.ScreenShown)
            advanceUntilIdle()

            val failedState = viewModel.uiState.value
            assertTrue(failedState.loadFailed)
            assertFalse(failedState.isLoading)
            assertTrue(failedState.chats.isEmpty())

            viewModel.onIntent(ChatListIntent.Retry)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.loadFailed)
            assertEquals(2, viewModel.uiState.value.chats.size)
        }

    @Test
    fun `당겨서 새로고침 실패는 목록을 남기고 토스트 효과를 낸다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = ChatListViewModel(repository)
            viewModel.onIntent(ChatListIntent.ScreenShown)
            advanceUntilIdle()

            val effects = mutableListOf<ChatListEffect>()
            val collection = launch { viewModel.uiEffect.collect { effect -> effects += effect } }

            repository.queuedMyChatsResults += DomainResult.Failure(DomainError.Network)
            viewModel.onIntent(ChatListIntent.Refresh)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isRefreshing)
            assertFalse(state.loadFailed)
            assertEquals(2, state.chats.size)
            assertEquals(listOf(ChatListEffect.ShowRefreshFailed), effects)

            collection.cancel()
        }

    @Test
    fun `새로고침은 진행 중인 조용한 재조회를 취소하고 시작한다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = ChatListViewModel(repository)
            viewModel.onIntent(ChatListIntent.ScreenShown)
            advanceUntilIdle()

            // 복귀 갱신이 응답을 기다리는 사이에 사용자가 당긴다.
            val gate = CompletableDeferred<Unit>()
            repository.myChatsGate = gate
            viewModel.onIntent(ChatListIntent.ScreenShown)
            advanceUntilIdle()
            assertEquals(2, repository.myChatsCallCount)

            repository.myChatsGate = null
            viewModel.onIntent(ChatListIntent.Refresh)
            advanceUntilIdle()

            // 취소하지 않고 진행 중이라며 돌아가 버리면 당김이 아무 일도 하지 않는다.
            assertEquals(3, repository.myChatsCallCount)
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertFalse(gate.isCompleted)
        }
}
