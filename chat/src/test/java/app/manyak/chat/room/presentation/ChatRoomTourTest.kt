package app.manyak.chat.room.presentation

import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.chat.room.presentation.tour.ChatTourStep
import app.manyak.chat.testing.FakeChatPreferencesRepository
import app.manyak.chat.testing.FakeChatRepository
import app.manyak.chat.testing.FakeCreditPolicyRepository
import app.manyak.chat.testing.FakeReportRepository
import app.manyak.chat.testing.FakeTrialsRepository
import app.manyak.chat.testing.FakeUserProfileRepository
import app.manyak.chat.testing.sampleChatDetail
import app.manyak.common.domain.error.DomainResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 첫 진입 안내 투어의 노출 판정·기록과 분석 이벤트. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRoomTourTest {
    private val dispatcher = StandardTestDispatcher()
    private val events = mutableListOf<AnalyticsEvent>()
    private val analytics =
        object : Analytics {
            override fun track(event: AnalyticsEvent) {
                events += event
            }
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `처음 들어온 턴 0개 방은 잠시 뒤 투어를 열고 노출과 열람을 기록한다`() =
        runTest(dispatcher) {
            val preferences = FakeChatPreferencesRepository(tourSeen = false)
            val viewModel = viewModel(newChatRepository(), preferences)
            runCurrent()

            assertFalse(viewModel.uiState.value.tourOpen)

            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.tourOpen)
            assertNull(viewModel.uiState.value.tourStep)
            assertEquals(1, preferences.tourSeenMarkCount)
            assertEquals(1, events.count { event -> event == AnalyticsEvent.ChatTourShown("chat-1") })
        }

    @Test
    fun `이미 본 기기와 대화가 있는 방에서는 열지 않고 기록도 남기지 않는다`() =
        runTest(dispatcher) {
            val seen = FakeChatPreferencesRepository(tourSeen = true)
            val seenRoom = viewModel(newChatRepository(), seen)
            val unseen = FakeChatPreferencesRepository(tourSeen = false)
            val playedRoom = viewModel(FakeChatRepository(), unseen)
            advanceUntilIdle()

            assertFalse(seenRoom.uiState.value.tourOpen)
            assertFalse(playedRoom.uiState.value.tourOpen)
            assertEquals(0, unseen.tourSeenMarkCount)
            assertTrue(events.none { event -> event is AnalyticsEvent.ChatTourShown })
        }

    @Test
    fun `열리기 전에 전송하면 이 방에서는 건너뛰고 다음 채팅을 위해 기록하지 않는다`() =
        runTest(dispatcher) {
            val preferences = FakeChatPreferencesRepository(tourSeen = false)
            val viewModel = viewModel(newChatRepository(), preferences)
            runCurrent()

            viewModel.onIntent(ChatRoomIntent.SuggestionSent(position = 0))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.tourOpen)
            assertEquals(0, preferences.tourSeenMarkCount)
        }

    @Test
    fun `스텝 도달과 건너뛰기를 0부터 센 자리와 대상 식별자로 보낸다`() =
        runTest(dispatcher) {
            val viewModel = viewModel(newChatRepository(), FakeChatPreferencesRepository(tourSeen = false))
            advanceUntilIdle()

            viewModel.onIntent(ChatRoomIntent.TourStepShown(0, ChatTourStep.ADD_BLOCKS))
            advanceUntilIdle()
            assertEquals(0, viewModel.uiState.value.tourStep)

            viewModel.onIntent(ChatRoomIntent.TourStepShown(1, ChatTourStep.SETTINGS))
            advanceUntilIdle()
            viewModel.onIntent(ChatRoomIntent.TourSkipped(1))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.tourOpen)
            assertNull(viewModel.uiState.value.tourStep)
            assertEquals(
                listOf(
                    AnalyticsEvent.ChatTourStepViewed("chat-1", 0, "add-blocks"),
                    AnalyticsEvent.ChatTourStepViewed("chat-1", 1, "settings"),
                    AnalyticsEvent.ChatTourSkipButtonClicked("chat-1", 1),
                ),
                events.filter { event ->
                    event is AnalyticsEvent.ChatTourStepViewed || event is AnalyticsEvent.ChatTourSkipButtonClicked
                },
            )
        }

    @Test
    fun `완료하면 닫고 완주를 보내며 닫힌 뒤의 늦은 조작은 버린다`() =
        runTest(dispatcher) {
            val viewModel = viewModel(newChatRepository(), FakeChatPreferencesRepository(tourSeen = false))
            advanceUntilIdle()

            viewModel.onIntent(ChatRoomIntent.TourCompleted)
            advanceUntilIdle()
            viewModel.onIntent(ChatRoomIntent.TourSkipped(2))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.tourOpen)
            assertTrue(AnalyticsEvent.ChatTourCompleted("chat-1") in events)
            assertTrue(events.none { event -> event is AnalyticsEvent.ChatTourSkipButtonClicked })
        }

    /** 프롤로그와 첫 추천만 있는 새 채팅. */
    private fun newChatRepository() =
        FakeChatRepository().apply {
            queuedChatDetailResults +=
                DomainResult.Success(sampleChatDetail(turns = emptyList(), suggestedInputs = listOf("문을 연다")))
        }

    private fun viewModel(
        repository: FakeChatRepository,
        preferences: FakeChatPreferencesRepository,
    ) = ChatRoomViewModel(
        chatId = "chat-1",
        chatRepository = repository,
        reportRepository = FakeReportRepository(),
        preferences = preferences,
        trialsRepository = FakeTrialsRepository(),
        creditPolicyRepository = FakeCreditPolicyRepository(),
        profileRepository = FakeUserProfileRepository(),
        analytics = analytics,
    )
}
