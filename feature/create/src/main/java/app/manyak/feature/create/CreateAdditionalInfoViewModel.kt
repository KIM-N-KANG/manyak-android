package app.manyak.feature.create

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.StoryCompletionCommand
import app.manyak.core.domain.story.StoryCreationRepository
import app.manyak.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    /** 완성 요청에 싣는 간편 제작 진행 ID. 생성 결과 없이 이 화면에 올 수 없다. */
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
) {
    val canAddInput: Boolean get() = additionalInfos.size < INPUT_MAX_COUNT

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
}

sealed interface CreateAdditionalInfoEffect {
    /** 완성 성공 — 퍼널을 닫는다. 완성된 스토리의 채팅 진입은 채팅 기능 구현과 함께 붙는다. */
    data object ExitFunnelAfterCompletion : CreateAdditionalInfoEffect
}

@HiltViewModel
class CreateAdditionalInfoViewModel
    @Inject
    constructor(
        storylineGenerationStore: StorylineGenerationStore,
        private val storyCreationRepository: StoryCreationRepository,
    ) : MviViewModel<
            CreateAdditionalInfoIntent,
            CreateAdditionalInfoUiState,
            CreateAdditionalInfoEvent,
            CreateAdditionalInfoEffect,
        >(
            storylineGenerationStore.state.value.toAdditionalInfoSnapshot(),
        ) {
        private var completeJob: Job? = null

        /** 같은 페이로드의 재시도가 requestId 를 재사용하도록 마지막 완성 명령을 기억한다. */
        private var lastCompletionCommand: StoryCompletionCommand? = null

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
            }
        }

        private suspend fun completeStory(
            state: CreateAdditionalInfoUiState,
            storylineIndex: Int,
        ) {
            if (state.isCompletingStory || completeJob?.isActive == true) return
            val simpleCreationId = state.simpleCreationId ?: return
            val storylineId = state.storylines.getOrNull(storylineIndex)?.id ?: return
            val command =
                buildCompletionCommand(
                    simpleCreationId = simpleCreationId,
                    storylineId = storylineId,
                    additionalInfos = state.submittedAdditionalInfos(),
                )
            lastCompletionCommand = command
            dispatchEvent(CreateAdditionalInfoEvent.CompletionStarted)
            completeJob =
                viewModelScope.launch {
                    when (val result = storyCreationRepository.completeStory(command)) {
                        is DomainResult.Success ->
                            dispatchEffect(CreateAdditionalInfoEffect.ExitFunnelAfterCompletion)

                        is DomainResult.Failure ->
                            dispatchEvent(
                                CreateAdditionalInfoEvent.CompletionFailed(result.error.toCompletionFailure()),
                            )
                    }
                }
        }

        /**
         * 같은 페이로드의 재시도는 requestId 를 재사용한다 — 서버가 이미 완성했다면(응답 유실)
         * AI 재호출 없이 저장된 결과를 돌려받아 중복 생성·중복 과금이 없다(멱등 계약).
         */
        private fun buildCompletionCommand(
            simpleCreationId: Long,
            storylineId: Long,
            additionalInfos: List<String>,
        ): StoryCompletionCommand {
            val reusableRequestId =
                lastCompletionCommand
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
            }
    }

/** 추천 채택분이 앞, 그 뒤로 공백을 정리한 자유 입력이 실린다. 빈 입력은 보내지 않는다. */
private fun CreateAdditionalInfoUiState.submittedAdditionalInfos(): List<String> =
    selectedRecommendations.toList() +
        additionalInfos.map { it.value.trim() }.filter(String::isNotEmpty)

private fun StorylineGenerationState.toAdditionalInfoSnapshot(): CreateAdditionalInfoUiState {
    val result = resultOrNull()
    return CreateAdditionalInfoUiState(
        simpleCreationId = result?.simpleCreationId,
        storylines =
            result
                ?.storylines
                .orEmpty()
                .map { storyline ->
                    AdditionalInfoStoryline(
                        id = storyline.id,
                        text = storyline.storyline,
                        recommendedInfos = storyline.recommendedInfos.map { it.text },
                    )
                },
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
