package app.manyak.feature.create

import app.manyak.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** [id]는 서버 ID가 아닌 화면 로컬 식별자다. 입력 삭제·변경의 대상 지정에 쓴다. */
data class AdditionalInfoInput(
    val id: Long,
    val value: String = "",
)

/** 화면이 그릴 스토리라인 본문과 추천 추가 정보. 생성 결과 순번대로 담긴다. */
data class AdditionalInfoStoryline(
    val text: String,
    val recommendedInfos: List<String>,
)

data class CreateAdditionalInfoUiState(
    /** 생성 결과 스냅숏. 이 화면에 머무는 동안 재생성은 일어나지 않는다. */
    val storylines: List<AdditionalInfoStoryline> = emptyList(),
    /** 선택한 추천 추가 정보 텍스트. 완성 요청에 자유 텍스트보다 앞서 실린다. */
    val selectedRecommendations: Set<String> = emptySet(),
    val additionalInfos: List<AdditionalInfoInput> =
        List(INITIAL_INPUT_COUNT) { index -> AdditionalInfoInput(id = index.toLong()) },
    val nextInputId: Long = INITIAL_INPUT_COUNT.toLong(),
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

    data object CompleteStory : CreateAdditionalInfoIntent
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
}

@HiltViewModel
class CreateAdditionalInfoViewModel
    @Inject
    constructor(
        storylineGenerationStore: StorylineGenerationStore,
    ) : MviViewModel<CreateAdditionalInfoIntent, CreateAdditionalInfoUiState, CreateAdditionalInfoEvent, Nothing>(
            CreateAdditionalInfoUiState(
                storylines =
                    storylineGenerationStore.state.value
                        .resultOrNull()
                        ?.storylines
                        .orEmpty()
                        .map { storyline ->
                            AdditionalInfoStoryline(
                                text = storyline.storyline,
                                recommendedInfos = storyline.recommendedInfos.map { it.text },
                            )
                        },
            ),
        ) {
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

                // 완성 요청과 채팅 화면 전환은 스토리 완성 API 연동과 함께 붙는다.
                CreateAdditionalInfoIntent.CompleteStory -> Unit
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
            }
    }
