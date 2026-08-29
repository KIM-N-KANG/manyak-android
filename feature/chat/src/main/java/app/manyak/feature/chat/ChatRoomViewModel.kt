package app.manyak.feature.chat

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.chat.ChatInputMode
import app.manyak.core.domain.chat.ChatPreferencesRepository
import app.manyak.core.domain.chat.ChatRepository
import app.manyak.core.domain.chat.ChatStreamEvent
import app.manyak.core.domain.chat.UserSource
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.ui.mvi.MviViewModel
import app.manyak.feature.chat.composer.ChatComposerState
import app.manyak.feature.chat.composer.InputBlockType
import app.manyak.feature.chat.composer.addBlock
import app.manyak.feature.chat.composer.removeBlock
import app.manyak.feature.chat.composer.updateBlock
import app.manyak.feature.chat.message.ChatMessageSegment
import app.manyak.feature.chat.message.appendCharacterImage
import app.manyak.feature.chat.message.appendText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 화면이 그리는 턴 하나 — 사용자 입력과 AI 출력의 짝. */
data class ChatRoomTurn(
    val id: Long,
    val userInput: String,
    val aiOutput: String,
)

/** 진행 중인 턴. 확정되면 확정 턴 목록과 **한 번에** 교체된다. */
data class StreamingTurn(
    val userInput: String,
    val segments: List<ChatMessageSegment> = emptyList(),
)

data class ChatRoomUiState(
    val isLoading: Boolean = true,
    val storyTitle: String = "",
    val prologue: String = "",
    val turns: List<ChatRoomTurn> = emptyList(),
    val loadFailed: Boolean = false,
    val composer: ChatComposerState = ChatComposerState(),
    val choicesEnabled: Boolean = true,
    /** 화면에 그릴 진행 블록. 응답을 다 받았는데 목록을 못 읽은 경우에도 남는다. */
    val streaming: StreamingTurn? = null,
    /** 전송 잠금. [streaming] 과 따로 두는 이유는 둘의 수명이 갈리는 경우가 있기 때문이다. */
    val isStreaming: Boolean = false,
)

sealed interface ChatRoomIntent {
    data object Retry : ChatRoomIntent

    data class PlainTextChanged(
        val text: String,
    ) : ChatRoomIntent

    data class BlockValueChanged(
        val id: Long,
        val value: String,
    ) : ChatRoomIntent

    data class BlockAdded(
        val type: InputBlockType,
    ) : ChatRoomIntent

    data class BlockRemoved(
        val id: Long,
    ) : ChatRoomIntent

    data class InputModeChanged(
        val mode: ChatInputMode,
    ) : ChatRoomIntent

    data class ChoicesEnabledChanged(
        val enabled: Boolean,
    ) : ChatRoomIntent

    data object Sent : ChatRoomIntent
}

sealed interface ChatRoomEvent {
    data object LoadStarted : ChatRoomEvent

    data class Loaded(
        val storyTitle: String,
        val prologue: String,
        val turns: List<ChatRoomTurn>,
    ) : ChatRoomEvent

    data object LoadFailed : ChatRoomEvent

    data class PreferencesLoaded(
        val composer: ChatComposerState,
        val choicesEnabled: Boolean,
    ) : ChatRoomEvent

    data class ComposerChanged(
        val composer: ChatComposerState,
    ) : ChatRoomEvent

    data class ChoicesEnabledChanged(
        val enabled: Boolean,
    ) : ChatRoomEvent

    data class SendStarted(
        val userInput: String,
        val composer: ChatComposerState,
    ) : ChatRoomEvent

    data class TokensAppended(
        val text: String,
    ) : ChatRoomEvent

    data class CharacterImageAppended(
        val name: String,
        val imageUrl: String,
    ) : ChatRoomEvent

    /** 확정 턴 목록과 진행 블록을 한 사건으로 바꾼다 — 두 사건으로 나누면 한 프레임 동안 화면이 수축한다. */
    data class TurnConfirmed(
        val turns: List<ChatRoomTurn>,
    ) : ChatRoomEvent

    data class TurnsRefreshed(
        val turns: List<ChatRoomTurn>,
    ) : ChatRoomEvent

    /** 진행 블록을 걷고 잠금을 푼다. 낙관적으로 붙였던 사용자 밴드도 함께 사라진다. */
    data object StreamCleared : ChatRoomEvent

    /** 잠금만 푼다. 응답은 다 받았는데 확정 목록을 읽지 못한 경우다. */
    data object StreamSettled : ChatRoomEvent
}

sealed interface ChatRoomEffect {
    /** [message] 가 null 이면 화면이 기본 문구를 쓴다. */
    data class ShowStreamFailure(
        val message: String?,
    ) : ChatRoomEffect
}

/**
 * 채팅방. 상세 조회 렌더에 더해 턴 진행(SSE)을 맡는다.
 *
 * 추천 입력·선택지와 재생성·삭제는 다음 단계에서 붙는다.
 */
@HiltViewModel(assistedFactory = ChatRoomViewModel.Factory::class)
class ChatRoomViewModel
    @AssistedInject
    constructor(
        @Assisted private val chatId: String,
        private val chatRepository: ChatRepository,
        private val preferences: ChatPreferencesRepository,
    ) : MviViewModel<ChatRoomIntent, ChatRoomUiState, ChatRoomEvent, ChatRoomEffect>(ChatRoomUiState()) {
        private var loadJob: Job? = null
        private var streamJob: Job? = null

        /**
         * 컴포저의 정본.
         *
         * `uiState` 를 읽어 다음 값을 만들지 않는다 — 사건은 별도 코루틴이 reduce 하므로 앞서 보낸
         * 사건이 아직 반영되지 않았을 수 있고, 그러면 빠르게 친 입력이 조용히 사라진다. 이 값은
         * 단일 소비자인 `handleIntent` 안에서만 바뀐다.
         */
        private var composer = ChatComposerState()

        init {
            load()
            viewModelScope.launch {
                val inputMode = preferences.inputMode()
                composer = composer.convertTo(inputMode)
                dispatchEvent(
                    ChatRoomEvent.PreferencesLoaded(
                        composer = composer,
                        choicesEnabled = preferences.choicesEnabled(),
                    ),
                )
            }
        }

        override suspend fun handleIntent(intent: ChatRoomIntent) {
            when (intent) {
                ChatRoomIntent.Retry -> load()

                is ChatRoomIntent.PlainTextChanged -> updateComposer(composer.copy(plainText = intent.text))

                is ChatRoomIntent.BlockValueChanged ->
                    updateComposer(composer.copy(blocks = composer.blocks.updateBlock(intent.id, intent.value)))

                is ChatRoomIntent.BlockAdded ->
                    updateComposer(composer.copy(blocks = composer.blocks.addBlock(intent.type)))

                is ChatRoomIntent.BlockRemoved ->
                    updateComposer(composer.copy(blocks = composer.blocks.removeBlock(intent.id)))

                is ChatRoomIntent.InputModeChanged -> {
                    updateComposer(composer.convertTo(intent.mode))
                    preferences.setInputMode(intent.mode)
                }

                is ChatRoomIntent.ChoicesEnabledChanged -> {
                    dispatchEvent(ChatRoomEvent.ChoicesEnabledChanged(intent.enabled))
                    preferences.setChoicesEnabled(intent.enabled)
                }

                ChatRoomIntent.Sent -> send()
            }
        }

        private suspend fun updateComposer(next: ChatComposerState) {
            composer = next
            dispatchEvent(ChatRoomEvent.ComposerChanged(next))
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
                                    turns = result.value.turns.map { turn -> turn.toUi() },
                                ),
                            )

                        is DomainResult.Failure -> dispatchEvent(ChatRoomEvent.LoadFailed)
                    }
                }
        }

        /**
         * 턴을 보낸다.
         *
         * **진행 중인 작업을 직접 확인해 두 번째 요청을 버린다** — 버튼을 잠그는 것만으로는 연타와
         * 접근성 서비스의 반복 클릭을 막지 못하고, 턴은 되돌릴 수 없는 쓰기다.
         */
        private fun send() {
            if (streamJob?.isActive == true) return
            val userInput = composer.toUserInput()
            if (userInput.isBlank()) return

            composer = composer.cleared()
            streamJob =
                viewModelScope.launch {
                    dispatchEvent(ChatRoomEvent.SendStarted(userInput = userInput, composer = composer))
                    collectTurnStream(
                        chatRepository.streamTurn(
                            chatId = chatId,
                            userInput = userInput,
                            userSource = UserSource.TYPED,
                        ),
                    )
                }
        }

        /**
         * 토큰을 [TOKEN_BATCH_MILLIS] 단위로 모아 흘린다.
         *
         * 사건 하나마다 `reduce` 가 돌고 상태가 바뀌면 recomposition 이 일어난다. 토큰을 그대로
         * 흘리면 초당 수십~수백 번 화면이 다시 그려지고, 자라는 본문을 매번 다시 파싱하게 된다.
         *
         * **모아 둔 조각이 있을 때만 시간을 잰다** — 주기적으로 도는 타이머를 두면 스트림이 조용한
         * 동안에도 깨어나고, 가상 시간을 쓰는 테스트에서는 영원히 끝나지 않는다.
         */
        private suspend fun collectTurnStream(stream: Flow<ChatStreamEvent>) =
            coroutineScope {
                val events = Channel<ChatStreamEvent>()
                launch {
                    try {
                        stream.collect { event -> events.send(event) }
                    } finally {
                        events.close()
                    }
                }

                val tokens = StringBuilder()
                while (true) {
                    val received =
                        if (tokens.isEmpty()) {
                            events.receiveCatching()
                        } else {
                            withTimeoutOrNull(TOKEN_BATCH_MILLIS) { events.receiveCatching() }
                        }
                    if (received == null) {
                        // 시간이 찼다. 모아 둔 조각을 흘리고 다시 기다린다.
                        flushTokens(tokens)
                    } else {
                        val event = received.getOrNull() ?: break
                        handleStreamEvent(event, tokens)
                    }
                }
            }

        private suspend fun handleStreamEvent(
            event: ChatStreamEvent,
            tokens: StringBuilder,
        ) {
            when (event) {
                ChatStreamEvent.Started -> Unit

                is ChatStreamEvent.Token -> tokens.append(event.text)

                is ChatStreamEvent.CharacterImage -> {
                    // 모아 둔 토큰을 먼저 흘려야 이미지가 잘못된 문단에 끼지 않는다.
                    flushTokens(tokens)
                    dispatchEvent(ChatRoomEvent.CharacterImageAppended(event.name, event.imageUrl))
                }

                ChatStreamEvent.Completed -> {
                    flushTokens(tokens)
                    confirmTurn()
                }

                is ChatStreamEvent.Failed -> {
                    flushTokens(tokens)
                    dispatchEvent(ChatRoomEvent.StreamCleared)
                    dispatchEffect(ChatRoomEffect.ShowStreamFailure(event.message))
                }

                ChatStreamEvent.Interrupted -> {
                    flushTokens(tokens)
                    dispatchEvent(ChatRoomEvent.StreamCleared)
                    dispatchEffect(ChatRoomEffect.ShowStreamFailure(null))
                    // 서버 저장 여부가 불명이라 임의로 복원하지 않고 확정 상태를 다시 읽는다.
                    refreshTurns()
                }
            }
        }

        private suspend fun flushTokens(tokens: StringBuilder) {
            if (tokens.isEmpty()) return
            val text = tokens.toString()
            tokens.setLength(0)
            dispatchEvent(ChatRoomEvent.TokensAppended(text))
        }

        /**
         * 저장이 끝났으니 서버 확정본으로 교체한다.
         *
         * 목록을 읽지 못하면 **진행 블록을 걷지 않고 잠금만 푼다** — 턴은 이미 저장됐으므로 블록을
         * 지우면 방금 받은 이야기가 화면에서 사라진다.
         */
        private suspend fun confirmTurn() {
            when (val result = chatRepository.chatDetail(chatId)) {
                is DomainResult.Success ->
                    dispatchEvent(ChatRoomEvent.TurnConfirmed(result.value.turns.map { turn -> turn.toUi() }))

                is DomainResult.Failure -> {
                    dispatchEvent(ChatRoomEvent.StreamSettled)
                    dispatchEffect(ChatRoomEffect.ShowStreamFailure(null))
                }
            }
        }

        private suspend fun refreshTurns() {
            val result = chatRepository.chatDetail(chatId)
            if (result is DomainResult.Success) {
                dispatchEvent(ChatRoomEvent.TurnsRefreshed(result.value.turns.map { turn -> turn.toUi() }))
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

                is ChatRoomEvent.PreferencesLoaded ->
                    state.copy(composer = event.composer, choicesEnabled = event.choicesEnabled)

                is ChatRoomEvent.ComposerChanged -> state.copy(composer = event.composer)

                is ChatRoomEvent.ChoicesEnabledChanged -> state.copy(choicesEnabled = event.enabled)

                else -> reduceStreaming(state, event)
            }

        /** 턴 진행 사건만 따로 접는다. 한 `when` 에 모으면 분기가 읽기 어려울 만큼 늘어난다. */
        private fun reduceStreaming(
            state: ChatRoomUiState,
            event: ChatRoomEvent,
        ): ChatRoomUiState =
            when (event) {
                is ChatRoomEvent.SendStarted ->
                    state.copy(
                        composer = event.composer,
                        streaming = StreamingTurn(userInput = event.userInput),
                        isStreaming = true,
                    )

                is ChatRoomEvent.TokensAppended ->
                    state.copy(
                        streaming = state.streaming?.let { it.copy(segments = it.segments.appendText(event.text)) },
                    )

                is ChatRoomEvent.CharacterImageAppended ->
                    state.copy(
                        streaming =
                            state.streaming?.let {
                                it.copy(segments = it.segments.appendCharacterImage(event.name, event.imageUrl))
                            },
                    )

                is ChatRoomEvent.TurnConfirmed ->
                    state.copy(turns = event.turns, streaming = null, isStreaming = false)

                is ChatRoomEvent.TurnsRefreshed -> state.copy(turns = event.turns)

                ChatRoomEvent.StreamCleared -> state.copy(streaming = null, isStreaming = false)

                ChatRoomEvent.StreamSettled -> state.copy(isStreaming = false)

                else -> state
            }

        @AssistedFactory
        interface Factory {
            fun create(chatId: String): ChatRoomViewModel
        }

        private companion object {
            /**
             * 토큰을 모으는 간격. 프레임 하나(약 17ms)보다 크게 잡아 recomposition 을 초당 스무 번쯤으로
             * 묶는다. 측정으로 정한 값이 아니라 시작값이다.
             */
            const val TOKEN_BATCH_MILLIS = 50L
        }
    }

private fun app.manyak.core.domain.chat.ChatTurn.toUi(): ChatRoomTurn =
    ChatRoomTurn(id = id, userInput = userInput, aiOutput = aiOutput)
