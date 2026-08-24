package app.manyak.feature.create

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.story.Storyline
import app.manyak.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 스토리라인 평가. 보조 신호이며 선택 진행을 막지 않는다. */
enum class StorylineRating {
    GOOD,
    BAD,
}

/** 생성 진행 중이거나, 결과(실패 시 빈 목록 포함)를 보여 주는 화면 콘텐츠. */
sealed interface StorylineContent {
    data object Generating : StorylineContent

    data class Loaded(
        val storylines: List<Storyline>,
    ) : StorylineContent
}

data class CreateStorylineUiState(
    val content: StorylineContent = StorylineContent.Generating,
    /** 생성·재생성 실패. 직전 결과가 남아 있으면 그대로 보여 주며 인라인 오류만 덧붙인다. */
    val hasGenerationError: Boolean = false,
    val activeIndex: Int = 0,
    /** 스토리라인 순번별 평가. 같은 평가를 다시 누르면 해제된다. */
    val ratings: Map<Int, StorylineRating> = emptyMap(),
) {
    val storylines: List<Storyline> get() = (content as? StorylineContent.Loaded)?.storylines.orEmpty()

    val activeStoryline: Storyline? get() = storylines.getOrNull(activeIndex)

    val activeRating: StorylineRating? get() = ratings[activeIndex]
}

sealed interface CreateStorylineIntent {
    data class SelectStoryline(
        val index: Int,
    ) : CreateStorylineIntent

    data class ToggleRating(
        val rating: StorylineRating,
    ) : CreateStorylineIntent

    data object Regenerate : CreateStorylineIntent

    data object ConfirmSelection : CreateStorylineIntent
}

sealed interface CreateStorylineEvent {
    data class ActiveStorylineChanged(
        val index: Int,
    ) : CreateStorylineEvent

    data class RatingToggled(
        val index: Int,
        val rating: StorylineRating,
    ) : CreateStorylineEvent

    data class GenerationStateChanged(
        val generation: StorylineGenerationState,
    ) : CreateStorylineEvent
}

sealed interface CreateStorylineEffect {
    /** 활성 스토리라인 "선택하기" — 추가 정보 단계로 넘어간다. */
    data class NavigateToAdditionalInfo(
        val storylineIndex: Int,
    ) : CreateStorylineEffect
}

@HiltViewModel
class CreateStorylineViewModel
    @Inject
    constructor(
        private val storylineGenerationStore: StorylineGenerationStore,
    ) : MviViewModel<CreateStorylineIntent, CreateStorylineUiState, CreateStorylineEvent, CreateStorylineEffect>(
            CreateStorylineUiState(),
        ) {
        private var regenerateJob: Job? = null

        init {
            viewModelScope.launch {
                storylineGenerationStore.state.collect { generation ->
                    dispatchEvent(CreateStorylineEvent.GenerationStateChanged(generation))
                }
            }
        }

        override suspend fun handleIntent(intent: CreateStorylineIntent) {
            val state = uiState.value
            when (intent) {
                is CreateStorylineIntent.SelectStoryline ->
                    if (intent.index in state.storylines.indices) {
                        dispatchEvent(CreateStorylineEvent.ActiveStorylineChanged(intent.index))
                    }

                // 평가는 화면 로컬 상태다. 서버 동기화(PUT·DELETE)는 평가 API 연동과 함께 붙는다.
                is CreateStorylineIntent.ToggleRating ->
                    if (state.activeStoryline != null) {
                        dispatchEvent(CreateStorylineEvent.RatingToggled(state.activeIndex, intent.rating))
                    }

                CreateStorylineIntent.Regenerate -> startRegenerate(state)

                CreateStorylineIntent.ConfirmSelection ->
                    if (state.activeStoryline != null) {
                        dispatchEffect(CreateStorylineEffect.NavigateToAdditionalInfo(state.activeIndex))
                    }
            }
        }

        private fun startRegenerate(state: CreateStorylineUiState) {
            if (state.content is StorylineContent.Generating) return
            if (regenerateJob?.isActive == true) return
            // UNDISPATCHED — 버튼을 누른 즉시 스토어가 Generating 으로 바뀌어 중복 탭을 막는다.
            regenerateJob =
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    storylineGenerationStore.regenerate()
                }
        }

        override fun reduce(
            state: CreateStorylineUiState,
            event: CreateStorylineEvent,
        ): CreateStorylineUiState =
            when (event) {
                is CreateStorylineEvent.ActiveStorylineChanged -> state.copy(activeIndex = event.index)
                is CreateStorylineEvent.RatingToggled ->
                    state.copy(
                        ratings =
                            if (state.ratings[event.index] == event.rating) {
                                state.ratings - event.index
                            } else {
                                state.ratings + (event.index to event.rating)
                            },
                    )

                is CreateStorylineEvent.GenerationStateChanged -> reduceGeneration(state, event.generation)
            }

        private fun reduceGeneration(
            state: CreateStorylineUiState,
            generation: StorylineGenerationState,
        ): CreateStorylineUiState =
            when (generation) {
                StorylineGenerationState.Generating ->
                    state.copy(content = StorylineContent.Generating, hasGenerationError = false)

                is StorylineGenerationState.Generated ->
                    state.copy(
                        content = StorylineContent.Loaded(generation.result.storylines),
                        hasGenerationError = false,
                        activeIndex = 0,
                        ratings = emptyMap(),
                    )

                // 재생성 실패면 직전 결과를 그대로 두고 인라인 오류만 켠다. 선택·평가 상태는
                // Generating 전이가 건드리지 않았으므로 직전 목록과 그대로 짝이 맞는다.
                is StorylineGenerationState.Failed ->
                    state.copy(
                        content = StorylineContent.Loaded(generation.previousResult?.storylines.orEmpty()),
                        hasGenerationError = true,
                    )

                // 프로세스 재시작 복원 등으로 결과가 사라진 경우. 복구 대응이 정해지기 전까지는
                // 실패와 같은 화면으로 보여 준다("다시 만들기"는 직전 명령이 없어 동작하지 않는다).
                StorylineGenerationState.Idle ->
                    state.copy(content = StorylineContent.Loaded(emptyList()), hasGenerationError = true)
            }
    }
