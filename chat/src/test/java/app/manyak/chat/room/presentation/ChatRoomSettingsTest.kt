package app.manyak.chat.room.presentation

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.chat.entity.ChatStreamEvent
import app.manyak.chat.room.presentation.composer.InputBlockType
import app.manyak.chat.testing.FakeChatPreferencesRepository
import app.manyak.chat.testing.FakeChatRepository
import app.manyak.chat.testing.FakeCreditPolicyRepository
import app.manyak.chat.testing.FakeReportRepository
import app.manyak.chat.testing.FakeTrialsRepository
import app.manyak.chat.testing.FakeUserProfileRepository
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

/** 채팅 설정 시트의 실시간 이미지 토글. 요청 본문과 진행 블록의 스냅샷을 고정한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRoomSettingsTest {
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
    fun `저장된 실시간 이미지 설정을 진입 시 상태로 올린다`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeChatRepository(), FakeChatPreferencesRepository(realtimeImage = false))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.realtimeImageEnabled)
        }

    @Test
    fun `토글을 끄면 이어쓰기 요청에 false 를 싣고 기기에 저장한다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val preferences = FakeChatPreferencesRepository()
            val viewModel = viewModel(repository, preferences)
            advanceUntilIdle()

            viewModel.onIntent(ChatRoomIntent.RealtimeImageEnabledChanged(false))
            viewModel.type("문을 연다")
            viewModel.onIntent(ChatRoomIntent.Sent)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.realtimeImageEnabled)
            assertEquals(listOf(false), preferences.savedRealtimeImages)
            assertEquals(listOf(false), repository.streamedRealtimeImages)
            assertEquals(
                false,
                viewModel.uiState.value.streaming
                    ?.realtimeImage,
            )
        }

    @Test
    fun `기본값은 켬이라 요청에 true 를 명시해 보낸다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.type("문을 연다")
            viewModel.onIntent(ChatRoomIntent.Sent)
            advanceUntilIdle()

            assertEquals(listOf(true), repository.streamedRealtimeImages)
            assertEquals(
                true,
                viewModel.uiState.value.streaming
                    ?.realtimeImage,
            )
        }

    @Test
    fun `응답을 받는 중에 토글을 바꿔도 진행 중 턴의 스냅샷은 그대로다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            viewModel.type("문을 연다")
            viewModel.onIntent(ChatRoomIntent.Sent)
            advanceUntilIdle()

            viewModel.onIntent(ChatRoomIntent.RealtimeImageEnabledChanged(false))
            repository.streamEvents.send(ChatStreamEvent.Token("문이 열린다"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.realtimeImageEnabled)
            assertTrue(state.isStreaming)
            assertEquals(true, state.streaming?.realtimeImage)

            // 다음 턴부터 바뀐 값을 쓴다. 가짜 스트림은 닫아야 끝난다.
            repository.streamEvents.send(ChatStreamEvent.Completed)
            repository.streamEvents.close()
            advanceUntilIdle()
            viewModel.type("계단을 오른다")
            viewModel.onIntent(ChatRoomIntent.Sent)
            advanceUntilIdle()
            assertEquals(listOf(true, false), repository.streamedRealtimeImages)
        }

    @Test
    fun `재생성 요청도 지금 설정의 값을 싣는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = viewModel(repository, FakeChatPreferencesRepository(realtimeImage = false))
            advanceUntilIdle()

            viewModel.onIntent(ChatRoomIntent.RegenerateRequested(turnId = 1))
            advanceUntilIdle()

            assertEquals(listOf(false), repository.regeneratedRealtimeImages)
            assertEquals(
                false,
                viewModel.uiState.value.streaming
                    ?.realtimeImage,
            )
        }

    private fun viewModel(
        repository: FakeChatRepository,
        preferences: FakeChatPreferencesRepository = FakeChatPreferencesRepository(),
    ) = ChatRoomViewModel(
        chatId = "chat-1",
        chatRepository = repository,
        reportRepository = FakeReportRepository(),
        preferences = preferences,
        trialsRepository = FakeTrialsRepository(),
        creditPolicyRepository = FakeCreditPolicyRepository(),
        profileRepository = FakeUserProfileRepository(),
        analytics = NoOpAnalytics,
    )

    /** 블럭 하나에 문장을 넣는다. 기본 모드가 블럭이라 첫 칸이 상황이다. */
    private fun ChatRoomViewModel.type(text: String) {
        onIntent(ChatRoomIntent.BlockAdded(InputBlockType.DIALOGUE))
        onIntent(ChatRoomIntent.BlockValueChanged(id = 3, value = text))
    }
}
