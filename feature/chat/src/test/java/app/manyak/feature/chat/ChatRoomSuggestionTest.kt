package app.manyak.feature.chat

import app.manyak.core.analytics.NoOpAnalytics
import app.manyak.core.domain.chat.ChatDetail
import app.manyak.core.domain.chat.ChatStreamEvent
import app.manyak.core.domain.chat.ChatTurn
import app.manyak.core.domain.chat.UserSource
import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.feature.chat.suggestion.ChoicesProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

private const val SITUATION_CHOICE = "*문이 삐걱인다* 누구세요?"
private const val PLAIN_CHOICE = "조용히 물러난다"

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRoomSuggestionTest {
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
    fun `선택지를 누르면 정규화해 바로 보내고 출처를 함께 싣는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = loadedWithChoices(repository)

            viewModel.onIntent(ChatRoomIntent.SuggestionSent(0))
            advanceUntilIdle()

            // 직접 입력한 같은 문장과 저장 본문이 갈리면 안 된다.
            assertEquals(listOf("*문이 삐걱인다*\n\n누구세요?"), repository.streamedInputs)
            assertEquals(Triple(UserSource.CHOICE, 1L, 1), repository.streamedOrigins.single())
        }

    @Test
    fun `채우기는 입력창에 넣기만 하고 그대로 보내면 선택이다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = loadedWithChoices(repository)

            viewModel.onIntent(ChatRoomIntent.SuggestionFilled(1))
            advanceUntilIdle()
            assertEquals(emptyList<String>(), repository.streamedInputs)
            assertEquals(
                PLAIN_CHOICE,
                viewModel.uiState.value.composer
                    .toUserInput(),
            )

            viewModel.onIntent(ChatRoomIntent.Sent)
            advanceUntilIdle()
            assertEquals(Triple(UserSource.CHOICE, 1L, 2), repository.streamedOrigins.single())
        }

    @Test
    fun `채운 뒤 고쳐 보내면 고친 선택이고 원본 턴은 그대로 싣는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = loadedWithChoices(repository)

            viewModel.onIntent(ChatRoomIntent.SuggestionFilled(1))
            advanceUntilIdle()
            val blockId =
                viewModel.uiState.value.composer.blocks
                    .first()
                    .id
            viewModel.onIntent(ChatRoomIntent.BlockValueChanged(blockId, "조용히 물러나며 숨을 죽인다"))
            viewModel.onIntent(ChatRoomIntent.Sent)
            advanceUntilIdle()

            assertEquals(Triple(UserSource.EDITED_CHOICE, 1L, 2), repository.streamedOrigins.single())
        }

    @Test
    fun `전송에 성공하면 채운 기억을 비운다`() =
        runTest(dispatcher) {
            // 다음 턴의 입력이 앞 턴에서 채운 문장과 대조되면 안 된다.
            val repository = FakeChatRepository()
            val viewModel = loadedWithChoices(repository)

            viewModel.onIntent(ChatRoomIntent.SuggestionFilled(1))
            advanceUntilIdle()
            viewModel.onIntent(ChatRoomIntent.Sent)
            advanceUntilIdle()
            repository.queuedChatDetailResults += DomainResult.Success(detail(turnsWithChoices()))
            repository.streamEvents.send(ChatStreamEvent.Completed)
            // 스트림을 닫아야 다음 전송이 진행 중 판정에 걸리지 않는다.
            repository.streamEvents.close()
            advanceUntilIdle()

            val blockId =
                viewModel.uiState.value.composer.blocks
                    .first()
                    .id
            viewModel.onIntent(ChatRoomIntent.BlockValueChanged(blockId, PLAIN_CHOICE))
            viewModel.onIntent(ChatRoomIntent.Sent)
            advanceUntilIdle()

            assertEquals(UserSource.TYPED, repository.streamedOrigins.last().first)
        }

    @Test
    fun `무작위 전송은 공백이 아닌 후보를 보낸다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            repository.queuedChatDetailResults +=
                DomainResult.Success(detail(turns = emptyList(), suggestedInputs = listOf(" ", PLAIN_CHOICE)))
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.onIntent(ChatRoomIntent.RandomSuggestionSent)
            advanceUntilIdle()

            assertEquals(listOf(PLAIN_CHOICE), repository.streamedInputs)
            // 시작 추천은 원본 턴이 없어 선택 메타데이터를 싣지 않는다.
            assertEquals(Triple(UserSource.CHOICE, null, null), repository.streamedOrigins.single())
        }

    @Test
    fun `진입에서는 선택지를 만들지 않는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            viewModel(repository)
            advanceUntilIdle()

            assertEquals(emptyList<Long>(), repository.generatedChoiceTurnIds)
        }

    @Test
    fun `턴이 끝나면 선택지를 만들고 다시 읽은 결과로 그린다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = streaming(repository)

            repository.queuedChatDetailResults += DomainResult.Success(detail(twoTurns()))
            repository.queuedChatDetailResults += DomainResult.Success(detail(twoTurns(choices = listOf("문을 닫는다"))))
            repository.streamEvents.send(ChatStreamEvent.Completed)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(2L), repository.generatedChoiceTurnIds)
            // 200 은 저장 신호일 뿐이라 그리는 값은 다시 읽은 상세에서 온다.
            assertEquals(listOf("문을 닫는다"), state.turns.last().choices)
            assertNull(state.choicesProgress)
        }

    @Test
    fun `선택지 생성에 실패해도 턴은 그대로 두고 그 턴에 실패를 남긴다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = streaming(repository)

            repository.queuedChatDetailResults += DomainResult.Success(detail(twoTurns()))
            repository.queuedChoicesResults += DomainResult.Failure(DomainError.Network)
            repository.streamEvents.send(ChatStreamEvent.Completed)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, state.turns.size)
            assertEquals(ChoicesProgress(turnId = 2, failed = true), state.choicesProgress)
        }

    @Test
    fun `껐다 켜면 만들고 꺼져 있으면 만들지 않는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.onIntent(ChatRoomIntent.ChoicesEnabledChanged(false))
            advanceUntilIdle()
            assertEquals(emptyList<Long>(), repository.generatedChoiceTurnIds)
            assertFalse(viewModel.uiState.value.suggestions.hasCandidate)

            viewModel.onIntent(ChatRoomIntent.ChoicesEnabledChanged(true))
            advanceUntilIdle()
            assertEquals(listOf(1L), repository.generatedChoiceTurnIds)
        }

    @Test
    fun `힌트는 못 본 사용자가 턴 0개 방에 들어오면 보이고 그 순간 열람으로 남는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            repository.queuedChatDetailResults += DomainResult.Success(detail(turns = emptyList()))
            val preferences = FakeChatPreferencesRepository(hintSeen = false)
            val viewModel = viewModel(repository, preferences)
            advanceUntilIdle()

            // 상태를 그대로 둬 이 방에 머무는 동안은 계속 보인다.
            assertTrue(viewModel.uiState.value.choicesHintUnseen)
            assertEquals(1, preferences.hintSeenMarkCount)
        }

    @Test
    fun `턴이 있는 방에서는 힌트를 열람으로 남기지 않는다`() =
        runTest(dispatcher) {
            val repository = FakeChatRepository()
            val preferences = FakeChatPreferencesRepository(hintSeen = false)
            viewModel(repository, preferences)
            advanceUntilIdle()

            assertEquals(0, preferences.hintSeenMarkCount)
        }

    private fun viewModel(
        repository: FakeChatRepository,
        preferences: FakeChatPreferencesRepository = FakeChatPreferencesRepository(),
    ) = ChatRoomViewModel(
        chatId = "chat-1",
        chatRepository = repository,
        storyRepository = FakeStoryRepository(),
        preferences = preferences,
        analytics = NoOpAnalytics,
    )

    /** 마지막 턴에 선택지가 달린 방. */
    private fun TestScope.loadedWithChoices(repository: FakeChatRepository): ChatRoomViewModel {
        repository.queuedChatDetailResults += DomainResult.Success(detail(turnsWithChoices()))
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        return viewModel
    }

    /** 턴 하나를 보내고 스트림이 열린 상태. */
    private fun TestScope.streaming(repository: FakeChatRepository): ChatRoomViewModel {
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.onIntent(ChatRoomIntent.BlockAdded(app.manyak.feature.chat.composer.InputBlockType.DIALOGUE))
        viewModel.onIntent(ChatRoomIntent.BlockValueChanged(id = 3, value = "문을 연다"))
        advanceUntilIdle()
        viewModel.onIntent(ChatRoomIntent.Sent)
        advanceUntilIdle()
        return viewModel
    }

    private fun detail(
        turns: List<ChatTurn>,
        suggestedInputs: List<String> = emptyList(),
    ): ChatDetail = sampleChatDetail(turns = turns, suggestedInputs = suggestedInputs)

    private fun turnsWithChoices(): List<ChatTurn> =
        listOf(
            ChatTurn(
                id = 1,
                userInput = "문을 연다.",
                aiOutput = "문이 열리자 태엽 소리가 쏟아진다.",
                choices = listOf(SITUATION_CHOICE, PLAIN_CHOICE),
            ),
        )

    private fun twoTurns(choices: List<String> = emptyList()): List<ChatTurn> =
        listOf(
            ChatTurn(id = 1, userInput = "문을 연다.", aiOutput = "문이 열리자 태엽 소리가 쏟아진다."),
            ChatTurn(id = 2, userInput = "문을 연다", aiOutput = "문이 열린다", choices = choices),
        )
}
