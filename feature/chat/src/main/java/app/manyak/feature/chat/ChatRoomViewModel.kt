package app.manyak.feature.chat

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.chat.ChatInputMode
import app.manyak.core.domain.chat.ChatPreferencesRepository
import app.manyak.core.domain.chat.ChatRepository
import app.manyak.core.domain.chat.ChatStreamEvent
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
import app.manyak.feature.chat.suggestion.ChatSuggestions
import app.manyak.feature.chat.suggestion.ChoicesProgress
import app.manyak.feature.chat.suggestion.FilledSuggestion
import app.manyak.feature.chat.suggestion.SuggestionOrigin
import app.manyak.feature.chat.suggestion.chatSuggestions
import app.manyak.feature.chat.suggestion.choiceOrigin
import app.manyak.feature.chat.suggestion.composerOrigin
import app.manyak.feature.chat.suggestion.normalizeSuggestion
import app.manyak.feature.chat.suggestion.randomSuggestionPosition
import app.manyak.feature.chat.suggestion.shouldGenerateChoices
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.random.Random

/** 화면이 그리는 턴 하나 — 사용자 입력과 AI 출력의 짝. */
data class ChatRoomTurn(
    val id: Long,
    val userInput: String,
    val aiOutput: String,
    /** 다음 행동 선택지. 화면은 마지막 턴의 것만 그린다. */
    val choices: List<String> = emptyList(),
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
    /** 턴이 0개인 방의 첫 입력 후보. */
    val suggestedInputs: List<String> = emptyList(),
    /** 선택지 생성의 진행 상태. 대상 턴이 마지막 턴일 때만 그린다. */
    val choicesProgress: ChoicesProgress? = null,
    /** 추천 입력 사용법 힌트를 아직 보지 않았는지. 이 방에 머무는 동안 값이 바뀌지 않는다. */
    val choicesHintUnseen: Boolean = false,
    /** 화면에 그릴 진행 블록. 응답을 다 받았는데 목록을 못 읽은 경우에도 남는다. */
    val streaming: StreamingTurn? = null,
    /** 전송 잠금. [streaming] 과 따로 두는 이유는 둘의 수명이 갈리는 경우가 있기 때문이다. */
    val isStreaming: Boolean = false,
) {
    /** 컴포저와 메시지 목록이 함께 쓰는 추천 목록. */
    val suggestions: ChatSuggestions
        get() = chatSuggestions(turns.lastOrNull(), suggestedInputs, choicesEnabled)
}

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

    /** 추천 문장을 눌렀다. 입력창을 거치지 않고 바로 보낸다. */
    data class SuggestionSent(
        val position: Int,
    ) : ChatRoomIntent

    /** 채우기 버튼을 눌렀다. 입력창에 넣기만 한다. */
    data class SuggestionFilled(
        val position: Int,
    ) : ChatRoomIntent

    data object RandomSuggestionSent : ChatRoomIntent

    data object ChoicesRetried : ChatRoomIntent
}

sealed interface ChatRoomEvent {
    data object LoadStarted : ChatRoomEvent

    data class Loaded(
        val storyTitle: String,
        val prologue: String,
        val turns: List<ChatRoomTurn>,
        val suggestedInputs: List<String>,
    ) : ChatRoomEvent

    data object LoadFailed : ChatRoomEvent

    data class PreferencesLoaded(
        val composer: ChatComposerState,
        val choicesEnabled: Boolean,
        val hintUnseen: Boolean,
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

    data class ChoicesRequested(
        val turnId: Long,
    ) : ChatRoomEvent

    data class ChoicesFailed(
        val turnId: Long,
    ) : ChatRoomEvent
}

sealed interface ChatRoomEffect {
    /** [message] 가 null 이면 화면이 기본 문구를 쓴다. */
    data class ShowStreamFailure(
        val message: String?,
    ) : ChatRoomEffect

    /** 추천을 입력창에 채웠다. 화면이 그 입력창으로 포커스를 옮긴다. */
    data object ComposerFilled : ChatRoomEffect
}

/**
 * 채팅방. 상세 조회 렌더에 더해 턴 진행(SSE)과 추천 입력·선택지를 맡는다.
 *
 * 재생성·삭제는 다음 단계에서 붙는다.
 */
@HiltViewModel(assistedFactory = ChatRoomViewModel.Factory::class)
class ChatRoomViewModel
    @AssistedInject
    constructor(
        @Assisted private val chatId: String,
        private val chatRepository: ChatRepository,
        private val preferences: ChatPreferencesRepository,
    ) : MviViewModel<ChatRoomIntent, ChatRoomUiState, ChatRoomEvent, ChatRoomEffect>(ChatRoomUiState()) {
        private var preferencesJob: Job? = null
        private var loadJob: Job? = null
        private var streamJob: Job? = null
        private var choicesJob: Job? = null

        /**
         * 의도 처리기가 읽는 정본.
         *
         * `uiState` 를 읽어 다음 값을 만들지 않는다 — 사건은 별도 코루틴이 reduce 하므로 앞서 보낸
         * 사건이 아직 반영되지 않았을 수 있고, 그러면 빠르게 친 입력이나 방금 받은 선택지가 조용히
         * 어긋난다. 이 값들은 단일 소비자인 [handleIntent] 와 그것이 띄운 작업 안에서만 바뀐다.
         */
        private var composer = ChatComposerState()
        private var turns: List<ChatRoomTurn> = emptyList()
        private var suggestedInputs: List<String> = emptyList()
        private var choicesEnabled = true
        private var hintUnseen = false

        /**
         * 응답을 받는 중인지. [streamJob] 은 확정 조회가 끝날 때까지 살아 있어 선택지 생성 조건과
         * 수명이 다르다.
         */
        private var isStreaming = false

        /** 채우기로 입력창에 넣어 둔 추천 원문. 전송에 성공하면 비운다. */
        private var filled: FilledSuggestion? = null

        private val random = Random.Default

        private val suggestions: ChatSuggestions
            get() = chatSuggestions(turns.lastOrNull(), suggestedInputs, choicesEnabled)

        init {
            preferencesJob =
                viewModelScope.launch {
                    composer = composer.convertTo(preferences.inputMode())
                    choicesEnabled = preferences.choicesEnabled()
                    hintUnseen = !preferences.isChoicesHintSeen()
                    dispatchEvent(
                        ChatRoomEvent.PreferencesLoaded(
                            composer = composer,
                            choicesEnabled = choicesEnabled,
                            hintUnseen = hintUnseen,
                        ),
                    )
                }
            load()
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
                    choicesEnabled = intent.enabled
                    // 끄면 그리지도 만들지도 않는다. 켜는 순간은 생성 시점 중 하나다.
                    choicesJob?.cancel()
                    dispatchEvent(ChatRoomEvent.ChoicesEnabledChanged(intent.enabled))
                    preferences.setChoicesEnabled(intent.enabled)
                    generateChoices()
                }

                ChatRoomIntent.Sent -> send()

                else -> handleSuggestion(intent)
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
                    // 힌트 노출 판정이 기기 설정에 기대므로 설정을 먼저 읽는다.
                    preferencesJob?.join()
                    when (val result = chatRepository.chatDetail(chatId)) {
                        is DomainResult.Success -> {
                            turns = result.value.turns.map { turn -> turn.toUi() }
                            suggestedInputs = result.value.suggestedInputs
                            dispatchEvent(
                                ChatRoomEvent.Loaded(
                                    storyTitle = result.value.storyTitle,
                                    prologue = result.value.prologue,
                                    turns = turns,
                                    suggestedInputs = suggestedInputs,
                                ),
                            )
                            // 보이는 순간 열람으로 기록한다. 상태는 그대로 둬 이 방에서는 계속 보인다.
                            if (hintUnseen && turns.isEmpty()) preferences.markChoicesHintSeen()
                        }

                        is DomainResult.Failure -> dispatchEvent(ChatRoomEvent.LoadFailed)
                    }
                }
        }

        private fun send() {
            val userInput = composer.toUserInput()
            startTurn(userInput, composerOrigin(userInput, filled))
        }

        /** 추천 문장을 입력창을 거치지 않고 바로 보낸다. */
        private fun sendSuggestion(position: Int) {
            val current = suggestions
            val text =
                current.items
                    .getOrNull(position)
                    ?.trim()
                    .orEmpty()
            if (text.isEmpty()) return
            startTurn(normalizeSuggestion(text), choiceOrigin(position, current.sourceTurnId))
        }

        private suspend fun handleSuggestion(intent: ChatRoomIntent) {
            when (intent) {
                is ChatRoomIntent.SuggestionSent -> sendSuggestion(intent.position)

                is ChatRoomIntent.SuggestionFilled -> {
                    val current = suggestions
                    val text = current.items.getOrNull(intent.position) ?: return
                    filled =
                        FilledSuggestion(
                            text = text,
                            position = intent.position,
                            sourceTurnId = current.sourceTurnId,
                        )
                    updateComposer(composer.filledWith(text))
                    dispatchEffect(ChatRoomEffect.ComposerFilled)
                }

                ChatRoomIntent.RandomSuggestionSent ->
                    randomSuggestionPosition(suggestions.items, random)?.let { position -> sendSuggestion(position) }

                ChatRoomIntent.ChoicesRetried -> generateChoices()

                else -> Unit
            }
        }

        /**
         * 턴 하나를 연다.
         *
         * **진행 중인 작업을 직접 확인해 두 번째 요청을 버린다** — 버튼을 잠그는 것만으로는 연타와
         * 접근성 서비스의 반복 클릭을 막지 못하고, 턴은 되돌릴 수 없는 쓰기다.
         */
        private fun startTurn(
            userInput: String,
            origin: SuggestionOrigin,
        ) {
            if (streamJob?.isActive == true) return
            if (userInput.isBlank()) return

            // 다음 턴의 입력이 앞 턴에서 채운 문장과 대조되면 안 된다.
            filled = null
            composer = composer.cleared()
            choicesJob?.cancel()
            isStreaming = true
            streamJob =
                viewModelScope.launch {
                    dispatchEvent(ChatRoomEvent.SendStarted(userInput = userInput, composer = composer))
                    chatRepository
                        .streamTurn(
                            chatId = chatId,
                            userInput = userInput,
                            userSource = origin.userSource,
                            sourceTurnId = origin.sourceTurnId,
                            choiceOrder = origin.choiceOrder,
                        ).collectBatched { event -> handleStreamEvent(event) }
                }
        }

        private suspend fun handleStreamEvent(event: ChatStreamEvent) {
            when (event) {
                ChatStreamEvent.Started -> Unit

                is ChatStreamEvent.Token -> dispatchEvent(ChatRoomEvent.TokensAppended(event.text))

                is ChatStreamEvent.CharacterImage ->
                    dispatchEvent(ChatRoomEvent.CharacterImageAppended(event.name, event.imageUrl))

                ChatStreamEvent.Completed -> {
                    isStreaming = false
                    refreshTurns(confirmed = true)
                }

                is ChatStreamEvent.Failed -> {
                    isStreaming = false
                    dispatchEvent(ChatRoomEvent.StreamCleared)
                    dispatchEffect(ChatRoomEffect.ShowStreamFailure(event.message))
                }

                ChatStreamEvent.Interrupted -> {
                    isStreaming = false
                    dispatchEvent(ChatRoomEvent.StreamCleared)
                    dispatchEffect(ChatRoomEffect.ShowStreamFailure(null))
                    // 서버 저장 여부가 불명이라 임의로 복원하지 않고 확정 상태를 다시 읽는다.
                    refreshTurns(confirmed = false)
                }
            }
        }

        /**
         * 서버 확정본으로 목록을 맞춘다.
         *
         * [confirmed] 는 응답을 끝까지 받았는지다. 끝까지 받았는데 목록을 읽지 못하면 **진행 블록을
         * 걷지 않고 잠금만 푼다** — 턴은 이미 저장됐으므로 블록을 지우면 방금 받은 이야기가 화면에서
         * 사라진다.
         */
        private suspend fun refreshTurns(confirmed: Boolean) {
            when (val result = chatRepository.chatDetail(chatId)) {
                is DomainResult.Success -> {
                    turns = result.value.turns.map { turn -> turn.toUi() }
                    suggestedInputs = result.value.suggestedInputs
                    dispatchEvent(
                        if (confirmed) ChatRoomEvent.TurnConfirmed(turns) else ChatRoomEvent.TurnsRefreshed(turns),
                    )
                    generateChoices()
                }

                is DomainResult.Failure ->
                    if (confirmed) {
                        dispatchEvent(ChatRoomEvent.StreamSettled)
                        dispatchEffect(ChatRoomEffect.ShowStreamFailure(null))
                    }
            }
        }

        /**
         * 마지막 턴의 선택지를 만든다. 조건을 만족하지 않으면 아무것도 하지 않는다.
         *
         * **응답 본문으로 그리지 않는다** — 200 은 저장이 끝났다는 신호일 뿐이고 화면은 상세를 다시
         * 읽어 `turns[].choices` 로 그린다. 그래서 생성이 끝나도 조회에 실패하면 실패로 남긴다.
         *
         * 진행 상태를 **대상 턴에 묶어** 늦게 끝난 요청이 최신 상태를 덮지 않게 한다.
         */
        private fun generateChoices() {
            val lastTurn = turns.lastOrNull() ?: return
            if (!shouldGenerateChoices(lastTurn, choicesEnabled, isStreaming)) return

            val turnId = lastTurn.id
            choicesJob?.cancel()
            choicesJob =
                viewModelScope.launch {
                    dispatchEvent(ChatRoomEvent.ChoicesRequested(turnId))
                    val generated = chatRepository.generateChoices(chatId, turnId)
                    val detail = if (generated is DomainResult.Success) chatRepository.chatDetail(chatId) else null
                    if (detail is DomainResult.Success) {
                        turns = detail.value.turns.map { turn -> turn.toUi() }
                        dispatchEvent(ChatRoomEvent.TurnsRefreshed(turns))
                    } else {
                        dispatchEvent(ChatRoomEvent.ChoicesFailed(turnId))
                    }
                }
        }

        override fun reduce(
            state: ChatRoomUiState,
            event: ChatRoomEvent,
        ): ChatRoomUiState = reduceChatRoom(state, event)

        @AssistedFactory
        interface Factory {
            fun create(chatId: String): ChatRoomViewModel
        }
    }

private fun reduceChatRoom(
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
                suggestedInputs = event.suggestedInputs,
            )

        ChatRoomEvent.LoadFailed -> state.copy(isLoading = false, loadFailed = true)

        is ChatRoomEvent.PreferencesLoaded ->
            state.copy(
                composer = event.composer,
                choicesEnabled = event.choicesEnabled,
                choicesHintUnseen = event.hintUnseen,
            )

        is ChatRoomEvent.ComposerChanged -> state.copy(composer = event.composer)

        is ChatRoomEvent.ChoicesEnabledChanged -> state.copy(choicesEnabled = event.enabled)

        else -> reduceTurn(state, event)
    }

/** 턴 진행 사건. 한 `when` 에 모으면 분기가 읽기 어려울 만큼 늘어난다. */
private fun reduceTurn(
    state: ChatRoomUiState,
    event: ChatRoomEvent,
): ChatRoomUiState =
    when (event) {
        is ChatRoomEvent.SendStarted ->
            state.copy(
                composer = event.composer,
                streaming = StreamingTurn(userInput = event.userInput),
                isStreaming = true,
                choicesProgress = null,
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

        is ChatRoomEvent.TurnsRefreshed -> state.copy(turns = event.turns, choicesProgress = null)

        ChatRoomEvent.StreamCleared -> state.copy(streaming = null, isStreaming = false)

        ChatRoomEvent.StreamSettled -> state.copy(isStreaming = false)

        else -> reduceChoices(state, event)
    }

private fun reduceChoices(
    state: ChatRoomUiState,
    event: ChatRoomEvent,
): ChatRoomUiState =
    when (event) {
        is ChatRoomEvent.ChoicesRequested -> state.copy(choicesProgress = ChoicesProgress(event.turnId))

        is ChatRoomEvent.ChoicesFailed ->
            state.copy(choicesProgress = ChoicesProgress(event.turnId, failed = true))

        else -> state
    }

private fun app.manyak.core.domain.chat.ChatTurn.toUi(): ChatRoomTurn =
    ChatRoomTurn(id = id, userInput = userInput, aiOutput = aiOutput, choices = choices)
