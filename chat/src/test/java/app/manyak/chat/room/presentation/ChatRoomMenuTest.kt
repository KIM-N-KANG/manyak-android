package app.manyak.chat.room.presentation

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.chat.testing.FakeChatPreferencesRepository
import app.manyak.chat.testing.FakeChatRepository
import app.manyak.chat.testing.FakeCreditPolicyRepository
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

/** 헤더 메뉴 시트의 새 채팅 시작하기·공유하기와 내 이프 카드. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRoomMenuTest {
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
    fun `새 채팅은 이 방의 스토리로 만들고 새 방으로 이동한다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = loaded(repository)

            viewModel.onIntent(ChatRoomIntent.NewChatRequested)
            advanceUntilIdle()

            assertEquals(listOf("story-1"), repository.createdStoryIds)
            assertEquals(
                ChatRoomEffect.NavigateToChat("chat-2"),
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
            // 이동 중에 항목이 되살아나면 두 번째 방이 만들어진다.
            assertTrue(viewModel.uiState.value.isStartingNewChat)
        }

    @Test
    fun `연타해도 새 채팅은 하나만 만든다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = loaded(repository)

            viewModel.onIntent(ChatRoomIntent.NewChatRequested)
            viewModel.onIntent(ChatRoomIntent.NewChatRequested)
            advanceUntilIdle()

            assertEquals(listOf("story-1"), repository.createdStoryIds)
        }

    @Test
    fun `새 채팅에 실패하면 잠금을 풀고 안내한다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            repository.queuedCreateResults += DomainResult.Failure(DomainError.Network)
            val viewModel = loaded(repository)

            viewModel.onIntent(ChatRoomIntent.NewChatRequested)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isStartingNewChat)
            assertEquals(ChatRoomEffect.ShowNewChatFailed, withTimeoutOrNull(1_000) { viewModel.uiEffect.first() })
        }

    @Test
    fun `참조 스토리가 삭제된 방은 새 채팅을 만들지 않는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            repository.queuedChatDetailResults +=
                DomainResult.Success(sampleChatDetail().copy(storyId = "", storyTitle = ""))
            val viewModel = loaded(repository)

            assertFalse(viewModel.uiState.value.hasStory)
            viewModel.onIntent(ChatRoomIntent.NewChatRequested)
            advanceUntilIdle()

            assertTrue(repository.createdStoryIds.isEmpty())
            assertFalse(viewModel.uiState.value.isStartingNewChat)
        }

    @Test
    fun `공유하기는 웹 열람 링크를 발급해 공유 시트로 넘긴다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = loaded(repository)

            viewModel.onIntent(ChatRoomIntent.ShareRequested)
            viewModel.onIntent(ChatRoomIntent.ShareRequested)
            advanceUntilIdle()

            // 발급은 멱등이지만 연타로 요청을 두 번 보내지는 않는다.
            assertEquals(listOf("chat-1"), repository.sharedChatIds)
            // 문구의 턴 수는 발급 시점의 확정 턴 수다 — 샘플 상세는 턴 하나다.
            assertEquals(
                ChatRoomEffect.ShareLink("https://example.com/share/share-1", turnCount = 1),
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
            assertFalse(viewModel.uiState.value.isSharing)
        }

    @Test
    fun `공유 발급에 실패하면 잠금을 풀고 안내한다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            repository.queuedShareResults += DomainResult.Failure(DomainError.Network)
            val viewModel = loaded(repository)

            viewModel.onIntent(ChatRoomIntent.ShareRequested)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isSharing)
            assertEquals(ChatRoomEffect.ShowShareFailed, withTimeoutOrNull(1_000) { viewModel.uiEffect.first() })
        }

    @Test
    fun `메뉴를 열면 프로필을 다시 읽어 잔액을 보인다`() =
        runTest(dispatcher) {
            val profiles = FakeUserProfileRepository(refreshedBalance = 1_130)
            val viewModel = loaded(FakeChatRepository(), profiles)
            assertNull(viewModel.uiState.value.creditBalance)

            viewModel.onIntent(ChatRoomIntent.MenuOpened)
            advanceUntilIdle()

            assertEquals(1, profiles.refreshCount)
            assertEquals(1_130L, viewModel.uiState.value.creditBalance)
        }

    private fun TestScope.loaded(
        repository: FakeChatRepository,
        profiles: FakeUserProfileRepository = FakeUserProfileRepository(),
    ): ChatRoomViewModel {
        val viewModel =
            ChatRoomViewModel(
                chatId = "chat-1",
                chatRepository = repository,
                reportRepository = FakeReportRepository(),
                preferences = FakeChatPreferencesRepository(),
                trialsRepository = FakeTrialsRepository(),
                creditPolicyRepository = FakeCreditPolicyRepository(),
                profileRepository = profiles,
                analytics = NoOpAnalytics,
            )
        advanceUntilIdle()
        return viewModel
    }
}
