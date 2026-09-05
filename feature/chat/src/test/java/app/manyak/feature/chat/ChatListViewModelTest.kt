package app.manyak.feature.chat

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.report.entity.StoryReportReason
import app.manyak.report.presentation.StoryReportAction
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
import org.junit.Assert.assertNull
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
            val viewModel = ChatListViewModel(repository, FakeReportRepository(), NoOpAnalytics)

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
            val viewModel = ChatListViewModel(repository, FakeReportRepository(), NoOpAnalytics)
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
            val viewModel = ChatListViewModel(repository, FakeReportRepository(), NoOpAnalytics)
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
            val viewModel = ChatListViewModel(repository, FakeReportRepository(), NoOpAnalytics)

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
            val viewModel = ChatListViewModel(repository, FakeReportRepository(), NoOpAnalytics)
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
            val viewModel = ChatListViewModel(repository, FakeReportRepository(), NoOpAnalytics)
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

    @Test
    fun `길게 눌러 연 시트에서 삭제를 확인하면 그 카드만 목록에서 빠진다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = ChatListViewModel(repository, FakeReportRepository(), NoOpAnalytics)
            viewModel.onIntent(ChatListIntent.ScreenShown)
            advanceUntilIdle()
            val effects = mutableListOf<ChatListEffect>()
            val collection = launch { viewModel.uiEffect.collect { effect -> effects += effect } }
            val target =
                viewModel.uiState.value.chats
                    .first()

            viewModel.onIntent(ChatListIntent.OpenOptions(target))
            advanceUntilIdle()
            viewModel.onIntent(ChatListIntent.RequestDelete)
            advanceUntilIdle()
            // 삭제하기는 시트를 닫고 확인을 묻는다 — 시트 위에 다이얼로그가 겹치지 않는다.
            assertNull(viewModel.uiState.value.optionsTarget)
            assertEquals(target, viewModel.uiState.value.deleteTarget)

            viewModel.onIntent(ChatListIntent.ConfirmDelete)
            advanceUntilIdle()

            assertEquals(listOf(target.id), repository.deletedChatIds)
            assertEquals(
                listOf("chat-2"),
                viewModel.uiState.value.chats
                    .map { chat -> chat.id },
            )
            assertNull(viewModel.uiState.value.deleteTarget)
            assertEquals(listOf(ChatListEffect.ShowChatDeleted), effects)
            // 재조회 없이 로컬에서 뺀다.
            assertEquals(1, repository.myChatsCallCount)

            collection.cancel()
        }

    @Test
    fun `삭제 실패는 목록을 그대로 두고 실패를 알린다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            repository.queuedDeleteResults += DomainResult.Failure(DomainError.Unknown)
            val viewModel = ChatListViewModel(repository, FakeReportRepository(), NoOpAnalytics)
            viewModel.onIntent(ChatListIntent.ScreenShown)
            advanceUntilIdle()
            val effects = mutableListOf<ChatListEffect>()
            val collection = launch { viewModel.uiEffect.collect { effect -> effects += effect } }

            viewModel.onIntent(
                ChatListIntent.OpenOptions(
                    viewModel.uiState.value.chats
                        .first(),
                ),
            )
            advanceUntilIdle()
            viewModel.onIntent(ChatListIntent.RequestDelete)
            advanceUntilIdle()
            viewModel.onIntent(ChatListIntent.ConfirmDelete)
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.chats.size)
            assertFalse(viewModel.uiState.value.isDeleting)
            assertEquals(listOf(ChatListEffect.ShowChatDeleteFailed), effects)

            collection.cancel()
        }

    @Test
    fun `시트에서 연 신고는 그 카드가 참조하는 스토리로 나간다`() =
        runTest(dispatcher) {
            val storyRepository = FakeReportRepository()
            val viewModel = ChatListViewModel(FakeChatRepository(), storyRepository, NoOpAnalytics)
            viewModel.onIntent(ChatListIntent.ScreenShown)
            advanceUntilIdle()
            val target =
                viewModel.uiState.value.chats
                    .last()

            viewModel.onIntent(ChatListIntent.OpenOptions(target))
            advanceUntilIdle()
            viewModel.onIntent(ChatListIntent.Report(StoryReportAction.Open))
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.optionsTarget)
            assertTrue(viewModel.uiState.value.report.isSheetOpen)

            viewModel.onIntent(ChatListIntent.Report(StoryReportAction.SelectReason(StoryReportReason.SPAM)))
            advanceUntilIdle()
            viewModel.onIntent(ChatListIntent.Report(StoryReportAction.Submit))
            advanceUntilIdle()

            assertEquals(target.storyId, storyRepository.reportedStories.single().first)
            assertFalse(viewModel.uiState.value.report.isSheetOpen)
            // 다음 신고가 지난 대상으로 나가지 않도록 시트가 닫히면 대상도 지운다.
            assertNull(viewModel.uiState.value.reportStoryId)
        }
}
