package app.manyak.feature.create

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.chat.ChatRepository
import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.CreationRequestSnapshot
import app.manyak.core.domain.story.PendingStoryCreationStore
import app.manyak.core.domain.story.StoryCompletionCommand
import app.manyak.core.domain.story.StoryCreationRepository
import app.manyak.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** [id]는 서버 ID가 아닌 화면 로컬 식별자다. 입력 삭제·변경의 대상 지정에 쓴다. */
data class AdditionalInfoInput(
    val id: Long,
    val value: String = "",
)

/** 화면이 그릴 스토리라인 본문과 추천 추가 정보. 생성 결과 순번대로 담긴다. */
data class AdditionalInfoStoryline(
    /** 완성 요청에 싣는 스토리라인 서버 ID. */
    val id: Long,
    val text: String,
    val recommendedInfos: List<String>,
)

/**
 * 완성 실패 사유. 앱은 로그인 필수라 402 는 회원 크레딧 부족뿐이며, 크레딧 획득 UI 가
 * 생기기 전까지 문구만 구분해 안내한다.
 */
enum class CompletionFailure {
    GENERAL,
    CREDIT,
}

data class CreateAdditionalInfoUiState(
    /**
     * 스토어가 비어 있어 진행 레코드 복원을 기다리는 중. 어떤 스토리라인을 골랐고 무엇을 입력해
     * 뒀는지 아직 모르므로 아무것도 그리지 않는다 — 재개 진입에서 빈 입력 화면이 스쳐 지나가지
     * 않게 한다.
     */
    val isRestoring: Boolean = false,
    /** 완성 요청에 싣는 간편 제작 진행 ID. 복원 전 잠깐을 제외하면 항상 있다. */
    val simpleCreationId: Long? = null,
    /** 생성 결과 스냅숏. 이 화면에 머무는 동안 재생성은 일어나지 않는다. */
    val storylines: List<AdditionalInfoStoryline> = emptyList(),
    /** 선택한 추천 추가 정보 텍스트. 완성 요청에 자유 텍스트보다 앞서 실린다. */
    val selectedRecommendations: Set<String> = emptySet(),
    val additionalInfos: List<AdditionalInfoInput> =
        List(INITIAL_INPUT_COUNT) { index -> AdditionalInfoInput(id = index.toLong()) },
    val nextInputId: Long = INITIAL_INPUT_COUNT.toLong(),
    /** 완성 요청 진행 중. 입력 화면 대신 완성 로딩을 그린다. */
    val isCompletingStory: Boolean = false,
    val completionFailure: CompletionFailure? = null,
    /** 보존할 내용 없이 이탈을 시도해 소실 경고 다이얼로그를 띄운 상태. */
    val showExitWarningDialog: Boolean = false,
    /** "다시 선택하기"가 추가 정보를 버린다고 알리는 중. */
    val showReselectWarningDialog: Boolean = false,
) {
    val canAddInput: Boolean get() = additionalInfos.size < INPUT_MAX_COUNT

    /** 버리면 아쉬운 추가 정보가 있는지. 빈 입력 칸만 있는 상태는 아니다. */
    val hasAdditionalInfo: Boolean
        get() = selectedRecommendations.isNotEmpty() || additionalInfos.any { it.value.isNotBlank() }

    companion object {
        const val INITIAL_INPUT_COUNT: Int = 3
        const val INPUT_MAX_COUNT: Int = 10
        const val INPUT_MAX_LENGTH: Int = 100
    }
}

sealed interface CreateAdditionalInfoIntent {
    data class ToggleRecommendation(
        val text: String,
    ) : CreateAdditionalInfoIntent

    data object AddInput : CreateAdditionalInfoIntent

    data class RemoveInput(
        val inputId: Long,
    ) : CreateAdditionalInfoIntent

    data class ChangeInput(
        val inputId: Long,
        val value: String,
    ) : CreateAdditionalInfoIntent

    data class CompleteStory(
        val storylineIndex: Int,
    ) : CreateAdditionalInfoIntent

    sealed interface FunnelNavigation : CreateAdditionalInfoIntent

    /** 앱 바 닫기·디바이스 뒤로가기 — 퍼널 이탈. */
    data object LeaveFunnel : FunnelNavigation

    /** 소실 경고 다이얼로그의 "그만 만들기". */
    data object ConfirmLeaveFunnel : FunnelNavigation

    data object DismissExitWarning : FunnelNavigation

    /** 하단 "다시 선택하기" — 스토리라인 단계 복귀. */
    data object ReselectStoryline : FunnelNavigation

    /** 초기화 경고 다이얼로그의 "다시 선택하기". */
    data object ConfirmReselect : FunnelNavigation

    data object DismissReselectWarning : FunnelNavigation
}

sealed interface CreateAdditionalInfoEvent {
    data class RecommendationToggled(
        val text: String,
    ) : CreateAdditionalInfoEvent

    data object InputAdded : CreateAdditionalInfoEvent

    data class InputRemoved(
        val inputId: Long,
    ) : CreateAdditionalInfoEvent

    data class InputChanged(
        val inputId: Long,
        val value: String,
    ) : CreateAdditionalInfoEvent

    data object CompletionStarted : CreateAdditionalInfoEvent

    data class CompletionFailed(
        val failure: CompletionFailure,
    ) : CreateAdditionalInfoEvent

    /** 프로세스 재시작·재개 진입 복원으로 생성 결과·입력 스냅숏이 늦게 도착했다. */
    data class SnapshotRestored(
        val snapshot: CreateAdditionalInfoUiState,
    ) : CreateAdditionalInfoEvent

    data class ExitWarningVisibleChanged(
        val visible: Boolean,
    ) : CreateAdditionalInfoEvent

    data class ReselectWarningVisibleChanged(
        val visible: Boolean,
    ) : CreateAdditionalInfoEvent
}

sealed interface CreateAdditionalInfoEffect {
    /** 완성 성공 — 생성된 채팅방으로 진입하며 퍼널을 닫는다(웹의 채팅 화면 `replace` 대응). */
    data class EnterChatAfterCompletion(
        val chatId: String,
    ) : CreateAdditionalInfoEffect

    /** 퍼널 이탈 확정. 내용이 남았으면 "임시 저장되었어요" 토스트를 함께 띄운다. */
    data class ExitFunnel(
        val contentPreserved: Boolean,
    ) : CreateAdditionalInfoEffect

    /** "다시 선택하기" 확정 — 스토리라인 단계로 pop 한다. */
    data object NavigateBackToStoryline : CreateAdditionalInfoEffect
}

@HiltViewModel
class CreateAdditionalInfoViewModel
    @Inject
    constructor(
        private val storylineGenerationStore: StorylineGenerationStore,
        private val storyCreationRepository: StoryCreationRepository,
        private val chatRepository: ChatRepository,
        private val pendingCreationStore: PendingStoryCreationStore,
    ) : MviViewModel<
            CreateAdditionalInfoIntent,
            CreateAdditionalInfoUiState,
            CreateAdditionalInfoEvent,
            CreateAdditionalInfoEffect,
        >(
            storylineGenerationStore.toInitialAdditionalInfoState(),
        ) {
        private var completeJob: Job? = null

        /**
         * 스토리 완성은 성공했는데 채팅 생성이 실패한 경우의 스토리 ID.
         * 재시도는 스토리 완성을 건너뛰고 채팅 생성만 재호출해 스토리를 중복 생성하지 않는다.
         */
        private var completedStoryId: String? = null

        /**
         * 이탈·초기화 처리 중. 이 전이는 스토어의 진행 미러를 비우는데, 그 사이 화면 상태를
         * 미러링하면 방금 저장한 재료를 빈 값으로 덮어쓴다. 이후 미러링을 멈춘다.
         */
        private var isLeaving = false

        val draftSaveStatus = storylineGenerationStore.draftSaveStatus

        init {
            viewModelScope.launch {
                // 프로세스 재시작·재개 진입이면 진행 레코드에서 스토어를 먼저 복원한다.
                storylineGenerationStore.ensureRestored()
                if (uiState.value.isRestoring) {
                    // 완성 진행 중이던 레코드는 입력 화면이 아니라 완성 로딩으로 이어진다. 복구
                    // 폴링이 같은 전이를 내지만 한 박자 늦어, 입력 화면이 한 프레임 비친다.
                    if (storylineGenerationStore.completionRecoveryTarget.value != null) {
                        dispatchEvent(CreateAdditionalInfoEvent.CompletionStarted)
                    }
                    dispatchEvent(
                        CreateAdditionalInfoEvent.SnapshotRestored(
                            storylineGenerationStore.toAdditionalInfoSnapshot(),
                        ),
                    )
                }
            }
            viewModelScope.launch {
                // 입력·추천 선택을 스토어에 미러링해 이탈 시 임시 저장 재료로 쓴다.
                uiState.collect { state ->
                    // 복원 전의 빈 입력을 미러링하면 되살릴 임시 저장 재료를 덮어쓴다.
                    if (state.isRestoring || isLeaving) return@collect
                    storylineGenerationStore.updateAdditionalInfoProgress(
                        inputs = state.additionalInfos.map(AdditionalInfoInput::value),
                        recommendations = state.selectedRecommendations.toList(),
                    )
                }
            }
        }

        /**
         * 응답을 못 받았거나 409 로 거절된 완성 요청의 복구 폴링. 화면이 STARTED 동안 수집해
         * 백그라운드에서 멈추고 복귀 시 재개된다. 복구 대상이 없으면 아무 일도 하지 않는다.
         */
        suspend fun driveCompletionRecovery() {
            // collectLatest 를 쓰면 블록 안에서 target 을 비우는 순간 채팅 생성 연결이 취소된다.
            storylineGenerationStore.completionRecoveryTarget.collect { command ->
                if (command == null) return@collect
                dispatchEvent(CreateAdditionalInfoEvent.CompletionStarted)
                pollCompletion(command)
            }
        }

        private suspend fun pollCompletion(command: StoryCompletionCommand) {
            while (true) {
                when (val result = storyCreationRepository.creationRequest(command.requestId)) {
                    is DomainResult.Success ->
                        when (val snapshot = result.value) {
                            CreationRequestSnapshot.Pending -> Unit

                            is CreationRequestSnapshot.StoryReady -> {
                                completedStoryId = snapshot.story.id
                                storylineGenerationStore.clearCompletionRecovery()
                                // 원 성공 경로의 부수효과 — 채팅 생성으로 이어 붙인다.
                                startChat(snapshot.story.id)
                                return
                            }

                            is CreationRequestSnapshot.StorylinesReady -> {
                                finishRecoveryAsFailure()
                                return
                            }

                            CreationRequestSnapshot.Failed -> {
                                finishRecoveryAsFailure()
                                return
                            }
                        }

                    is DomainResult.Failure ->
                        // 폴링은 읽기라 네트워크 단절은 다음 주기로 넘기고, 404 를 포함한
                        // 서버 응답 실패는 기존 완성 실패 처리로 합류한다.
                        if (result.error !is DomainError.Network) {
                            finishRecoveryAsFailure()
                            return
                        }
                }
                delay(StorylineGenerationStore.RECOVERY_POLL_INTERVAL_MS)
            }
        }

        private suspend fun finishRecoveryAsFailure() {
            storylineGenerationStore.restoreDraftAfterCompletionFailure()
            dispatchEvent(CreateAdditionalInfoEvent.CompletionFailed(CompletionFailure.GENERAL))
        }

        override suspend fun handleIntent(intent: CreateAdditionalInfoIntent) {
            val state = uiState.value
            when (intent) {
                is CreateAdditionalInfoIntent.ToggleRecommendation ->
                    dispatchEvent(CreateAdditionalInfoEvent.RecommendationToggled(intent.text))

                CreateAdditionalInfoIntent.AddInput ->
                    if (state.canAddInput) {
                        dispatchEvent(CreateAdditionalInfoEvent.InputAdded)
                    }

                is CreateAdditionalInfoIntent.RemoveInput ->
                    if (state.additionalInfos.any { it.id == intent.inputId }) {
                        dispatchEvent(CreateAdditionalInfoEvent.InputRemoved(intent.inputId))
                    }

                is CreateAdditionalInfoIntent.ChangeInput ->
                    dispatchEvent(
                        CreateAdditionalInfoEvent.InputChanged(
                            inputId = intent.inputId,
                            value = intent.value.take(CreateAdditionalInfoUiState.INPUT_MAX_LENGTH),
                        ),
                    )

                is CreateAdditionalInfoIntent.CompleteStory -> completeStory(state, intent.storylineIndex)

                is CreateAdditionalInfoIntent.FunnelNavigation -> handleFunnelNavigation(intent, state)
            }
        }

        private suspend fun handleFunnelNavigation(
            intent: CreateAdditionalInfoIntent.FunnelNavigation,
            state: CreateAdditionalInfoUiState,
        ) {
            when (intent) {
                CreateAdditionalInfoIntent.LeaveFunnel ->
                    if (storylineGenerationStore.hasContentToPreserve()) {
                        isLeaving = true
                        val preserved = storylineGenerationStore.leaveFunnel()
                        dispatchEffect(CreateAdditionalInfoEffect.ExitFunnel(contentPreserved = preserved))
                    } else {
                        dispatchEvent(CreateAdditionalInfoEvent.ExitWarningVisibleChanged(visible = true))
                    }

                CreateAdditionalInfoIntent.ConfirmLeaveFunnel -> {
                    isLeaving = true
                    storylineGenerationStore.leaveFunnel()
                    dispatchEvent(CreateAdditionalInfoEvent.ExitWarningVisibleChanged(visible = false))
                    dispatchEffect(CreateAdditionalInfoEffect.ExitFunnel(contentPreserved = false))
                }

                CreateAdditionalInfoIntent.DismissExitWarning ->
                    dispatchEvent(CreateAdditionalInfoEvent.ExitWarningVisibleChanged(visible = false))

                CreateAdditionalInfoIntent.ReselectStoryline ->
                    if (state.hasAdditionalInfo) {
                        dispatchEvent(CreateAdditionalInfoEvent.ReselectWarningVisibleChanged(visible = true))
                    } else {
                        confirmReselect()
                    }

                CreateAdditionalInfoIntent.ConfirmReselect -> {
                    dispatchEvent(CreateAdditionalInfoEvent.ReselectWarningVisibleChanged(visible = false))
                    confirmReselect()
                }

                CreateAdditionalInfoIntent.DismissReselectWarning ->
                    dispatchEvent(CreateAdditionalInfoEvent.ReselectWarningVisibleChanged(visible = false))
            }
        }

        /**
         * 스토리라인 단계로 되돌아간다. 미러링을 먼저 끊고 스토어를 비운다 — 순서를 바꾸면
         * 남아 있던 화면 상태가 방금 비운 진행을 다시 채운다.
         */
        private suspend fun confirmReselect() {
            isLeaving = true
            storylineGenerationStore.clearAdditionalInfoProgress()
            dispatchEffect(CreateAdditionalInfoEffect.NavigateBackToStoryline)
        }

        private suspend fun completeStory(
            state: CreateAdditionalInfoUiState,
            storylineIndex: Int,
        ) {
            if (state.isCompletingStory || completeJob?.isActive == true) return

            // 스토리는 완성됐고 채팅 생성만 실패한 재시도 — 완성 요청을 건너뛴다(3-1 완성 재시도 분기).
            val alreadyCompletedStoryId = completedStoryId
            if (alreadyCompletedStoryId != null) {
                dispatchEvent(CreateAdditionalInfoEvent.CompletionStarted)
                completeJob = viewModelScope.launch { startChat(alreadyCompletedStoryId) }
                return
            }

            val simpleCreationId = state.simpleCreationId ?: return
            val storylineId = state.storylines.getOrNull(storylineIndex)?.id ?: return
            val command =
                buildCompletionCommand(
                    simpleCreationId = simpleCreationId,
                    storylineId = storylineId,
                    additionalInfos = state.submittedAdditionalInfos(),
                )
            // UiState가 이벤트 채널보다 먼저 읽힌 직후에도 완성 레코드에는 최신 입력이 들어가야 한다.
            storylineGenerationStore.updateAdditionalInfoProgress(
                inputs = state.additionalInfos.map(AdditionalInfoInput::value),
                recommendations = state.selectedRecommendations.toList(),
            )
            // 요청 전에 영속한다 — 응답을 못 받아도 재진입 복구 조회가 이 requestId 를 쓴다.
            storylineGenerationStore.beginCompletion(command)
            dispatchEvent(CreateAdditionalInfoEvent.CompletionStarted)
            completeJob =
                viewModelScope.launch {
                    when (val result = storyCreationRepository.completeStory(command)) {
                        is DomainResult.Success -> {
                            completedStoryId = result.value.id
                            startChat(result.value.id)
                        }

                        is DomainResult.Failure -> onCompletionFailed(command, result.error)
                    }
                }
        }

        private suspend fun onCompletionFailed(
            command: StoryCompletionCommand,
            error: DomainError,
        ) {
            when {
                // 같은 requestId 재시도가 서버 PENDING 과 겹쳤다(409). 실패가 아니라 진행 중이라는
                // 뜻이므로 복구 폴링으로 결과를 되찾는다. 로딩 상태는 폴링 드라이브가 유지한다.
                error.isConflict() -> storylineGenerationStore.requestCompletionRecovery(command)

                // 상태 코드로 응답한 실패는 복구 대상이 아니라 레코드를 지운다. 응답을 못 받은
                // 네트워크 오류만 보존한다(3-1 정리 규칙).
                error is DomainError.Network ->
                    dispatchEvent(CreateAdditionalInfoEvent.CompletionFailed(error.toCompletionFailure()))

                else -> {
                    storylineGenerationStore.restoreDraftAfterCompletionFailure()
                    dispatchEvent(CreateAdditionalInfoEvent.CompletionFailed(error.toCompletionFailure()))
                }
            }
        }

        /**
         * 완성 흐름의 마지막 단계 — 채팅을 만들어 바로 진입한다. 실패는 완성 실패와 같은 인라인
         * 오류이며, 스토리가 이미 완성됐으므로 레코드는 남겨 재진입 복구가 채팅 생성으로 이어지게 한다.
         */
        private suspend fun startChat(storyId: String) {
            when (val result = chatRepository.createChat(storyId)) {
                is DomainResult.Success -> {
                    pendingCreationStore.clear()
                    // 스토어를 비워야 다음 퍼널 이탈에서 이미 완성된 결과가 임시 저장으로 둔갑하지 않는다.
                    storylineGenerationStore.resetAfterCompletion()
                    dispatchEffect(CreateAdditionalInfoEffect.EnterChatAfterCompletion(result.value.id))
                }

                is DomainResult.Failure ->
                    dispatchEvent(CreateAdditionalInfoEvent.CompletionFailed(CompletionFailure.GENERAL))
            }
        }

        /**
         * 같은 페이로드의 재시도는 requestId 를 재사용한다 — 서버가 이미 완성했다면(응답 유실)
         * AI 재호출 없이 저장된 결과를 돌려받아 중복 생성·중복 과금이 없다(멱등 계약).
         * 마지막 명령은 스토어가 기억해 임시 저장 재개 후의 재시도에도 승계된다.
         */
        private fun buildCompletionCommand(
            simpleCreationId: Long,
            storylineId: Long,
            additionalInfos: List<String>,
        ): StoryCompletionCommand {
            val reusableRequestId =
                storylineGenerationStore.lastCompletionCommand
                    ?.takeIf {
                        it.simpleCreationId == simpleCreationId &&
                            it.storylineId == storylineId &&
                            it.additionalInfos == additionalInfos
                    }?.requestId
            return StoryCompletionCommand(
                requestId = reusableRequestId ?: UUID.randomUUID().toString(),
                simpleCreationId = simpleCreationId,
                storylineId = storylineId,
                additionalInfos = additionalInfos,
            )
        }

        override fun reduce(
            state: CreateAdditionalInfoUiState,
            event: CreateAdditionalInfoEvent,
        ): CreateAdditionalInfoUiState =
            when (event) {
                is CreateAdditionalInfoEvent.RecommendationToggled ->
                    state.copy(
                        selectedRecommendations =
                            if (event.text in state.selectedRecommendations) {
                                state.selectedRecommendations - event.text
                            } else {
                                state.selectedRecommendations + event.text
                            },
                    )

                CreateAdditionalInfoEvent.InputAdded ->
                    state.copy(
                        additionalInfos = state.additionalInfos + AdditionalInfoInput(id = state.nextInputId),
                        nextInputId = state.nextInputId + 1,
                    )

                is CreateAdditionalInfoEvent.InputRemoved ->
                    state.copy(additionalInfos = state.additionalInfos.filterNot { it.id == event.inputId })

                is CreateAdditionalInfoEvent.InputChanged ->
                    state.copy(
                        additionalInfos =
                            state.additionalInfos.map { input ->
                                if (input.id == event.inputId) input.copy(value = event.value) else input
                            },
                    )

                CreateAdditionalInfoEvent.CompletionStarted ->
                    state.copy(isCompletingStory = true, completionFailure = null)

                is CreateAdditionalInfoEvent.CompletionFailed ->
                    state.copy(isCompletingStory = false, completionFailure = event.failure)

                is CreateAdditionalInfoEvent.SnapshotRestored ->
                    // 복원 스냅숏이 늦게 도착하는 동안 화면 조작은 불가능했으므로 통째로 대체한다.
                    // 되살릴 레코드가 없었더라도 복원 대기는 여기서 끝난다 — 빈 화면으로 남지 않는다.
                    event.snapshot.copy(
                        isRestoring = false,
                        isCompletingStory = state.isCompletingStory,
                        completionFailure = state.completionFailure,
                        showExitWarningDialog = state.showExitWarningDialog,
                        showReselectWarningDialog = state.showReselectWarningDialog,
                    )

                is CreateAdditionalInfoEvent.ExitWarningVisibleChanged ->
                    state.copy(showExitWarningDialog = event.visible)

                is CreateAdditionalInfoEvent.ReselectWarningVisibleChanged ->
                    state.copy(showReselectWarningDialog = event.visible)
            }
    }

/** 추천 채택분이 앞, 그 뒤로 공백을 정리한 자유 입력이 실린다. 빈 입력은 보내지 않는다. */
private fun CreateAdditionalInfoUiState.submittedAdditionalInfos(): List<String> =
    selectedRecommendations.toList() +
        additionalInfos.map { it.value.trim() }.filter(String::isNotEmpty)

/**
 * 스토어의 현재 상태로 첫 프레임을 만든다.
 *
 * 스토리라인 단계에서 넘어온 진입은 스토어에 결과가 있어 입력 화면이 곧바로 그려지고, 스토어가 빈
 * 재개 진입은 복원 결과를 알기 전까지 [CreateAdditionalInfoUiState.isRestoring] 으로 남는다.
 */
private fun StorylineGenerationStore.toInitialAdditionalInfoState(): CreateAdditionalInfoUiState =
    if (state.value is StorylineGenerationState.Idle) {
        CreateAdditionalInfoUiState(isRestoring = true)
    } else {
        toAdditionalInfoSnapshot()
    }

/** 스토어의 생성 결과·진행 미러로 초기 상태를 만든다. 임시 저장 복원 입력도 여기서 되살아난다. */
internal fun StorylineGenerationStore.toAdditionalInfoSnapshot(): CreateAdditionalInfoUiState {
    val result = state.value.resultOrNull()
    val storylines =
        result
            ?.storylines
            .orEmpty()
            .map { storyline ->
                AdditionalInfoStoryline(
                    id = storyline.id,
                    text = storyline.storyline,
                    recommendedInfos = storyline.recommendedInfos.map { it.text },
                )
            }
    val savedInputs = progress.additionalInfoInputs
    val inputs =
        if (savedInputs.isEmpty()) {
            List(CreateAdditionalInfoUiState.INITIAL_INPUT_COUNT) { index ->
                AdditionalInfoInput(id = index.toLong())
            }
        } else {
            savedInputs.mapIndexed { index, value -> AdditionalInfoInput(id = index.toLong(), value = value) }
        }
    // 추천 선택은 실제 추천 목록에 남아 있는 것만 복원한다 — 다른 스토리라인의 선택이 섞여
    // 완성 요청에 실리는 것을 막는다.
    val availableRecommendations = storylines.flatMapTo(mutableSetOf()) { it.recommendedInfos }
    return CreateAdditionalInfoUiState(
        simpleCreationId = result?.simpleCreationId,
        storylines = storylines,
        selectedRecommendations =
            progress.selectedRecommendations.filterTo(mutableSetOf()) { it in availableRecommendations },
        additionalInfos = inputs,
        nextInputId = inputs.size.toLong(),
    )
}

// 앱은 로그인 필수라 402 는 게스트 한도가 아니라 회원 크레딧 부족이다.
private fun DomainError.toCompletionFailure(): CompletionFailure =
    if (this is DomainError.Server && status == HTTP_PAYMENT_REQUIRED) {
        CompletionFailure.CREDIT
    } else {
        CompletionFailure.GENERAL
    }

private const val HTTP_PAYMENT_REQUIRED = 402
