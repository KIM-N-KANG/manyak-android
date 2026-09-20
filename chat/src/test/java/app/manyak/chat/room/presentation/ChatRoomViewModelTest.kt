package app.manyak.chat.room.presentation

import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.chat.testing.FakeChatPreferencesRepository
import app.manyak.chat.testing.FakeChatRepository
import app.manyak.chat.testing.FakeCreditPolicyRepository
import app.manyak.chat.testing.FakeReportRepository
import app.manyak.chat.testing.FakeTrialsRepository
import app.manyak.chat.testing.FakeUserProfileRepository
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
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
import org.junit.Assert.assertNull
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
            val viewModel =
                ChatRoomViewModel(
                    chatId = "chat-1",
                    chatRepository = repository,
                    reportRepository = FakeReportRepository(),
                    preferences = FakeChatPreferencesRepository(),
                    trialsRepository = FakeTrialsRepository(),
                    creditPolicyRepository = FakeCreditPolicyRepository(),
                    profileRepository = FakeUserProfileRepository(),
                    analytics = NoOpAnalytics,
                )
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
            val viewModel =
                ChatRoomViewModel(
                    chatId = "chat-1",
                    chatRepository = repository,
                    reportRepository = FakeReportRepository(),
                    preferences = FakeChatPreferencesRepository(),
                    trialsRepository = FakeTrialsRepository(),
                    creditPolicyRepository = FakeCreditPolicyRepository(),
                    profileRepository = FakeUserProfileRepository(),
                    analytics = NoOpAnalytics,
                )
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.loadFailed)
            assertFalse(viewModel.uiState.value.isLoading)

            viewModel.onIntent(ChatRoomIntent.Retry)
            advanceUntilIdle()

            assertEquals(2, repository.chatDetailIds.size)
            assertFalse(viewModel.uiState.value.loadFailed)
            assertEquals("두 번째 시계공", viewModel.uiState.value.storyTitle)
        }

    @Test
    fun `인물 뷰어 열기와 닫기는 채팅 상태를 유지하고 유효한 탭만 기록한다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val events = mutableListOf<AnalyticsEvent>()
            val analytics =
                object : Analytics {
                    override fun track(event: AnalyticsEvent) {
                        events += event
                    }
                }
            val viewModel =
                ChatRoomViewModel(
                    "chat-1",
                    repository,
                    reportRepository = FakeReportRepository(),
                    preferences = FakeChatPreferencesRepository(),
                    trialsRepository = FakeTrialsRepository(),
                    creditPolicyRepository = FakeCreditPolicyRepository(),
                    profileRepository = FakeUserProfileRepository(),
                    analytics = analytics,
                )
            advanceUntilIdle()
            val before = viewModel.uiState.value
            val url = "https://cdn.manyak.app/characters/originals/clockmaker.png"
            viewModel.onIntent(ChatRoomIntent.OpenCharacterImage("https://evil.example/a.png"))
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.imageViewerUrl)
            viewModel.onIntent(ChatRoomIntent.OpenCharacterImage(url))
            advanceUntilIdle()
            assertEquals(before.copy(imageViewerUrl = url), viewModel.uiState.value)
            viewModel.onIntent(ChatRoomIntent.CloseImageViewer)
            advanceUntilIdle()
            assertEquals(before, viewModel.uiState.value)
            assertEquals(listOf("chat-1"), repository.chatDetailIds)
            assertEquals(
                listOf(AnalyticsEvent.ChatCharacterImageClicked("chat-1")),
                events.filterIsInstance<AnalyticsEvent.ChatCharacterImageClicked>(),
            )
        }
}
