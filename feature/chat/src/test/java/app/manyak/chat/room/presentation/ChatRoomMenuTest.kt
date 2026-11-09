package app.manyak.chat.room.presentation

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.chat.testing.FakeChatPreferencesRepository
import app.manyak.chat.testing.FakeChatRepository
import app.manyak.chat.testing.FakeCreditTransactionReader
import app.manyak.chat.testing.FakeReportRepository
import app.manyak.chat.testing.FakeUserProfileRepository
import app.manyak.chat.testing.sampleChatDetail
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.credit.CreditTransaction
import app.manyak.common.entity.credit.CreditTransactionPage
import app.manyak.common.entity.credit.CreditTransactionReason
import app.manyak.common.entity.credit.CreditTransactionType
import app.manyak.testing.FakeAttendanceClaimer
import app.manyak.testing.FakeCreditPolicyRepository
import app.manyak.testing.FakeTrialsRepository
import app.manyak.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 헤더 메뉴 시트의 새 채팅 시작하기, 공유하기, 내 이프 카드와 이프 내역. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRoomMenuTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

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

    @Test
    fun `내역을 열면 첫 페이지를 보이고 돌아가면 메뉴로, 다시 열면 첫 페이지부터 읽는다`() =
        runTest(dispatcher) {
            val credits = FakeCreditTransactionReader(pages = mapOf(null to page("a", next = "c1")))
            val viewModel = loaded(FakeChatRepository(), credits = credits)

            viewModel.onIntent(ChatRoomIntent.CreditHistoryOpened)
            advanceUntilIdle()
            assertEquals(
                listOf("a"),
                viewModel.uiState.value.creditHistory
                    ?.items
                    ?.map { it.title },
            )

            viewModel.onIntent(ChatRoomIntent.CreditHistoryClosed)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.creditHistory)

            viewModel.onIntent(ChatRoomIntent.CreditHistoryOpened)
            advanceUntilIdle()
            assertEquals(listOf<String?>(null, null), credits.requests)
        }

    @Test
    fun `목록 끝에서 다음 커서로 이어 붙이고 실패하면 보던 목록을 남긴다`() =
        runTest(dispatcher) {
            val credits =
                FakeCreditTransactionReader(
                    pages = mapOf(null to page("a", next = "c1"), "c1" to page("b", next = "c2")),
                )
            val viewModel = loaded(FakeChatRepository(), credits = credits)
            viewModel.onIntent(ChatRoomIntent.CreditHistoryOpened)
            advanceUntilIdle()

            viewModel.onIntent(ChatRoomIntent.CreditHistoryLoadMore)
            advanceUntilIdle()
            viewModel.onIntent(ChatRoomIntent.CreditHistoryLoadMore)
            advanceUntilIdle()

            val history = viewModel.uiState.value.creditHistory
            assertEquals(listOf<String?>(null, "c1", "c2"), credits.requests)
            assertEquals(listOf("a", "b"), history?.items?.map { it.title })
            assertTrue(history?.loadMoreFailed == true)
            assertFalse(history?.canLoadMore == true)
        }

    @Test
    fun `새로고침은 응답까지 목록을 두고 뒷 페이지를 버린 첫 페이지로 바꾼다`() =
        runTest(dispatcher) {
            val credits =
                FakeCreditTransactionReader(
                    pages = mapOf(null to page("a", next = "c1"), "c1" to page("b", next = null)),
                )
            val viewModel = loaded(FakeChatRepository(), credits = credits)
            viewModel.onIntent(ChatRoomIntent.CreditHistoryOpened)
            advanceUntilIdle()
            viewModel.onIntent(ChatRoomIntent.CreditHistoryLoadMore)
            advanceUntilIdle()

            val gate = CompletableDeferred<Unit>()
            credits.gate = gate
            credits.pages = mapOf(null to page("new", next = "c2"))
            viewModel.onIntent(ChatRoomIntent.CreditHistoryRefresh)
            advanceUntilIdle()
            val refreshing = viewModel.uiState.value.creditHistory
            assertTrue(refreshing?.isRefreshing == true)
            assertEquals(listOf("a", "b"), refreshing?.items?.map { it.title })
            assertFalse(refreshing?.canLoadMore == true)

            gate.complete(Unit)
            advanceUntilIdle()
            val refreshed = viewModel.uiState.value.creditHistory
            assertEquals(listOf<String?>(null, "c1", null), credits.requests)
            assertEquals(listOf("new"), refreshed?.items?.map { it.title })
            assertEquals("c2", refreshed?.nextCursor)
            assertFalse(refreshed?.isRefreshing == true)
        }

    @Test
    fun `닫은 뒤 늦게 온 응답은 내역을 되살리지 않는다`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val credits = FakeCreditTransactionReader(pages = mapOf(null to page("a", next = null)), gate = gate)
            val viewModel = loaded(FakeChatRepository(), credits = credits)

            viewModel.onIntent(ChatRoomIntent.CreditHistoryOpened)
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.creditHistory
                    ?.isLoading == true,
            )

            viewModel.onIntent(ChatRoomIntent.CreditHistoryClosed)
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.creditHistory)
        }

    private fun page(
        title: String,
        next: String?,
    ) = DomainResult.Success(
        CreditTransactionPage(
            items =
                listOf(
                    CreditTransaction(
                        type = CreditTransactionType.SPEND,
                        reason = CreditTransactionReason.CHAT_TURN,
                        amount = 20,
                        title = title,
                        expiresDate = null,
                        createdDate = "2026-11-04",
                    ),
                ),
            nextCursor = next,
        ),
    )

    private fun TestScope.loaded(
        repository: FakeChatRepository,
        profiles: FakeUserProfileRepository = FakeUserProfileRepository(),
        credits: FakeCreditTransactionReader = FakeCreditTransactionReader(),
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
                attendance = FakeAttendanceClaimer(),
                creditTransactions = credits,
                analytics = NoOpAnalytics,
            )
        advanceUntilIdle()
        return viewModel
    }
}
