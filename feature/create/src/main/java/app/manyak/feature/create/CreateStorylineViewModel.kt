package app.manyak.feature.create

import androidx.lifecycle.viewModelScope
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.story.StoryCreationRepository
import app.manyak.common.entity.story.StoryTag
import app.manyak.common.entity.story.StoryTagCategory
import app.manyak.common.entity.story.Storyline
import app.manyak.common.entity.story.StorylineGenerationCommand
import app.manyak.common.entity.story.StorylineRating
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.core.analytics.Analytics
import app.manyak.core.analytics.AnalyticsEvent
import app.manyak.core.analytics.CreateStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 생성 진행 중이거나, 결과(실패 시 빈 목록 포함)를 보여 주는 화면 콘텐츠. */
sealed interface StorylineContent {
    /**
     * 스토어가 비어 있어 진행 레코드 복원을 기다리는 중. 생성 중인지 이미 결과가 있는지 아직
     * 모르므로 어느 쪽도 그리지 않는다 — 재개 진입에서 로딩 화면이 스쳐 지나가지 않게 한다.
     */
    data object Restoring : StorylineContent

    data object Generating : StorylineContent

    data class Loaded(
        val storylines: List<Storyline>,
    ) : StorylineContent
}

/**
 * "선택한 키워드 보기" 시트가 그리는 묶음 하나. 라벨 문구는 리소스라 화면이 붙인다.
 */
data class SelectedKeywordGroup(
    val category: StoryTagCategory,
    /** 주변 인물이 둘 이상일 때의 순번. 하나뿐이면 null 이고 라벨에 번호를 붙이지 않는다. */
    val ordinal: Int? = null,
    val keywords: List<String>,
)

/** "선택한 키워드 보기" 시트. 이름표를 얻으려면 제공 태그 목록이 있어야 해 조회 상태를 함께 든다. */
sealed interface SelectedKeywords {
    data object Hidden : SelectedKeywords

    data object Loading : SelectedKeywords

    data class Loaded(
        val groups: List<SelectedKeywordGroup>,
    ) : SelectedKeywords

    data object Failed : SelectedKeywords
}

data class CreateStorylineUiState(
    val content: StorylineContent = StorylineContent.Restoring,
    /** 생성·재생성 실패. 직전 결과가 남아 있으면 그대로 보여 주며 인라인 오류만 덧붙인다. */
    val hasGenerationError: Boolean = false,
    /** 이탈을 막고 띄운 경고. */
    val exitWarning: FunnelExitWarning? = null,
    val activeIndex: Int = 0,
    /** 스토리라인 ID별 평가. 같은 평가를 다시 누르면 해제된다. */
    val ratings: Map<Long, StorylineRating> = emptyMap(),
    /** 생성에 실린 키워드가 남아 있는지. 없으면 "선택한 키워드 보기"를 그리지 않는다. */
    val hasKeywords: Boolean = false,
    val selectedKeywords: SelectedKeywords = SelectedKeywords.Hidden,
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

    /** 푸터의 "선택한 키워드 보기". 조회 실패 뒤 다시 누르는 것이 재시도다. */
    data object ShowSelectedKeywords : CreateStorylineIntent

    data object DismissSelectedKeywords : CreateStorylineIntent

    /** 헤더의 임시 저장 버튼과 백그라운드 전환. */
    data object SaveDraft : CreateStorylineIntent

    /** 시스템·헤더 뒤로가기 — 퍼널 이탈(이 단계가 키워드를 대체하므로 홈 복귀). */
    data object LeaveFunnel : CreateStorylineIntent

    /** 이탈 경고의 "나가기"·"그만 만들기". */
    data object ConfirmLeaveFunnel : CreateStorylineIntent

    /** 이탈 경고의 머무르기·바깥 탭. */
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
        val hasKeywords: Boolean = false,
    ) : CreateStorylineEvent

    data class SelectedKeywordsChanged(
        val keywords: SelectedKeywords,
    ) : CreateStorylineEvent

    data class ExitWarningChanged(
        val warning: FunnelExitWarning?,
    ) : CreateStorylineEvent
}

sealed interface CreateStorylineEffect {
    /** 활성 스토리라인 "선택하기" — 추가 정보 단계로 넘어간다. */
    data class NavigateToAdditionalInfo(
        val storylineIndex: Int,
    ) : CreateStorylineEffect

    /** 평가 동기화 실패 안내. 성공은 버튼 상태로 충분해 따로 알리지 않는다. */
    data object ShowRatingSyncFailed : CreateStorylineEffect

    /** 퍼널 이탈 확정. */
    data object ExitFunnel : CreateStorylineEffect
}

@HiltViewModel
class CreateStorylineViewModel
    @Inject
    constructor(
        private val storylineGenerationStore: StorylineGenerationStore,
        private val storyCreationRepository: StoryCreationRepository,
        private val analytics: Analytics,
    ) : MviViewModel<CreateStorylineIntent, CreateStorylineUiState, CreateStorylineEvent, CreateStorylineEffect>(
            storylineGenerationStore.toStorylineSnapshot(),
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

        private var selectedKeywordsJob: Job? = null

        val draftSave = storylineGenerationStore.draftSave

        init {
            analytics.track(AnalyticsEvent.StoryCreateStepViewed(CreateStep.STORYLINE_SELECT))
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
                            hasKeywords = storylineGenerationStore.generationCommand != null,
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
            intent.analyticsEvent(state, creationId())?.let(analytics::track)
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

                CreateStorylineIntent.SaveDraft -> storylineGenerationStore.saveDraft()

                CreateStorylineIntent.LeaveFunnel -> leaveFunnel(confirmed = false)

                CreateStorylineIntent.ConfirmLeaveFunnel -> leaveFunnel(confirmed = true)

                CreateStorylineIntent.DismissExitWarning ->
                    dispatchEvent(CreateStorylineEvent.ExitWarningChanged(null))

                CreateStorylineIntent.ShowSelectedKeywords -> loadSelectedKeywords()

                CreateStorylineIntent.DismissSelectedKeywords -> {
                    selectedKeywordsJob?.cancel()
                    dispatchEvent(CreateStorylineEvent.SelectedKeywordsChanged(SelectedKeywords.Hidden))
                }

                CreateStorylineIntent.ConfirmSelection ->
                    if (state.activeStoryline != null) {
                        storylineGenerationStore.markStorylineSelected(state.activeIndex)
                        dispatchEffect(CreateStorylineEffect.NavigateToAdditionalInfo(state.activeIndex))
                    }
            }
        }

        /**
         * 시트를 열면서 키워드 이름을 채운다. 조회는 자식 작업으로 돌린다 — 인텐트 큐에서 기다리면
         * 응답이 올 때까지 닫기도 막힌다.
         */
        private suspend fun loadSelectedKeywords() {
            val command = storylineGenerationStore.generationCommand ?: return
            if (selectedKeywordsJob?.isActive == true) return
            dispatchEvent(CreateStorylineEvent.SelectedKeywordsChanged(SelectedKeywords.Loading))
            selectedKeywordsJob =
                viewModelScope.launch {
                    val keywords =
                        when (val result = storylineGenerationStore.tagCatalog()) {
                            is DomainResult.Success ->
                                SelectedKeywords.Loaded(command.toKeywordGroups(result.value))

                            is DomainResult.Failure -> SelectedKeywords.Failed
                        }
                    dispatchEvent(CreateStorylineEvent.SelectedKeywordsChanged(keywords))
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
                dispatchEvent(CreateStorylineEvent.ExitWarningChanged(warning))
                return
            }
            if (confirmed) dispatchEvent(CreateStorylineEvent.ExitWarningChanged(null))
            isLeaving = true
            storylineGenerationStore.leaveFunnel()
            dispatchEffect(CreateStorylineEffect.ExitFunnel)
        }

        /** UI 에는 즉시 반영하고 서버 동기화는 디바운스한다. */
        private suspend fun toggleRating(
            storylineId: Long,
            rating: StorylineRating,
        ) {
            val next = if (desiredRatings[storylineId] == rating) null else rating
            analytics.track(AnalyticsEvent.StorylineRatingClicked(storylineId, rating, active = next != null))
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

        /** 분석 이벤트의 `creation_id`. 생성 결과가 없으면 프로퍼티를 채울 수 없어 이벤트를 내지 않는다. */
        private fun creationId(): String? =
            storylineGenerationStore.state.value
                .resultOrNull()
                ?.simpleCreationId
                ?.toString()

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
                        .copy(hasKeywords = event.hasKeywords)

                is CreateStorylineEvent.SelectedKeywordsChanged ->
                    state.copy(selectedKeywords = event.keywords)

                is CreateStorylineEvent.ExitWarningChanged -> state.copy(exitWarning = event.warning)
            }

        companion object {
            const val RATING_SYNC_DEBOUNCE_MS: Long = 300
        }
    }

/**
 * 스토어의 현재 생성 상태로 첫 프레임을 만든다.
 *
 * 키워드 단계에서 넘어온 진입은 스토어가 이미 생성 중이라 로딩이 곧바로 그려지고, 스토어가 빈
 * 재개 진입은 복원 결과를 알기 전까지 [StorylineContent.Restoring] 으로 남는다. 초기값을
 * 생성 중으로 고정하면 복원이 끝나는 한두 프레임 동안 재개 진입에서 로딩 화면이 번쩍인다.
 */
internal fun StorylineGenerationStore.toStorylineSnapshot(): CreateStorylineUiState {
    val generation = state.value
    return if (generation is StorylineGenerationState.Idle) {
        CreateStorylineUiState()
    } else {
        reduceGeneration(CreateStorylineUiState(), generation, progress.activeStorylineIndex)
            .copy(hasKeywords = generationCommand != null)
    }
}

/**
 * 생성에 실린 태그 ID 를 이름으로 풀어 시트가 그릴 묶음으로 만든다. 비어 있는 묶음은 내지 않는다 —
 * 주변 인물은 아예 없을 수도 있고, 이름·성별만 넣고 키워드는 고르지 않은 인물도 있다.
 */
internal fun StorylineGenerationCommand.toKeywordGroups(tags: List<StoryTag>): List<SelectedKeywordGroup> {
    val names = tags.associateBy(StoryTag::id)

    fun keywords(
        tagIds: List<Long>,
        customTags: List<String>,
    ): List<String> = tagIds.mapNotNull { id -> names[id]?.name } + customTags

    val supporting =
        supportingCharacters
            .map { character -> keywords(character.featureTagIds, character.customTags) }
            .filter(List<String>::isNotEmpty)
    return buildList {
        keywords(genreTagIds, customGenreTags).ifNotEmpty { group ->
            add(SelectedKeywordGroup(category = StoryTagCategory.GENRE, keywords = group))
        }
        keywords(protagonist.featureTagIds, protagonist.customTags).ifNotEmpty { group ->
            add(SelectedKeywordGroup(category = StoryTagCategory.PROTAGONIST, keywords = group))
        }
        supporting.forEachIndexed { index, group ->
            add(
                SelectedKeywordGroup(
                    category = StoryTagCategory.SUPPORTING_CHARACTER,
                    // 인물이 하나뿐이면 번호 없이 "주변 인물"로만 둔다.
                    ordinal = (index + 1).takeIf { supporting.size > 1 },
                    keywords = group,
                ),
            )
        }
    }
}

private inline fun List<String>.ifNotEmpty(action: (List<String>) -> Unit) {
    if (isNotEmpty()) action(this)
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

private fun MutableMap<Long, StorylineRating>.putOrRemove(
    storylineId: Long,
    rating: StorylineRating?,
) {
    if (rating == null) remove(storylineId) else put(storylineId, rating)
}

/**
 * 의도가 만드는 분석 이벤트. `creation_id` 가 필요한 이벤트는 생성 결과가 없으면 내지 않는다 —
 * 프로퍼티가 빈 이벤트는 퍼널 집계에서 짝을 잃는다.
 */
private fun CreateStorylineIntent.analyticsEvent(
    state: CreateStorylineUiState,
    creationId: String?,
): AnalyticsEvent? =
    when (this) {
        is CreateStorylineIntent.SelectStoryline ->
            creationId
                ?.takeIf { index in state.storylines.indices }
                ?.let { AnalyticsEvent.StorylineTabSelected(it, index) }
        CreateStorylineIntent.Regenerate -> creationId?.let(AnalyticsEvent::RegenerateStorylineButtonClicked)
        CreateStorylineIntent.SaveDraft -> AnalyticsEvent.DraftSaved(CreateStep.STORYLINE_SELECT)
        CreateStorylineIntent.ConfirmLeaveFunnel -> AnalyticsEvent.CreateExitButtonClicked(CreateStep.STORYLINE_SELECT)
        CreateStorylineIntent.ShowSelectedKeywords -> creationId?.let(AnalyticsEvent::SelectedTagsButtonClicked)
        CreateStorylineIntent.ConfirmSelection ->
            creationId
                ?.takeIf { state.activeStoryline != null }
                ?.let { AnalyticsEvent.StorylineOptionSelected(it, state.activeIndex) }
        else -> null
    }
