package app.manyak.chat.room.presentation

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.chat.entity.ChatInputMode
import app.manyak.chat.entity.ChatStreamEvent
import app.manyak.chat.testing.FakeChatPreferencesRepository
import app.manyak.chat.testing.FakeChatRepository
import app.manyak.chat.testing.FakeCreditPolicyRepository
import app.manyak.chat.testing.FakeReportRepository
import app.manyak.chat.testing.FakeTrialsRepository
import app.manyak.chat.testing.FakeUserProfileRepository
import app.manyak.common.domain.error.DomainError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRoomRealtimeImageNudgeTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeChatRepository()
    private val preferences = FakeChatPreferencesRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `두 번째 완료 뒤 500ms에 열고 닫은 뒤에는 다시 열지 않는다`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()
            send(model)
            complete()
            advanceUntilIdle()
            assertEquals(1, preferences.completedTurnCount)
            assertFalse(model.uiState.value.settingsOpen)

            send(model)
            complete()
            advanceTimeBy(499)
            runCurrent()
            assertFalse(model.uiState.value.settingsOpen)
            advanceTimeBy(1)
            runCurrent()
            assertTrue(model.uiState.value.settingsOpen)
            assertTrue(model.uiState.value.realtimeImageNudgeOpen)
            model.onIntent(ChatRoomIntent.RealtimeImageNudgeDismissed)
            runCurrent()
            assertTrue(model.uiState.value.settingsOpen)
            assertFalse(model.uiState.value.realtimeImageNudgeOpen)
            model.onIntent(ChatRoomIntent.SettingsClosed)
            send(model)
            complete()
            advanceUntilIdle()
            assertEquals(3, preferences.completedTurnCount)
            assertFalse(model.uiState.value.settingsOpen)
        }

    @Test
    fun `예약 중 다음 전송이 시작되면 그 응답 종료 뒤 다시 기다린다`() =
        runTest(dispatcher) {
            preferences.completedTurnCount = 1
            val model = viewModel()
            advanceUntilIdle()
            send(model)
            complete()
            advanceTimeBy(250)
            send(model)
            advanceTimeBy(1000)
            runCurrent()
            assertFalse(model.uiState.value.settingsOpen)
            complete()
            advanceTimeBy(499)
            runCurrent()
            assertFalse(model.uiState.value.settingsOpen)
            advanceTimeBy(1)
            runCurrent()
            assertTrue(model.uiState.value.realtimeImageNudgeOpen)
        }

    @Test
    fun `두 번째 완료 순간 켜져 있었다면 나중에 꺼도 열지 않는다`() =
        runTest(dispatcher) {
            preferences.completedTurnCount = 1
            preferences.setRealtimeImageEnabled(true)
            val model = viewModel()
            advanceUntilIdle()
            send(model)
            complete()
            model.onIntent(ChatRoomIntent.RealtimeImageEnabledChanged(false))
            advanceUntilIdle()
            send(model)
            complete()
            advanceUntilIdle()
            assertFalse(model.uiState.value.settingsOpen)
        }

    @Test
    fun `재생성과 실패와 중단은 완료 횟수에 더하지 않는다`() =
        runTest(dispatcher) {
            preferences.completedTurnCount = 1
            val model = viewModel()
            advanceUntilIdle()
            model.onIntent(ChatRoomIntent.RegenerateRequested(1))
            runCurrent()
            repository.regenerateEvents.send(ChatStreamEvent.Completed)
            repository.regenerateEvents.close()
            advanceUntilIdle()
            send(model)
            repository.streamEvents.send(ChatStreamEvent.Failed(DomainError.Network, null))
            repository.streamEvents.close()
            advanceUntilIdle()
            repository.streamEvents = Channel(Channel.UNLIMITED)
            send(model)
            repository.streamEvents.send(ChatStreamEvent.Interrupted)
            repository.streamEvents.close()
            advanceUntilIdle()
            assertEquals(1, preferences.completedTurnCount)
            assertFalse(model.uiState.value.settingsOpen)
        }

    @Test
    fun `기록 실패나 방 재진입만으로 안내가 열리지 않는다`() =
        runTest(dispatcher) {
            preferences.completedTurnCount = 1
            preferences.failsRecordingTurn = true
            val model = viewModel()
            advanceUntilIdle()
            send(model)
            complete()
            advanceUntilIdle()
            assertFalse(model.uiState.value.settingsOpen)
            preferences.completedTurnCount = 2
            preferences.failsRecordingTurn = false
            val reopened = viewModel()
            advanceUntilIdle()
            assertFalse(reopened.uiState.value.settingsOpen)
        }

    @Test
    fun `시트를 닫으면 안내도 닫고 잠금 중 설정을 바꿔도 턴 스냅샷은 유지한다`() =
        runTest(dispatcher) {
            preferences.completedTurnCount = 1
            val model = viewModel()
            advanceUntilIdle()
            send(model)
            model.onIntent(ChatRoomIntent.SettingsOpened)
            runCurrent()
            assertTrue(model.uiState.value.settingsOpen)
            assertTrue(model.uiState.value.isStreaming)
            assertEquals(
                false,
                model.uiState.value.streaming
                    ?.realtimeImage,
            )
            complete()
            advanceUntilIdle()
            model.onIntent(ChatRoomIntent.SettingsClosed)
            runCurrent()
            assertFalse(model.uiState.value.realtimeImageNudgeOpen)
            assertFalse(model.uiState.value.settingsOpen)
        }

    @Test
    fun `예약을 미룬 다음 응답이 실패해도 안내는 한 번 열고 실패는 세지 않는다`() =
        runTest(dispatcher) {
            preferences.completedTurnCount = 1
            val model = viewModel()
            advanceUntilIdle()
            send(model)
            complete()
            advanceTimeBy(250)
            send(model)
            repository.streamEvents.send(ChatStreamEvent.Failed(DomainError.Network))
            repository.streamEvents.close()
            advanceUntilIdle()
            assertEquals(2, preferences.completedTurnCount)
            assertTrue(model.uiState.value.realtimeImageNudgeOpen)
        }

    @Test
    fun `중복 완료 이벤트는 두 번째 전송으로 세지 않는다`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()
            send(model)
            repository.streamEvents.send(ChatStreamEvent.Completed)
            repository.streamEvents.send(ChatStreamEvent.Completed)
            repository.streamEvents.close()
            advanceUntilIdle()
            assertEquals(1, preferences.completedTurnCount)
            assertFalse(model.uiState.value.settingsOpen)
        }

    private fun TestScope.send(model: ChatRoomViewModel) {
        model.onIntent(ChatRoomIntent.InputModeChanged(ChatInputMode.PLAIN))
        model.onIntent(ChatRoomIntent.PlainTextChanged("문을 연다"))
        model.onIntent(ChatRoomIntent.Sent)
        runCurrent()
    }

    private suspend fun TestScope.complete() {
        repository.streamEvents.send(ChatStreamEvent.Completed)
        repository.streamEvents.close()
        runCurrent()
        repository.streamEvents = Channel(Channel.UNLIMITED)
    }

    private fun viewModel() =
        ChatRoomViewModel(
            chatId = "chat-1",
            chatRepository = repository,
            reportRepository = FakeReportRepository(),
            preferences = preferences,
            trialsRepository = FakeTrialsRepository(),
            creditPolicyRepository = FakeCreditPolicyRepository(),
            profileRepository = FakeUserProfileRepository(),
            analytics = NoOpAnalytics,
        )
}
