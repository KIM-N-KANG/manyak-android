package app.manyak.create.additionalinfo.presentation

import androidx.lifecycle.viewModelScope
import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.analytics.entity.CompletionStage
import app.manyak.analytics.entity.CreateStep
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.create.entity.StoryCompletionCommand
import app.manyak.create.presentation.state.FunnelExitWarning
import app.manyak.create.presentation.state.StorylineGenerationState
import app.manyak.create.presentation.state.StorylineGenerationStore
import app.manyak.create.presentation.state.resultOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
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
    /** 완성 요청을 영속하는 중. 연타를 요청 하나로 묶고, 성공하면 화면이 제작 탭으로 대체된다. */
    val isSubmitting: Boolean = false,
    /** 이탈을 막고 띄운 경고. */
    val exitWarning: FunnelExitWarning? = null,
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

    /** 헤더의 임시 저장 버튼과 백그라운드 전환. */
    data object SaveDraft : CreateAdditionalInfoIntent

    sealed interface FunnelNavigation : CreateAdditionalInfoIntent

    /** 앱 바 닫기·디바이스 뒤로가기 — 퍼널 이탈. */
    data object LeaveFunnel : FunnelNavigation

    /** 이탈 경고의 "나가기"·"그만 만들기". */
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

    data object SubmissionStarted : CreateAdditionalInfoEvent

    data object SubmissionFailed : CreateAdditionalInfoEvent

    /** 프로세스 재시작·재개 진입 복원으로 생성 결과·입력 스냅숏이 늦게 도착했다. */
    data class SnapshotRestored(
        val snapshot: CreateAdditionalInfoUiState,
    ) : CreateAdditionalInfoEvent

    data class ExitWarningChanged(
        val warning: FunnelExitWarning?,
    ) : CreateAdditionalInfoEvent

    data class ReselectWarningVisibleChanged(
        val visible: Boolean,
    ) : CreateAdditionalInfoEvent
}

sealed interface CreateAdditionalInfoEffect {
    /** 완성 요청을 영속했다 — 퍼널을 걷어내고 제작 탭으로 돌아간다. 서버 응답은 기다리지 않는다. */
    data object ReturnToStudioAfterSubmission : CreateAdditionalInfoEffect

    /** 요청을 영속하지 못했다. 입력은 그대로 남고 아무것도 전송되지 않았다. */
    data object ShowSubmissionFailure : CreateAdditionalInfoEffect

    /** 퍼널 이탈 확정. */
    data object ExitFunnel : CreateAdditionalInfoEffect

    /** "다시 선택하기" 확정 — 스토리라인 단계로 pop 한다. */
    data object NavigateBackToStoryline : CreateAdditionalInfoEffect
}

@HiltViewModel
class CreateAdditionalInfoViewModel
    @Inject
    constructor(
        private val storylineGenerationStore: StorylineGenerationStore,
        private val analytics: Analytics,
    ) : MviViewModel<
            CreateAdditionalInfoIntent,
            CreateAdditionalInfoUiState,
            CreateAdditionalInfoEvent,
            CreateAdditionalInfoEffect,
        >(
            storylineGenerationStore.toInitialAdditionalInfoState(),
        ) {
        /**
         * 이탈·초기화·제출 처리 중. 이 전이는 스토어의 진행 미러를 비우는데, 그 사이 화면 상태를
         * 미러링하면 방금 저장한 재료를 빈 값으로 덮어쓴다. 이후 미러링을 멈춘다.
         */
        private var isLeaving = false

        val draftSave = storylineGenerationStore.draftSave

        init {
            analytics.track(AnalyticsEvent.StoryCreateStepViewed(CreateStep.ADDITIONAL_INFO))
            viewModelScope.launch {
                // 프로세스 재시작·재개 진입이면 진행 레코드에서 스토어를 먼저 복원한다.
                storylineGenerationStore.ensureRestored()
                if (uiState.value.isRestoring) {
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

        override suspend fun handleIntent(intent: CreateAdditionalInfoIntent) {
            val state = uiState.value
            when (intent) {
                is CreateAdditionalInfoIntent.ToggleRecommendation -> {
                    val selected = intent.text !in state.selectedRecommendations
                    analytics.track(AnalyticsEvent.RecommendedInfoClicked(selected))
                    dispatchEvent(CreateAdditionalInfoEvent.RecommendationToggled(intent.text))
                }

                CreateAdditionalInfoIntent.AddInput ->
                    if (state.canAddInput) {
                        analytics.track(AnalyticsEvent.AdditionalInfoAddButtonClicked)
                        dispatchEvent(CreateAdditionalInfoEvent.InputAdded)
                    }

                is CreateAdditionalInfoIntent.RemoveInput ->
                    if (state.additionalInfos.any { it.id == intent.inputId }) {
                        analytics.track(AnalyticsEvent.AdditionalInfoRemoveButtonClicked)
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

                CreateAdditionalInfoIntent.SaveDraft -> {
                    analytics.track(AnalyticsEvent.DraftSaved(CreateStep.ADDITIONAL_INFO))
                    // 미러링 수집이 UiState 보다 늦게 돌아도 저장에는 지금 입력이 들어가야 한다.
                    storylineGenerationStore.updateAdditionalInfoProgress(
                        inputs = state.additionalInfos.map(AdditionalInfoInput::value),
                        recommendations = state.selectedRecommendations.toList(),
                    )
                    storylineGenerationStore.saveDraft()
                }

                is CreateAdditionalInfoIntent.FunnelNavigation -> handleFunnelNavigation(intent, state)
            }
        }

        private suspend fun handleFunnelNavigation(
            intent: CreateAdditionalInfoIntent.FunnelNavigation,
            state: CreateAdditionalInfoUiState,
        ) {
            when (intent) {
                CreateAdditionalInfoIntent.LeaveFunnel -> leaveFunnel(confirmed = false)

                CreateAdditionalInfoIntent.ConfirmLeaveFunnel -> {
                    analytics.track(AnalyticsEvent.CreateExitButtonClicked(CreateStep.ADDITIONAL_INFO))
                    leaveFunnel(confirmed = true)
                }

                CreateAdditionalInfoIntent.DismissExitWarning ->
                    dispatchEvent(CreateAdditionalInfoEvent.ExitWarningChanged(null))

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
         * 닫기는 상태와 무관하게 늘 확인을 거친다. 저장하지 않은 편집이 있으면 미저장 경고, 저장할 것도
         * 저장된 것도 없으면 소실 경고, 저장분·진행 중 레코드만 남았으면 잃는 것 없이 닫는다는 확인이다.
         */
        private suspend fun leaveFunnel(confirmed: Boolean) {
            val warning =
                when {
                    confirmed -> null
                    storylineGenerationStore.draftSave.value.hasUnsavedChanges ->
                        FunnelExitWarning.UNSAVED_CHANGES

                    storylineGenerationStore.hasContentToPreserve() -> FunnelExitWarning.SAVED_DRAFT
                    else -> FunnelExitWarning.NOTHING_TO_PRESERVE
                }
            if (warning != null) {
                dispatchEvent(CreateAdditionalInfoEvent.ExitWarningChanged(warning))
                return
            }
            if (confirmed) dispatchEvent(CreateAdditionalInfoEvent.ExitWarningChanged(null))
            isLeaving = true
            storylineGenerationStore.leaveFunnel()
            dispatchEffect(CreateAdditionalInfoEffect.ExitFunnel)
        }

        /**
         * 스토리라인 단계로 되돌아간다. 미러링을 먼저 끊고 스토어를 비운다 — 순서를 바꾸면
         * 남아 있던 화면 상태가 방금 비운 진행을 다시 채운다.
         */
        private suspend fun confirmReselect() {
            analytics.track(AnalyticsEvent.BackToStorylineButtonClicked)
            isLeaving = true
            storylineGenerationStore.clearAdditionalInfoProgress()
            dispatchEffect(CreateAdditionalInfoEffect.NavigateBackToStoryline)
        }

        /**
         * 완성 제출. 요청을 영속하는 데 성공해야 실행자가 전송을 시작하고 화면이 제작 탭으로 돌아간다.
         * 영속 실패는 아무것도 보내지 않은 상태이므로 입력을 그대로 두고 알린다.
         */
        private suspend fun completeStory(
            state: CreateAdditionalInfoUiState,
            storylineIndex: Int,
        ) {
            if (state.isSubmitting) return
            val simpleCreationId = state.simpleCreationId ?: return
            val storylineId = state.storylines.getOrNull(storylineIndex)?.id ?: return
            dispatchEvent(CreateAdditionalInfoEvent.SubmissionStarted)
            val command =
                buildCompletionCommand(
                    previous = storylineGenerationStore.lastCompletionCommand,
                    simpleCreationId = simpleCreationId,
                    storylineId = storylineId,
                    additionalInfos = state.submittedAdditionalInfos(),
                )
            // UiState가 이벤트 채널보다 먼저 읽힌 직후에도 요청에는 최신 입력이 들어가야 한다.
            storylineGenerationStore.updateAdditionalInfoProgress(
                inputs = state.additionalInfos.map(AdditionalInfoInput::value),
                recommendations = state.selectedRecommendations.toList(),
            )
            analytics.track(AnalyticsEvent.StoryCompletionRequested(simpleCreationId.toString()))
            // 제출이 스토어를 비우므로 그 뒤의 미러링을 먼저 끊는다 — 남은 화면 상태가 빈 스토어를 다시 채우면 안 된다.
            isLeaving = true
            if (storylineGenerationStore.submitCompletion(command)) {
                dispatchEffect(CreateAdditionalInfoEffect.ReturnToStudioAfterSubmission)
            } else {
                isLeaving = false
                analytics.track(AnalyticsEvent.CompleteErrorShown(CompletionStage.STORY))
                dispatchEvent(CreateAdditionalInfoEvent.SubmissionFailed)
                dispatchEffect(CreateAdditionalInfoEffect.ShowSubmissionFailure)
            }
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

                CreateAdditionalInfoEvent.SubmissionStarted -> state.copy(isSubmitting = true)

                CreateAdditionalInfoEvent.SubmissionFailed -> state.copy(isSubmitting = false)

                is CreateAdditionalInfoEvent.SnapshotRestored ->
                    // 복원 스냅숏이 늦게 도착하는 동안 화면 조작은 불가능했으므로 통째로 대체한다.
                    // 되살릴 레코드가 없었더라도 복원 대기는 여기서 끝난다 — 빈 화면으로 남지 않는다.
                    event.snapshot.copy(
                        isRestoring = false,
                        isSubmitting = state.isSubmitting,
                        exitWarning = state.exitWarning,
                        showReselectWarningDialog = state.showReselectWarningDialog,
                    )

                is CreateAdditionalInfoEvent.ExitWarningChanged -> state.copy(exitWarning = event.warning)

                is CreateAdditionalInfoEvent.ReselectWarningVisibleChanged ->
                    state.copy(showReselectWarningDialog = event.visible)
            }
    }

/**
 * 같은 페이로드의 재시도는 requestId 를 재사용한다 — 서버가 이미 완성했다면 AI 재호출 없이
 * 저장된 결과를 돌려받아 중복 생성·중복 과금이 없다(멱등 계약). [previous] 는 영속 실패 뒤의
 * 재시도와 이전 버전 임시 저장본이 남긴 마지막 명령이다.
 */
private fun buildCompletionCommand(
    previous: StoryCompletionCommand?,
    simpleCreationId: Long,
    storylineId: Long,
    additionalInfos: List<String>,
): StoryCompletionCommand {
    val reusableRequestId =
        previous
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
