package app.manyak.feature.chat

import androidx.lifecycle.viewModelScope
import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.analytics.entity.CreditShortageTrigger
import app.manyak.analytics.entity.MessageInputMode
import app.manyak.analytics.entity.ReportSource
import app.manyak.common.domain.chat.ChatInputMode
import app.manyak.common.domain.chat.ChatPreferencesRepository
import app.manyak.common.domain.chat.ChatRepository
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.story.StoryRepository
import app.manyak.common.entity.chat.ChatStreamEvent
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.core.ui.report.StoryReportAction
import app.manyak.core.ui.report.StoryReportChange
import app.manyak.core.ui.report.StoryReportController
import app.manyak.core.ui.report.StoryReportUiState
import app.manyak.core.ui.report.reduceReport
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.random.Random

/** 화면이 그리는 턴 하나 — 사용자 입력과 AI 출력의 짝. */
data class ChatRoomTurn(
    val id: Long,
    val userInput: String,
    val aiOutput: String,
    /** 다음 행동 선택지. 화면은 마지막 턴의 것만 그린다. */
    val choices: List<String> = emptyList(),
    /** 이 턴에서 도달한 엔딩의 이름. */
    val reachedEnding: String? = null,
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
    /** 재생성 중인 턴. 그 턴은 목록에서 자리를 지킨 채 [streaming] 으로 바뀐다. */
    val regeneratingTurnId: Long? = null,
    /** 삭제 요청 중. 확인 다이얼로그의 버튼을 잠근다. */
    val isDeleting: Boolean = false,
    /** 이 방이 참조하는 스토리. 신고 대상이라 조회 전에는 진입점을 두지 않는다. */
    val storyId: String? = null,
    val report: StoryReportUiState = StoryReportUiState(),
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

    /** 잠긴 입력창을 눌렀다. 응답 생성 중이면 왜 입력할 수 없는지 알린다. */
    data object LockedComposerTapped : ChatRoomIntent

    data object ChoicesRetried : ChatRoomIntent

    /** 마지막 턴의 AI 출력을 다시 만든다. */
    data class RegenerateRequested(
        val turnId: Long,
    ) : ChatRoomIntent

    /** 확인 다이얼로그에서 삭제를 확정했다. */
    data object DeleteConfirmed : ChatRoomIntent

    data class Report(
        val action: StoryReportAction,
    ) : ChatRoomIntent
}

sealed interface ChatRoomEvent {
    data object LoadStarted : ChatRoomEvent

    data class Loaded(
        val storyId: String,
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

    /** 대상 턴을 진행 블록으로 **대체한다**. 사용자 입력은 그 턴의 것을 그대로 쓴다. */
    data class RegenerateStarted(
        val turnId: Long,
        val userInput: String,
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

    data object DeleteStarted : ChatRoomEvent

    data object DeleteFailed : ChatRoomEvent

    data class Report(
        val change: StoryReportChange,
    ) : ChatRoomEvent
}

sealed interface ChatRoomEffect {
    /** [message] 가 null 이면 화면이 기본 문구를 쓴다. */
    data class ShowStreamFailure(
        val message: String?,
    ) : ChatRoomEffect

    /** 이프가 모자라 턴을 열지 못했다. 앱은 로그인 필수라 402 의 사유가 이것 하나뿐이다. */
    data object ShowCreditRequired : ChatRoomEffect

    /** 삭제가 끝났다. 화면이 안내하고 채팅 탭으로 돌아간다. */
    data object ChatDeleted : ChatRoomEffect

    data object ShowDeleteFailed : ChatRoomEffect

    /** 응답 생성 중에 입력창을 눌렀다. 끝나면 입력할 수 있다고 알린다. */
    data object ShowComposerLocked : ChatRoomEffect

    data object ShowReportSubmitted : ChatRoomEffect

    data object ShowReportFailed : ChatRoomEffect
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
        private val storyRepository: StoryRepository,
        private val preferences: ChatPreferencesRepository,
        private val analytics: Analytics,
    ) : MviViewModel<ChatRoomIntent, ChatRoomUiState, ChatRoomEvent, ChatRoomEffect>(ChatRoomUiState()) {
        private var preferencesJob: Job? = null
        private var loadJob: Job? = null
        private var streamJob: Job? = null

        /** 신고 절차는 스토리 상세와 같아 :core:ui 의 컨트롤러가 소유한다. */
        private val report =
            StoryReportController(
                scope = viewModelScope,
                repository = storyRepository,
                analytics = analytics,
                source = ReportSource.CHAT,
                emit = { change -> dispatchEvent(ChatRoomEvent.Report(change)) },
                notify = { submitted ->
                    dispatchEffect(
                        if (submitted) ChatRoomEffect.ShowReportSubmitted else ChatRoomEffect.ShowReportFailed,
                    )
                },
            )
        private var choicesJob: Job? = null
        private var deleteJob: Job? = null

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

        /** 전송하며 비운 입력. 턴이 열리지 못하면 그대로 되돌린다. */
        private var sent: SentInput? = null

        /** 재생성 중인 턴. 실패 복구가 이어쓰기와 갈리는 지점을 이 값이 가른다. */
        private var regeneratingTurnId: Long? = null

        private val random = Random.Default

        private val suggestions: ChatSuggestions
            get() = chatSuggestions(turns.lastOrNull(), suggestedInputs, choicesEnabled)

        init {
            analytics.track(AnalyticsEvent.ChatViewed(chatId))
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
            intent.analyticsEvent(chatId, composer)?.let(analytics::track)
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

                ChatRoomIntent.Sent -> {
                    val userInput = composer.toUserInput()
                    val origin = composerOrigin(userInput, filled)
                    startTurn(userInput, inputMode = composer.mode.messageInputMode) {
                        chatRepository.turnStream(chatId, userInput, origin)
                    }
                }

                ChatRoomIntent.DeleteConfirmed -> delete()

                is ChatRoomIntent.Report ->
                    report.handle(intent.action, uiState.value.storyId, uiState.value.report)

                is ChatRoomIntent.RegenerateRequested -> {
                    // 화면이 본 마지막 턴과 지금 마지막 턴이 다르면 낡은 클릭이다.
                    val turn = turns.lastOrNull()?.takeIf { last -> last.id == intent.turnId } ?: return
                    analytics.track(AnalyticsEvent.RegenerateTurnButtonClicked(chatId, turnNumber = turns.size))
                    startTurn(userInput = turn.userInput, regeneratedTurnId = turn.id) {
                        chatRepository.regenerateTurn(chatId, turn.id)
                    }
                }

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
                                    storyId = result.value.storyId,
                                    storyTitle = result.value.storyTitle,
                                    prologue = result.value.prologue,
                                    turns = turns,
                                    suggestedInputs = suggestedInputs,
                                ),
                            )
                            // 보이는 순간 열람으로 기록한다. 상태는 그대로 둬 이 방에서는 계속 보인다.
                            if (hintUnseen && turns.isEmpty()) preferences.markChoicesHintSeen()
                        }

                        is DomainResult.Failure -> {
                            analytics.track(AnalyticsEvent.ChatLoadErrorShown(chatId))
                            dispatchEvent(ChatRoomEvent.LoadFailed)
                        }
                    }
                }
        }

        /**
         * 채팅을 지운다.
         *
         * **확정하기 전에 진행 중인 스트림을 끊는다** — 지운 채팅에 턴을 계속 붙이면 서버가 거절하고,
         * 그 실패 안내가 삭제 안내와 겹쳐 뜬다.
         *
         * 없는 채팅(404)은 데이터 계층이 이미 성공으로 접어 준다.
         */
        private fun delete() {
            if (deleteJob?.isActive == true) return
            deleteJob =
                viewModelScope.launch {
                    dispatchEvent(ChatRoomEvent.DeleteStarted)
                    streamJob?.cancel()
                    choicesJob?.cancel()
                    when (chatRepository.deleteChat(chatId)) {
                        is DomainResult.Success -> dispatchEffect(ChatRoomEffect.ChatDeleted)

                        is DomainResult.Failure -> {
                            dispatchEvent(ChatRoomEvent.DeleteFailed)
                            dispatchEffect(ChatRoomEffect.ShowDeleteFailed)
                        }
                    }
                }
        }

        private suspend fun handleSuggestion(intent: ChatRoomIntent) {
            when (intent) {
                // 추천 문장은 입력창을 거치지 않고 바로 보낸다.
                is ChatRoomIntent.SuggestionSent -> {
                    val current = suggestions
                    val text =
                        current.items
                            .getOrNull(intent.position)
                            ?.trim()
                            .orEmpty()
                    if (text.isEmpty()) return
                    val userInput = normalizeSuggestion(text)
                    val origin = choiceOrigin(intent.position, current.sourceTurnId)
                    analytics.track(
                        AnalyticsEvent.ChoiceOptionSelected(chatId, turns.nextTurnNumber(), intent.position),
                    )
                    startTurn(userInput, inputMode = MessageInputMode.CHOICE) {
                        chatRepository.turnStream(chatId, userInput, origin)
                    }
                }

                is ChatRoomIntent.SuggestionFilled -> {
                    val current = suggestions
                    val text = current.items.getOrNull(intent.position) ?: return
                    analytics.track(
                        AnalyticsEvent.ChoiceFillButtonClicked(chatId, turns.nextTurnNumber(), intent.position),
                    )
                    filled =
                        FilledSuggestion(
                            text = text,
                            position = intent.position,
                            sourceTurnId = current.sourceTurnId,
                        )
                    updateComposer(composer.filledWith(text))
                }

                ChatRoomIntent.LockedComposerTapped ->
                    if (uiState.value.isStreaming) dispatchEffect(ChatRoomEffect.ShowComposerLocked)

                ChatRoomIntent.RandomSuggestionSent ->
                    randomSuggestionPosition(suggestions.items, random)?.let { position ->
                        handleSuggestion(ChatRoomIntent.SuggestionSent(position))
                    }

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
            regeneratedTurnId: Long? = null,
            inputMode: MessageInputMode? = null,
            stream: () -> Flow<ChatStreamEvent>,
        ) {
            // 스트림을 만들기 전에 막는다 — 진행 중에 또 만들면 버린 요청이 서버 기록에 남는다.
            if (streamJob?.isActive == true) return
            if (userInput.isBlank()) return
            // 실제로 열리는 턴만 센다 — 잠금·빈 입력으로 걸러진 탭은 전송이 아니다.
            if (inputMode != null) {
                analytics.track(AnalyticsEvent.MessageInputSubmitted(chatId, turns.nextTurnNumber(), inputMode))
            }

            // 상태를 바꾸기 전에 만든다 — 출처 판정이 아직 비우지 않은 채우기 기억을 봐야 한다.
            val events = stream()
            if (regeneratedTurnId == null) {
                // 이어쓰기만 컴포저를 비운다 — 재생성은 쓰던 초안을 건드리지 않는다.
                // 다음 턴의 입력이 앞 턴에서 채운 문장과 대조되면 안 된다.
                sent = SentInput(composer = composer, filled = filled)
                filled = null
                composer = composer.cleared()
            } else {
                sent = null
            }
            regeneratingTurnId = regeneratedTurnId
            choicesJob?.cancel()
            isStreaming = true
            streamJob =
                viewModelScope.launch {
                    dispatchEvent(
                        if (regeneratedTurnId == null) {
                            ChatRoomEvent.SendStarted(userInput = userInput, composer = composer)
                        } else {
                            ChatRoomEvent.RegenerateStarted(turnId = regeneratedTurnId, userInput = userInput)
                        },
                    )
                    events.collectBatched { event -> handleStreamEvent(event) }
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
                    regeneratingTurnId = null
                }

                is ChatStreamEvent.Failed -> handleStreamFailure(event)

                ChatStreamEvent.Interrupted -> {
                    isStreaming = false
                    analytics.track(AnalyticsEvent.StreamErrorShown(chatId, turns.activeTurnNumber(regeneratingTurnId)))
                    dispatchEvent(ChatRoomEvent.StreamCleared)
                    dispatchEffect(ChatRoomEffect.ShowStreamFailure(null))
                    // 서버 저장·교체 여부가 불명이라 임의로 복원하지 않고 확정 상태를 다시 읽는다.
                    refreshTurns(confirmed = false)
                    regeneratingTurnId = null
                }
            }
        }

        /**
         * `error` 사건의 복구.
         *
         * **블록을 걷는 것이 곧 복원이다** — 이어쓰기면 낙관적으로 붙였던 밴드가 사라지고, 재생성이면
         * 자리를 지키고 있던 대상 턴이 그대로 다시 보인다. 서버가 교체하지 않았음이 보장되는 실패라
         * 다시 읽지 않는다.
         *
         * 다만 **409 는 이미 새 턴이 붙은 낡은 화면**이라 되살릴 기존 본문이 정본이 아니다. 그때만
         * 확정 상태를 다시 읽는다.
         */
        private suspend fun handleStreamFailure(event: ChatStreamEvent.Failed) {
            isStreaming = false
            dispatchEvent(ChatRoomEvent.StreamCleared)
            // 열지 못한 턴의 입력은 컴포저로 되돌린다 — 이프가 모자라거나 요청이 닿지 못한 실패에
            // 사용자가 쓴 문장을 없앨 이유가 없다. 받는 동안 컴포저는 잠겨 있어 새 초안을 덮지 않는다.
            sent?.let { restored ->
                sent = null
                filled = restored.filled
                updateComposer(restored.composer)
            }
            val status = (event.error as? DomainError.Server)?.status
            if (status == HTTP_PAYMENT_REQUIRED) {
                analytics.track(AnalyticsEvent.CreditShortageShown(CreditShortageTrigger.CHAT_TURN))
                dispatchEffect(ChatRoomEffect.ShowCreditRequired)
            } else {
                analytics.track(AnalyticsEvent.StreamErrorShown(chatId, turns.activeTurnNumber(regeneratingTurnId)))
                dispatchEffect(ChatRoomEffect.ShowStreamFailure(event.message))
            }
            if (regeneratingTurnId != null && status == HTTP_CONFLICT) refreshTurns(confirmed = false)
            regeneratingTurnId = null
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
                        // 재생성은 대상 턴이 목록에 그대로 있어 블록만 걷으면 기존 본문이 돌아온다.
                        // 이어쓰기는 새 턴이 목록에 없으므로 블록을 남기고 잠금만 푼다.
                        dispatchEvent(
                            if (regeneratingTurnId != null) {
                                ChatRoomEvent.StreamCleared
                            } else {
                                ChatRoomEvent.StreamSettled
                            },
                        )
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

        private companion object {
            /** 이프가 모자랄 때의 응답. */
            const val HTTP_PAYMENT_REQUIRED = 402

            /** 서버가 보는 마지막 턴과 재생성 대상이 다를 때의 응답. */
            const val HTTP_CONFLICT = 409
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
                storyId = event.storyId,
                storyTitle = event.storyTitle,
                prologue = event.prologue,
                turns = event.turns,
                suggestedInputs = event.suggestedInputs,
            )

        is ChatRoomEvent.Report -> state.copy(report = state.report.reduceReport(event.change))

        ChatRoomEvent.LoadFailed -> state.copy(isLoading = false, loadFailed = true)

        is ChatRoomEvent.PreferencesLoaded ->
            state.copy(
                composer = event.composer,
                choicesEnabled = event.choicesEnabled,
                choicesHintUnseen = event.hintUnseen,
            )

        is ChatRoomEvent.ComposerChanged -> state.copy(composer = event.composer)

        is ChatRoomEvent.ChoicesEnabledChanged -> state.copy(choicesEnabled = event.enabled)

        ChatRoomEvent.DeleteStarted -> state.copy(isDeleting = true)

        ChatRoomEvent.DeleteFailed -> state.copy(isDeleting = false)

        else -> reduceTurn(state, event)
    }

/** 전송하며 비운 컴포저와 채우기 기억. 되돌릴 때 둘이 함께 돌아가야 출처 판정이 어긋나지 않는다. */
private data class SentInput(
    val composer: ChatComposerState,
    val filled: FilledSuggestion?,
)

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

        is ChatRoomEvent.RegenerateStarted ->
            state.copy(
                streaming = StreamingTurn(userInput = event.userInput),
                isStreaming = true,
                regeneratingTurnId = event.turnId,
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
            state.copy(
                turns = event.turns,
                streaming = null,
                isStreaming = false,
                regeneratingTurnId = null,
            )

        is ChatRoomEvent.TurnsRefreshed -> state.copy(turns = event.turns, choicesProgress = null)

        ChatRoomEvent.StreamCleared ->
            state.copy(streaming = null, isStreaming = false, regeneratingTurnId = null)

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

/** 이어쓰기 스트림. 출처는 서버가 선택 결과를 기록하는 데만 쓰인다. */
private fun ChatRepository.turnStream(
    chatId: String,
    userInput: String,
    origin: SuggestionOrigin,
): Flow<ChatStreamEvent> =
    streamTurn(
        chatId = chatId,
        userInput = userInput,
        userSource = origin.userSource,
        sourceTurnId = origin.sourceTurnId,
        choiceOrder = origin.choiceOrder,
    )

private fun app.manyak.common.entity.chat.ChatTurn.toUi(): ChatRoomTurn =
    ChatRoomTurn(
        id = id,
        userInput = userInput,
        aiOutput = aiOutput,
        choices = choices,
        reachedEnding = reachedEnding,
    )

/** 다음에 열릴 턴의 번호. 웹과 같이 1부터 센다. */
private fun List<ChatRoomTurn>.nextTurnNumber(): Int = size + 1

/** 진행 중인 턴의 번호. 재생성이면 그 자리, 이어쓰기면 새 자리다. */
private fun List<ChatRoomTurn>.activeTurnNumber(regeneratingTurnId: Long?): Int =
    if (regeneratingTurnId != null) size else size + 1

private val ChatInputMode.messageInputMode: MessageInputMode
    get() = if (this == ChatInputMode.BLOCK) MessageInputMode.BLOCK else MessageInputMode.PLAIN

/**
 * 컴포저 조작 의도가 만드는 분석 이벤트. 상태를 바꾸기 전의 [composer] 를 봐야 하므로 처리기 첫 줄에서 부른다 —
 * 같은 모드 재선택은 이동이 아니고, 지우는 블럭의 종류는 지우기 전에만 알 수 있다.
 */
private fun ChatRoomIntent.analyticsEvent(
    chatId: String,
    composer: ChatComposerState,
): AnalyticsEvent? =
    when (this) {
        ChatRoomIntent.Retry -> AnalyticsEvent.ChatRetryButtonClicked(chatId)
        is ChatRoomIntent.BlockAdded -> AnalyticsEvent.AddBlockButtonClicked(chatId, type.name.lowercase())
        is ChatRoomIntent.BlockRemoved ->
            composer.blocks
                .firstOrNull { block -> block.id == id }
                ?.let { block -> AnalyticsEvent.RemoveBlockButtonClicked(chatId, block.type.name.lowercase()) }
        is ChatRoomIntent.InputModeChanged ->
            AnalyticsEvent.ChatInputModeSelected(chatId, mode.name.lowercase()).takeIf { mode != composer.mode }
        is ChatRoomIntent.ChoicesEnabledChanged -> AnalyticsEvent.ChoicesToggleClicked(chatId, enabled)
        else -> null
    }
