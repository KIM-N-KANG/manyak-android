package app.manyak.feature.create

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.StoryCreationRepository
import app.manyak.core.domain.story.Storyline
import app.manyak.core.domain.story.StorylineRating
import app.manyak.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    /** 보존할 내용 없이 이탈을 시도해 소실 경고 다이얼로그를 띄운 상태(3-1 이탈 가드). */
    val showExitWarningDialog: Boolean = false,
    val activeIndex: Int = 0,
    /** 스토리라인 ID별 평가. 같은 평가를 다시 누르면 해제된다. */
    val ratings: Map<Long, StorylineRating> = emptyMap(),
) {
    val storylines: List<Storyline> get() = (content as? StorylineContent.Loaded)?.storylines.orEmpty()

    val activeStoryline: Storyline? get() = storylines.getOrNull(activeIndex)

    val activeRating: StorylineRating? get() = activeStoryline?.let { ratings[it.id] }
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

    /** 시스템·헤더 뒤로가기 — 퍼널 이탈(이 단계가 키워드를 대체하므로 홈 복귀). */
    data object LeaveFunnel : CreateStorylineIntent

    /** 소실 경고 다이얼로그의 "그만 만들기". */
    data object ConfirmLeaveFunnel : CreateStorylineIntent

    /** 소실 경고 다이얼로그의 "계속 만들기"·바깥 탭. */
    data object DismissExitWarning : CreateStorylineIntent
}

sealed interface CreateStorylineEvent {
    data class ActiveStorylineChanged(
        val index: Int,
    ) : CreateStorylineEvent

    /** 평가 토글·실패 롤백의 최종값 반영. null 은 평가 해제다. */
    data class RatingChanged(
        val storylineId: Long,
        val rating: StorylineRating?,
    ) : CreateStorylineEvent

    data class GenerationStateChanged(
        val generation: StorylineGenerationState,
        /** 복원된 결과의 활성 탭. 새 생성은 미러가 초기화되어 0 이다. */
        val restoredActiveIndex: Int = 0,
    ) : CreateStorylineEvent

    data class ExitWarningVisibleChanged(
        val visible: Boolean,
    ) : CreateStorylineEvent
}

sealed interface CreateStorylineEffect {
    /** 활성 스토리라인 "선택하기" — 추가 정보 단계로 넘어간다. */
    data class NavigateToAdditionalInfo(
        val storylineIndex: Int,
    ) : CreateStorylineEffect

    /** 평가 동기화 실패 안내. 성공은 버튼 상태로 충분해 따로 알리지 않는다. */
    data object ShowRatingSyncFailed : CreateStorylineEffect

    /** 퍼널 이탈 확정. 내용이 남았으면 "임시 저장되었어요" 토스트를 함께 띄운다. */
    data class ExitFunnel(
        val contentPreserved: Boolean,
    ) : CreateStorylineEffect
}

@HiltViewModel
class CreateStorylineViewModel
    @Inject
    constructor(
        private val storylineGenerationStore: StorylineGenerationStore,
        private val storyCreationRepository: StoryCreationRepository,
    ) : MviViewModel<CreateStorylineIntent, CreateStorylineUiState, CreateStorylineEvent, CreateStorylineEffect>(
            CreateStorylineUiState(),
        ) {
        /**
         * 평가 동기화 판정의 정본. UiState 반영은 이벤트 채널을 거쳐 한 박자 늦을 수 있어,
         * 원하는 값·서버 반영 값은 인텐트 처리 시점에 즉시 갱신되는 이 장부로 판정한다.
         */
        private val desiredRatings = mutableMapOf<Long, StorylineRating>()
        private val syncedRatings = mutableMapOf<Long, StorylineRating>()
        private val ratingSyncTimers = mutableMapOf<Long, Job>()
        private val ratingSyncInFlight = mutableSetOf<Long>()

        /**
         * 이탈 처리 중. 이탈은 스토어를 초기화(Idle)하는데, 그 전이를 화면에 반영하면 pop
         * 애니메이션 동안 나가는 화면이 빈 실패 상태로 번쩍인다. 이후 상태 반영을 멈춘다.
         */
        private var isLeaving = false

        init {
            viewModelScope.launch {
                // 프로세스 재시작·재개 진입으로 스토어가 비어 있으면 진행 레코드에서 먼저 복원한다.
                storylineGenerationStore.ensureRestored()
                storylineGenerationStore.state.collect { generation ->
                    if (isLeaving) return@collect
                    if (generation is StorylineGenerationState.Generated) resetRatingSync()
                    dispatchEvent(
                        CreateStorylineEvent.GenerationStateChanged(
                            generation = generation,
                            restoredActiveIndex = storylineGenerationStore.progress.activeStorylineIndex,
                        ),
                    )
                }
            }
        }

        /**
         * 응답을 못 받은 생성 요청의 복구 폴링. 화면이 STARTED 동안 수집해 백그라운드에서 멈추고
         * 복귀 시 재개된다. 복구 대상이 없으면 아무 일도 하지 않는다.
         */
        suspend fun driveRecovery() {
            storylineGenerationStore.runStorylineRecovery()
        }

        override suspend fun handleIntent(intent: CreateStorylineIntent) {
            val state = uiState.value
            when (intent) {
                is CreateStorylineIntent.SelectStoryline ->
                    if (intent.index in state.storylines.indices) {
                        storylineGenerationStore.updateActiveStoryline(intent.index)
                        dispatchEvent(CreateStorylineEvent.ActiveStorylineChanged(intent.index))
                    }

                is CreateStorylineIntent.ToggleRating ->
                    state.activeStoryline?.let { storyline -> toggleRating(storyline.id, intent.rating) }

                // 스토어가 상태 전이를 동기로 수행해 중복 탭을 막고, 실행은 퍼널 스코프가 담는다.
                CreateStorylineIntent.Regenerate -> storylineGenerationStore.regenerate()

                CreateStorylineIntent.LeaveFunnel ->
                    if (storylineGenerationStore.hasContentToPreserve()) {
                        isLeaving = true
                        val preserved = storylineGenerationStore.leaveFunnel()
                        dispatchEffect(CreateStorylineEffect.ExitFunnel(contentPreserved = preserved))
                    } else {
                        // 복원할 결과가 없는 소실 — 3-1 이탈 가드의 소실 경고 다이얼로그.
                        dispatchEvent(CreateStorylineEvent.ExitWarningVisibleChanged(visible = true))
                    }

                CreateStorylineIntent.ConfirmLeaveFunnel -> {
                    isLeaving = true
                    storylineGenerationStore.leaveFunnel()
                    dispatchEvent(CreateStorylineEvent.ExitWarningVisibleChanged(visible = false))
                    dispatchEffect(CreateStorylineEffect.ExitFunnel(contentPreserved = false))
                }

                CreateStorylineIntent.DismissExitWarning ->
                    dispatchEvent(CreateStorylineEvent.ExitWarningVisibleChanged(visible = false))

                CreateStorylineIntent.ConfirmSelection ->
                    if (state.activeStoryline != null) {
                        storylineGenerationStore.markStorylineSelected(state.activeIndex)
                        dispatchEffect(CreateStorylineEffect.NavigateToAdditionalInfo(state.activeIndex))
                    }
            }
        }

        /** UI 에는 즉시 반영하고 서버 동기화는 디바운스한다. */
        private suspend fun toggleRating(
            storylineId: Long,
            rating: StorylineRating,
        ) {
            val next = if (desiredRatings[storylineId] == rating) null else rating
            desiredRatings.putOrRemove(storylineId, next)
            dispatchEvent(CreateStorylineEvent.RatingChanged(storylineId, next))
            scheduleRatingSync(storylineId)
        }

        private fun scheduleRatingSync(storylineId: Long) {
            ratingSyncTimers[storylineId]?.cancel()
            ratingSyncTimers[storylineId] =
                viewModelScope.launch {
                    delay(RATING_SYNC_DEBOUNCE_MS)
                    ratingSyncTimers.remove(storylineId)
                    launchRatingSync(storylineId)
                }
        }

        /**
         * 진행 중 요청은 완주시키고, 끝난 뒤 원하는 값과 서버 값이 갈리면 다시 동기화한다.
         * 진행 중 요청을 취소하면 서버 반영 여부를 알 수 없어 화면과 서버가 어긋난 채 남는다.
         */
        private fun launchRatingSync(storylineId: Long) {
            if (storylineId in ratingSyncInFlight) return
            ratingSyncInFlight += storylineId
            viewModelScope.launch {
                try {
                    syncRating(storylineId)
                } finally {
                    ratingSyncInFlight -= storylineId
                    if (desiredRatings[storylineId] != syncedRatings[storylineId]) {
                        launchRatingSync(storylineId)
                    }
                }
            }
        }

        private suspend fun syncRating(storylineId: Long) {
            val desired = desiredRatings[storylineId]
            val synced = syncedRatings[storylineId]
            if (desired == synced) return
            val result =
                if (desired == null) {
                    storyCreationRepository.clearStorylineRating(storylineId)
                } else {
                    storyCreationRepository.rateStoryline(storylineId, desired)
                }
            when (result) {
                is DomainResult.Success -> syncedRatings.putOrRemove(storylineId, desired)

                is DomainResult.Failure -> {
                    // 마지막으로 서버에 반영된 값으로 되돌리고 실패를 알린다.
                    desiredRatings.putOrRemove(storylineId, synced)
                    dispatchEvent(CreateStorylineEvent.RatingChanged(storylineId, synced))
                    dispatchEffect(CreateStorylineEffect.ShowRatingSyncFailed)
                }
            }
        }

        /** 새 생성 결과가 오면 이전 스토리라인의 평가 동기화는 의미가 없다. */
        private fun resetRatingSync() {
            ratingSyncTimers.values.forEach(Job::cancel)
            ratingSyncTimers.clear()
            desiredRatings.clear()
            syncedRatings.clear()
        }

        override fun reduce(
            state: CreateStorylineUiState,
            event: CreateStorylineEvent,
        ): CreateStorylineUiState =
            when (event) {
                is CreateStorylineEvent.ActiveStorylineChanged -> state.copy(activeIndex = event.index)
                is CreateStorylineEvent.RatingChanged ->
                    state.copy(
                        ratings =
                            if (event.rating == null) {
                                state.ratings - event.storylineId
                            } else {
                                state.ratings + (event.storylineId to event.rating)
                            },
                    )

                is CreateStorylineEvent.GenerationStateChanged ->
                    reduceGeneration(state, event.generation, event.restoredActiveIndex)

                is CreateStorylineEvent.ExitWarningVisibleChanged ->
                    state.copy(showExitWarningDialog = event.visible)
            }

        private fun reduceGeneration(
            state: CreateStorylineUiState,
            generation: StorylineGenerationState,
            restoredActiveIndex: Int,
        ): CreateStorylineUiState =
            when (generation) {
                StorylineGenerationState.Generating ->
                    state.copy(content = StorylineContent.Generating, hasGenerationError = false)

                is StorylineGenerationState.Generated ->
                    state.copy(
                        content = StorylineContent.Loaded(generation.result.storylines),
                        hasGenerationError = false,
                        activeIndex =
                            restoredActiveIndex.coerceIn(
                                0,
                                (generation.result.storylines.size - 1).coerceAtLeast(0),
                            ),
                        ratings = emptyMap(),
                    )

                // 재생성 실패면 직전 결과를 그대로 두고 인라인 오류만 켠다. 선택·평가 상태는
                // Generating 전이가 건드리지 않았으므로 직전 목록과 그대로 짝이 맞는다.
                is StorylineGenerationState.Failed ->
                    state.copy(
                        content = StorylineContent.Loaded(generation.previousResult?.storylines.orEmpty()),
                        hasGenerationError = true,
                    )

                // 복원할 진행 레코드조차 없이 결과가 사라진 경우. 실패와 같은 화면으로 보여 준다
                // ("다시 만들기"는 직전 명령이 없어 동작하지 않는다).
                StorylineGenerationState.Idle ->
                    state.copy(content = StorylineContent.Loaded(emptyList()), hasGenerationError = true)
            }

        companion object {
            const val RATING_SYNC_DEBOUNCE_MS: Long = 300
        }
    }

private fun MutableMap<Long, StorylineRating>.putOrRemove(
    storylineId: Long,
    rating: StorylineRating?,
) {
    if (rating == null) remove(storylineId) else put(storylineId, rating)
}
