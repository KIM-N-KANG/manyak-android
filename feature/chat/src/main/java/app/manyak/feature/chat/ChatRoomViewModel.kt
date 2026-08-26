package app.manyak.feature.chat

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.chat.ChatRepository
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.ui.mvi.MviViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 화면이 그리는 턴 하나 — 사용자 입력과 AI 출력의 짝. */
data class ChatRoomTurn(
    val id: Long,
    val userInput: String,
    val aiOutput: String,
)

data class ChatRoomUiState(
    val isLoading: Boolean = true,
    val storyTitle: String = "",
    val prologue: String = "",
    val turns: List<ChatRoomTurn> = emptyList(),
    val loadFailed: Boolean = false,
)

sealed interface ChatRoomIntent {
    data object Retry : ChatRoomIntent
}

sealed interface ChatRoomEvent {
    data object LoadStarted : ChatRoomEvent

    data class Loaded(
        val storyTitle: String,
        val prologue: String,
        val turns: List<ChatRoomTurn>,
    ) : ChatRoomEvent

    data object LoadFailed : ChatRoomEvent
}

/**
 * 채팅방. 지금은 상세 조회 렌더(제목·프롤로그·턴 이력)까지만 담당하고,
 * 입력 컴포저와 턴 진행(SSE)은 다음 단계에서 붙는다.
 */
@HiltViewModel(assistedFactory = ChatRoomViewModel.Factory::class)
class ChatRoomViewModel
    @AssistedInject
    constructor(
        @Assisted private val chatId: String,
        private val chatRepository: ChatRepository,
    ) : MviViewModel<ChatRoomIntent, ChatRoomUiState, ChatRoomEvent, Nothing>(ChatRoomUiState()) {
        private var loadJob: Job? = null

        init {
            load()
        }

        override suspend fun handleIntent(intent: ChatRoomIntent) {
            when (intent) {
                ChatRoomIntent.Retry -> load()
            }
        }

        private fun load() {
            if (loadJob?.isActive == true) return
            loadJob =
                viewModelScope.launch {
                    dispatchEvent(ChatRoomEvent.LoadStarted)
                    when (val result = chatRepository.chatDetail(chatId)) {
                        is DomainResult.Success ->
                            dispatchEvent(
                                ChatRoomEvent.Loaded(
                                    storyTitle = result.value.storyTitle,
                                    prologue = result.value.prologue,
                                    turns =
                                        result.value.turns.map { turn ->
                                            ChatRoomTurn(
                                                id = turn.id,
                                                userInput = turn.userInput,
                                                aiOutput = turn.aiOutput,
                                            )
                                        },
                                ),
                            )

                        is DomainResult.Failure ->
                            dispatchEvent(ChatRoomEvent.LoadFailed)
                    }
                }
        }

        override fun reduce(
            state: ChatRoomUiState,
            event: ChatRoomEvent,
        ): ChatRoomUiState =
            when (event) {
                ChatRoomEvent.LoadStarted -> state.copy(isLoading = true, loadFailed = false)

                is ChatRoomEvent.Loaded ->
                    state.copy(
                        isLoading = false,
                        storyTitle = event.storyTitle,
                        prologue = event.prologue,
                        turns = event.turns,
                    )

                ChatRoomEvent.LoadFailed -> state.copy(isLoading = false, loadFailed = true)
            }

        @AssistedFactory
        interface Factory {
            fun create(chatId: String): ChatRoomViewModel
        }
    }
