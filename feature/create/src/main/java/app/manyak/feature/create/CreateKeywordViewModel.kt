package app.manyak.feature.create

import androidx.lifecycle.viewModelScope
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.story.StoryCreationRepository
import app.manyak.common.entity.story.CharacterGender
import app.manyak.common.entity.story.KeywordCharacterSnapshot
import app.manyak.common.entity.story.KeywordCustomTagSnapshot
import app.manyak.common.entity.story.KeywordDraftSnapshot
import app.manyak.common.entity.story.PendingStoryCreation
import app.manyak.common.entity.story.PendingStoryCreationStore
import app.manyak.common.entity.story.StoryCharacterInput
import app.manyak.common.entity.story.StoryTag
import app.manyak.common.entity.story.StoryTagCategory
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.core.analytics.Analytics
import app.manyak.core.analytics.AnalyticsEvent
import app.manyak.core.analytics.CreateStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.Normalizer
import javax.inject.Inject

/** 직접 추가한 키워드는 선택 해제해도 목록에 남는다. */
data class CustomTag(
    val name: String,
    val selected: Boolean,
)

/** [id]는 서버 ID가 아닌 화면 로컬 식별자다. */
data class KeywordCharacter(
    val id: Long,
    val name: String = "",
    val gender: CharacterGender? = null,
    val selectedTagIds: Set<Long> = emptySet(),
    val customTags: List<CustomTag> = emptyList(),
) {
    val featureCount: Int get() = selectedTagIds.size + customTags.count { it.selected }
}

sealed interface KeywordTarget {
    data object Genre : KeywordTarget

    data object Protagonist : KeywordTarget

    data class Supporting(
        val characterId: Long,
    ) : KeywordTarget
}

val KeywordTarget.category: StoryTagCategory
    get() =
        when (this) {
            KeywordTarget.Genre -> StoryTagCategory.GENRE
            KeywordTarget.Protagonist -> StoryTagCategory.PROTAGONIST
            is KeywordTarget.Supporting -> StoryTagCategory.SUPPORTING_CHARACTER
        }

/** 제공 태그 조회 상태. */
sealed interface ProvidedTags {
    data object Loading : ProvidedTags

    data class Loaded(
        val byCategory: Map<StoryTagCategory, List<StoryTag>>,
    ) : ProvidedTags

    data object Failed : ProvidedTags
}

data class CreateKeywordUiState(
    /**
     * 진행 레코드 복원을 기다리는 중. 저장해 둔 키워드가 있는지 아직 몰라 화면을 그리지 않는다 —
     * 빈 입력 화면이 스쳐 간 뒤 값이 채워지면 재개 진입에서 화면이 번쩍인다.
     */
    val isRestoring: Boolean = true,
    /** 임시 저장 버튼이 그리는 상태. */
    val draftSaveStatus: DraftSaveStatus = DraftSaveStatus.IDLE,
    /**
     * 디스크에 마지막으로 반영된 스냅숏. 복원이 끝나면 그때의 화면 스냅숏으로 채워지므로,
     * 이 값과 현재 스냅숏이 갈리는 것이 곧 "임시 저장하지 않은 변경"이다.
     */
    val savedSnapshot: KeywordDraftSnapshot? = null,
    /** 이탈을 막고 띄운 경고. */
    val exitWarning: FunnelExitWarning? = null,
    val activeCategory: StoryTagCategory = StoryTagCategory.GENRE,
    /** "다음"을 눌러 검증에 실패한 카테고리. 그 카테고리가 활성일 때만 푸터에 오류를 표시한다. */
    val validationErrorCategory: StoryTagCategory? = null,
    /** "스토리라인 만들기"를 한 번이라도 눌렀는지. 이름 중복 푸터 오류는 이 뒤에만 노출한다. */
    val hasAttemptedGenerate: Boolean = false,
    /** 스토리라인 생성 요청 진행 중. CTA 를 스피너로 바꾸고 중복 요청을 막는다. */
    val isGeneratingStorylines: Boolean = false,
    val providedTags: ProvidedTags = ProvidedTags.Loading,
    val selectedGenreTagIds: Set<Long> = emptySet(),
    val customGenreTags: List<CustomTag> = emptyList(),
    val protagonist: KeywordCharacter = KeywordCharacter(id = PROTAGONIST_ID),
    /** 퍼널 진입 시 빈 주변 인물 입력 섹션 1개가 놓여 있다. 빈 섹션도 인원으로 센다. */
    val supportingCharacters: List<KeywordCharacter> = listOf(KeywordCharacter(id = FIRST_SUPPORTING_ID)),
    val nextSupportingId: Long = FIRST_SUPPORTING_ID + 1,
) {
    val genreSelectedCount: Int get() = selectedGenreTagIds.size + customGenreTags.count { it.selected }

    /**
     * 주인공과 주변 인물의 이름은 한 스토리 안에서 겹칠 수 없다. 판정 키는 서버와 같다 —
     * NFC 정규화 → 공백 제거 → 소문자. 먼저 쓴 이름(주인공 우선)을 남기고 뒤에 겹친 인물만 표시한다.
     * 비워 둔 이름은 AI 가 지어 주므로 판정 대상이 아니다.
     */
    val duplicateNameCharacterIds: Set<Long>
        get() {
            val seen = mutableSetOf<String>()
            val duplicates = mutableSetOf<Long>()
            for (character in listOf(protagonist) + supportingCharacters) {
                val key = normalizeName(character.name) ?: continue
                if (!seen.add(key)) duplicates += character.id
            }
            return duplicates
        }

    fun character(target: KeywordTarget): KeywordCharacter? =
        when (target) {
            KeywordTarget.Genre -> null
            KeywordTarget.Protagonist -> protagonist
            is KeywordTarget.Supporting -> supportingCharacters.firstOrNull { it.id == target.characterId }
        }

    fun isAtSelectionCap(target: KeywordTarget): Boolean =
        when (target) {
            KeywordTarget.Genre -> genreSelectedCount >= GENRE_MAX_SELECTION
            else -> (character(target)?.featureCount ?: 0) >= FEATURE_MAX_SELECTION
        }

    fun isComplete(category: StoryTagCategory): Boolean =
        when (category) {
            StoryTagCategory.GENRE -> genreSelectedCount > 0
            StoryTagCategory.PROTAGONIST -> protagonist.featureCount > 0
            StoryTagCategory.SUPPORTING_CHARACTER -> true
        }

    fun isUnlocked(category: StoryTagCategory): Boolean =
        StoryTagCategory.entries
            .take(category.ordinal)
            .all { isComplete(it) }

    val canGenerateStorylines: Boolean
        get() =
            isComplete(StoryTagCategory.GENRE) &&
                isComplete(StoryTagCategory.PROTAGONIST) &&
                duplicateNameCharacterIds.isEmpty()

    val showDuplicateNameFooterError: Boolean
        get() = hasAttemptedGenerate && duplicateNameCharacterIds.isNotEmpty()

    val isFooterEnabled: Boolean
        get() = providedTags !is ProvidedTags.Failed

    /** 복원 전에는 무엇이 저장돼 있는지 몰라 변경 여부를 판정하지 않는다. */
    val hasUnsavedChanges: Boolean
        get() = savedSnapshot != null && toKeywordSnapshot() != savedSnapshot

    val draftSave: DraftSaveUiState
        get() {
            val unsaved = hasUnsavedChanges
            return DraftSaveUiState(
                status =
                    if (draftSaveStatus == DraftSaveStatus.SAVED && unsaved) {
                        DraftSaveStatus.IDLE
                    } else {
                        draftSaveStatus
                    },
                // 입력을 모두 지운 변경도 저장 대상이다 — 그래야 남아 있는 저장본이 함께 사라진다.
                canSave = !isRestoring && unsaved,
                hasUnsavedChanges = unsaved,
            )
        }

    companion object {
        const val PROTAGONIST_ID: Long = 0
        const val FIRST_SUPPORTING_ID: Long = 1
        const val GENRE_MAX_SELECTION: Int = 3
        const val FEATURE_MAX_SELECTION: Int = 3
        const val SUPPORTING_CHARACTER_MAX: Int = 5
        const val CUSTOM_TAG_MAX_LENGTH: Int = 15
        const val CHARACTER_NAME_MAX_LENGTH: Int = 30
    }
}

private fun normalizeName(name: String): String? {
    val normalized =
        Normalizer
            .normalize(name, Normalizer.Form.NFC)
            .filterNot(Char::isWhitespace)
            .lowercase()
    return normalized.ifEmpty { null }
}

sealed interface CreateKeywordIntent {
    data class SelectCategory(
        val category: StoryTagCategory,
    ) : CreateKeywordIntent

    data object GoPrevious : CreateKeywordIntent

    data object GoNext : CreateKeywordIntent

    data object GenerateStorylines : CreateKeywordIntent

    data object RetryTags : CreateKeywordIntent

    /** 헤더의 임시 저장 버튼과 백그라운드 전환. */
    data object SaveDraft : CreateKeywordIntent

    sealed interface ExitNavigation : CreateKeywordIntent

    /** 시스템 뒤로가기·헤더 닫기 — 퍼널 이탈. 저장하지 않은 입력이 있으면 먼저 경고한다. */
    data object LeaveFunnel : ExitNavigation

    /** 이탈 경고의 "나가기" — 저장하지 않은 입력을 버리고 나간다. */
    data object ConfirmLeaveFunnel : ExitNavigation

    data object DismissExitWarning : ExitNavigation

    data class ToggleProvidedTag(
        val target: KeywordTarget,
        val tagId: Long,
    ) : CreateKeywordIntent

    data class ToggleCustomTag(
        val target: KeywordTarget,
        val index: Int,
    ) : CreateKeywordIntent

    data class AddCustomTag(
        val target: KeywordTarget,
        val name: String,
    ) : CreateKeywordIntent

    data class ChangeCharacterName(
        val target: KeywordTarget,
        val name: String,
    ) : CreateKeywordIntent

    data class ChangeCharacterGender(
        val target: KeywordTarget,
        val gender: CharacterGender?,
    ) : CreateKeywordIntent

    data object AddSupportingCharacter : CreateKeywordIntent

    data class RemoveSupportingCharacter(
        val characterId: Long,
    ) : CreateKeywordIntent
}

sealed interface CreateKeywordEvent {
    /** 키워드 임시 저장본이 도착했다. */
    data class SnapshotRestored(
        val snapshot: KeywordDraftSnapshot,
    ) : CreateKeywordEvent

    /** 되살릴 저장본이 없었다. 복원 대기만 끝낸다. */
    data object RestoreFinished : CreateKeywordEvent

    data object DraftSaveStarted : CreateKeywordEvent

    data class DraftSaveFinished(
        val snapshot: KeywordDraftSnapshot,
        val saved: Boolean,
    ) : CreateKeywordEvent

    /** 저장 성공 표시 시간이 지났다. */
    data object DraftSavedDisplayExpired : CreateKeywordEvent

    data class ExitWarningChanged(
        val warning: FunnelExitWarning?,
    ) : CreateKeywordEvent

    data class TagsLoaded(
        val byCategory: Map<StoryTagCategory, List<StoryTag>>,
    ) : CreateKeywordEvent

    data object TagsLoadFailed : CreateKeywordEvent

    data object TagsReloadStarted : CreateKeywordEvent

    data class CategoryChanged(
        val category: StoryTagCategory,
    ) : CreateKeywordEvent

    data class ValidationFailed(
        val category: StoryTagCategory,
    ) : CreateKeywordEvent

    data object GenerateAttempted : CreateKeywordEvent

    data object StorylineGenerationStarted : CreateKeywordEvent

    data class ProvidedTagToggled(
        val target: KeywordTarget,
        val tagId: Long,
    ) : CreateKeywordEvent

    data class CustomTagToggled(
        val target: KeywordTarget,
        val index: Int,
    ) : CreateKeywordEvent

    data class CustomTagAdded(
        val target: KeywordTarget,
        val name: String,
    ) : CreateKeywordEvent

    data class CharacterNameChanged(
        val target: KeywordTarget,
        val name: String,
    ) : CreateKeywordEvent

    data class CharacterGenderChanged(
        val target: KeywordTarget,
        val gender: CharacterGender?,
    ) : CreateKeywordEvent

    data object SupportingCharacterAdded : CreateKeywordEvent

    data class SupportingCharacterRemoved(
        val characterId: Long,
    ) : CreateKeywordEvent
}

sealed interface CreateKeywordEffect {
    /** 검증을 통과한 "스토리라인 만들기" — 스토리라인 선택 단계로 넘어간다. */
    data object NavigateToStoryline : CreateKeywordEffect

    /** 퍼널 이탈 확정. */
    data object ExitFunnel : CreateKeywordEffect
}

@HiltViewModel
class CreateKeywordViewModel
    @Inject
    constructor(
        private val storyCreationRepository: StoryCreationRepository,
        private val storylineGenerationStore: StorylineGenerationStore,
        private val pendingCreationStore: PendingStoryCreationStore,
        private val analytics: Analytics,
    ) : MviViewModel<CreateKeywordIntent, CreateKeywordUiState, CreateKeywordEvent, CreateKeywordEffect>(
            CreateKeywordUiState(),
        ) {
        private var tagsLoadJob: Job? = null
        private var draftSaveJob: Job? = null
        private var savedDisplayJob: Job? = null

        /**
         * 디스크에 마지막으로 써 넣은 스냅숏. 연타로 같은 내용을 다시 쓰지 않기 위한 장부다.
         * UiState 의 `savedSnapshot` 은 이벤트 채널을 거쳐 한 박자 늦어 판정 기준으로 쓸 수 없다.
         */
        private var persistedSnapshot: KeywordDraftSnapshot? = null

        init {
            analytics.track(AnalyticsEvent.StoryCreateViewed)
            analytics.track(AnalyticsEvent.StoryCreateStepViewed(CreateStep.KEYWORD))
            startTagsLoad()
            viewModelScope.launch {
                // 레코드가 남아 있는 진입은 곧 재개다. 재개 의도를 따로 저장하지 않는다.
                val record = pendingCreationStore.read()
                if (record is PendingStoryCreation.KeywordDraft) {
                    persistedSnapshot = record.snapshot
                    dispatchEvent(CreateKeywordEvent.SnapshotRestored(record.snapshot))
                } else {
                    dispatchEvent(CreateKeywordEvent.RestoreFinished)
                }
            }
        }

        private fun startTagsLoad(showLoading: Boolean = false) {
            if (tagsLoadJob?.isActive == true) return
            tagsLoadJob =
                viewModelScope.launch {
                    if (showLoading) dispatchEvent(CreateKeywordEvent.TagsReloadStarted)
                    when (val result = storyCreationRepository.tags()) {
                        is DomainResult.Success -> {
                            // 스토리라인 단계의 "선택한 키워드 보기"가 태그 ID 를 이름으로 풀 때 쓴다.
                            storylineGenerationStore.cacheTags(result.value)
                            dispatchEvent(CreateKeywordEvent.TagsLoaded(result.value.groupBy(StoryTag::category)))
                        }

                        is DomainResult.Failure -> dispatchEvent(CreateKeywordEvent.TagsLoadFailed)
                    }
                }
        }

        override suspend fun handleIntent(intent: CreateKeywordIntent) {
            val state = uiState.value
            when (intent) {
                is CreateKeywordIntent.SelectCategory ->
                    if (state.isUnlocked(intent.category)) moveToCategory(state, intent.category)

                CreateKeywordIntent.GoPrevious ->
                    state.activeCategory.previous?.let { moveToCategory(state, it) }

                CreateKeywordIntent.GoNext -> goNext(state)
                CreateKeywordIntent.GenerateStorylines -> generateStorylines(state)
                CreateKeywordIntent.SaveDraft -> saveDraft()
                is CreateKeywordIntent.ExitNavigation -> handleExitNavigation(intent, state)

                CreateKeywordIntent.RetryTags ->
                    if (state.providedTags is ProvidedTags.Failed) {
                        startTagsLoad(showLoading = true)
                    }

                else -> handleKeywordInput(state, intent)
            }
        }

        private suspend fun handleExitNavigation(
            intent: CreateKeywordIntent.ExitNavigation,
            state: CreateKeywordUiState,
        ) {
            when (intent) {
                // 닫기는 상태와 무관하게 늘 확인을 거친다 — 무엇을 잃는지에 따라 문구만 갈린다.
                CreateKeywordIntent.LeaveFunnel ->
                    dispatchEvent(
                        CreateKeywordEvent.ExitWarningChanged(
                            when {
                                state.hasUnsavedChanges -> FunnelExitWarning.UNSAVED_CHANGES
                                pendingCreationStore.read() != null -> FunnelExitWarning.SAVED_DRAFT
                                else -> FunnelExitWarning.NOTHING_TO_PRESERVE
                            },
                        ),
                    )

                CreateKeywordIntent.ConfirmLeaveFunnel -> {
                    analytics.track(AnalyticsEvent.CreateExitButtonClicked(CreateStep.KEYWORD))
                    dispatchEvent(CreateKeywordEvent.ExitWarningChanged(null))
                    leaveFunnel()
                }

                CreateKeywordIntent.DismissExitWarning ->
                    dispatchEvent(CreateKeywordEvent.ExitWarningChanged(null))
            }
        }

        private suspend fun handleKeywordInput(
            state: CreateKeywordUiState,
            intent: CreateKeywordIntent,
        ) {
            when (intent) {
                is CreateKeywordIntent.ToggleProvidedTag ->
                    state.providedTagToggleEvent(intent)?.let { dispatchEvent(it) }

                is CreateKeywordIntent.ToggleCustomTag ->
                    state.customTagToggleEvent(intent)?.let { dispatchEvent(it) }

                is CreateKeywordIntent.AddCustomTag ->
                    state.customTagAddEvent(intent)?.let {
                        analytics.track(AnalyticsEvent.AddTagSubmitted(intent.target.category))
                        dispatchEvent(it)
                    }

                is CreateKeywordIntent.ChangeCharacterName ->
                    dispatchEvent(
                        CreateKeywordEvent.CharacterNameChanged(
                            target = intent.target,
                            name = intent.name.take(CreateKeywordUiState.CHARACTER_NAME_MAX_LENGTH),
                        ),
                    )

                is CreateKeywordIntent.ChangeCharacterGender ->
                    dispatchEvent(CreateKeywordEvent.CharacterGenderChanged(intent.target, intent.gender))

                CreateKeywordIntent.AddSupportingCharacter ->
                    if (state.supportingCharacters.size < CreateKeywordUiState.SUPPORTING_CHARACTER_MAX) {
                        dispatchEvent(CreateKeywordEvent.SupportingCharacterAdded)
                    }

                is CreateKeywordIntent.RemoveSupportingCharacter ->
                    dispatchEvent(CreateKeywordEvent.SupportingCharacterRemoved(intent.characterId))

                else -> Unit
            }
        }

        /** 같은 카테고리 재선택은 이동이 아니라 이벤트를 내지 않는다. */
        private suspend fun moveToCategory(
            state: CreateKeywordUiState,
            category: StoryTagCategory,
        ) {
            if (category != state.activeCategory) {
                analytics.track(AnalyticsEvent.TagCategorySelected(from = state.activeCategory, to = category))
            }
            dispatchEvent(CreateKeywordEvent.CategoryChanged(category))
        }

        private suspend fun goNext(state: CreateKeywordUiState) {
            if (state.isComplete(state.activeCategory)) {
                state.activeCategory.next?.let { moveToCategory(state, it) }
            } else {
                dispatchEvent(CreateKeywordEvent.ValidationFailed(state.activeCategory))
            }
        }

        /**
         * 지금 입력을 진행 레코드로 내보낸다. 임시 저장 버튼과 백그라운드 전환이 부르는 유일한
         * 저장 경로다. 쓰기를 인텐트 처리 안에서 기다리면 그동안 다음 입력이 막히므로 따로 띄운다.
         *
         * 복원이 화면에 반영될 때까지 기다린다 — 버튼은 복원 중에도 눌리므로, 기다리지 않으면
         * 아직 비어 있는 상태를 스냅숏해 저장해 둔 입력을 지운다.
         */
        private fun saveDraft() {
            // 쓰기가 도는 동안의 추가 요청은 버린다 — 같은 내용을 두 번 쓸 뿐이다.
            if (draftSaveJob?.isActive == true) return
            draftSaveJob =
                viewModelScope.launch {
                    val state = uiState.first { !it.isRestoring }
                    if (state.isGeneratingStorylines) return@launch
                    val snapshot = state.toKeywordSnapshot()
                    // 이미 같은 스냅숏이 디스크에 있으면 확인 표시만 다시 보여 준다. 연타로
                    // 눌러도 디스크는 건드리지 않고, 버튼이 죽은 것처럼 보이지도 않는다.
                    if (snapshot != persistedSnapshot) {
                        dispatchEvent(CreateKeywordEvent.DraftSaveStarted)
                        if (!pendingCreationStore.persistKeywordSnapshot(snapshot)) {
                            dispatchEvent(CreateKeywordEvent.DraftSaveFinished(snapshot, saved = false))
                            return@launch
                        }
                        persistedSnapshot = snapshot
                        analytics.track(AnalyticsEvent.DraftSaved(CreateStep.KEYWORD))
                    }
                    dispatchEvent(CreateKeywordEvent.DraftSaveFinished(snapshot, saved = true))
                    scheduleSavedDisplayReset()
                }
        }

        private fun scheduleSavedDisplayReset() {
            savedDisplayJob?.cancel()
            savedDisplayJob =
                viewModelScope.launch {
                    delay(DRAFT_SAVED_DISPLAY_MS)
                    dispatchEvent(CreateKeywordEvent.DraftSavedDisplayExpired)
                }
        }

        /**
         * 키워드 단계 이탈. 저장하지 않은 입력은 사용자가 버리기로 한 것이라 여기서 저장하지 않는다.
         *
         * 뒤 단계의 진행 중 레코드가 슬롯에 있으면 건드리지 않는다 — 서버에서 실제로 돌고 있는
         * 복구 대상이 우선한다. 그 판정과 정리는 스토어가 소유하므로 여기서는 넘기기만 한다.
         */
        private suspend fun leaveFunnel() {
            // 저장은 취소하지 않고 끝날 때까지 기다린다 — 쓰기를 끊으면 무엇이 남았는지 알 수 없다.
            draftSaveJob?.join()
            storylineGenerationStore.leaveFunnel()
            dispatchEffect(CreateKeywordEffect.ExitFunnel)
        }

        /**
         * 요청을 시작하면서 동시에 스토리라인 단계로 전환한다 — 로딩은 다음 단계 화면이 그리고,
         * 이 화면은 스토리라인 목적지로 대체되어 사라지므로 진행 플래그는 해제하지 않는다.
         * 실행은 스토어의 퍼널 스코프가 담아 이 ViewModel 이 죽어도 계속된다.
         */
        private suspend fun generateStorylines(state: CreateKeywordUiState) {
            dispatchEvent(CreateKeywordEvent.GenerateAttempted)
            if (!state.canGenerateStorylines) return
            if (state.isGeneratingStorylines) return
            // 진행 중 KeywordDraft 쓰기가 요청 직전 Generating 레코드를 늦게 덮지 않게 먼저 합류한다.
            draftSaveJob?.join()
            dispatchEvent(CreateKeywordEvent.StorylineGenerationStarted)
            analytics.track(AnalyticsEvent.StoryGenerationRequested)
            storylineGenerationStore.generate(state.toGenerationInput())
            dispatchEffect(CreateKeywordEffect.NavigateToStoryline)
        }

        override fun reduce(
            state: CreateKeywordUiState,
            event: CreateKeywordEvent,
        ): CreateKeywordUiState = reduceKeywordState(state, event)
    }

/** 선택 상한에 걸리면 이벤트를 내지 않는다. 판정 재료가 모두 상태라 상태 옆에 둔다. */
private fun CreateKeywordUiState.providedTagToggleEvent(
    intent: CreateKeywordIntent.ToggleProvidedTag,
): CreateKeywordEvent? {
    val selecting =
        when (intent.target) {
            KeywordTarget.Genre -> intent.tagId !in selectedGenreTagIds
            else -> character(intent.target)?.let { intent.tagId !in it.selectedTagIds } ?: return null
        }
    if (selecting && isAtSelectionCap(intent.target)) return null
    return CreateKeywordEvent.ProvidedTagToggled(intent.target, intent.tagId)
}

private fun CreateKeywordUiState.customTagToggleEvent(
    intent: CreateKeywordIntent.ToggleCustomTag,
): CreateKeywordEvent? {
    val customTags =
        when (intent.target) {
            KeywordTarget.Genre -> customGenreTags
            else -> character(intent.target)?.customTags ?: return null
        }
    val tag = customTags.getOrNull(intent.index) ?: return null
    if (!tag.selected && isAtSelectionCap(intent.target)) return null
    return CreateKeywordEvent.CustomTagToggled(intent.target, intent.index)
}

private fun CreateKeywordUiState.customTagAddEvent(intent: CreateKeywordIntent.AddCustomTag): CreateKeywordEvent? {
    val name = intent.name.trim().take(CreateKeywordUiState.CUSTOM_TAG_MAX_LENGTH)
    if (name.isEmpty()) return null
    if (isAtSelectionCap(intent.target)) return null
    return CreateKeywordEvent.CustomTagAdded(intent.target, name)
}

/** 뒤 단계의 생성·완성·결과 레코드는 키워드 편집본보다 우선한다. */
private suspend fun PendingStoryCreationStore.persistKeywordSnapshot(snapshot: KeywordDraftSnapshot): Boolean {
    val current = read()
    if (current != null && current !is PendingStoryCreation.KeywordDraft) return false
    // 입력을 모두 지운 상태를 저장하면 남아 있던 저장본도 함께 사라져야 한다.
    if (!snapshot.hasInput) {
        return if (current is PendingStoryCreation.KeywordDraft) clear() else true
    }
    return write(PendingStoryCreation.KeywordDraft(snapshot))
}

/** 자동 저장할 편집 상태. 선택 해제된 커스텀 키워드도 그대로 담는다. */
internal fun CreateKeywordUiState.toKeywordSnapshot(): KeywordDraftSnapshot =
    KeywordDraftSnapshot(
        selectedGenreTagIds = selectedGenreTagIds.toList(),
        customGenreTags = customGenreTags.map { KeywordCustomTagSnapshot(it.name, it.selected) },
        protagonist = protagonist.toSnapshot(),
        supportingCharacters = supportingCharacters.map { it.toSnapshot() },
    )

private fun KeywordCharacter.toSnapshot(): KeywordCharacterSnapshot =
    KeywordCharacterSnapshot(
        name = name,
        gender = gender,
        selectedTagIds = selectedTagIds.toList(),
        customTags = customTags.map { KeywordCustomTagSnapshot(it.name, it.selected) },
    )

/**
 * 저장본으로 화면 상태를 되살린다. 인물 식별자는 화면 로컬 값이라 저장하지 않고 다시 매긴다.
 * 활성 카테고리는 담지 않아 항상 첫 탭에서 시작한다 — 완료된 카테고리는 잠금이 풀려 있어
 * 사용자가 바로 이동할 수 있다.
 */
internal fun KeywordDraftSnapshot.toKeywordUiState(base: CreateKeywordUiState): CreateKeywordUiState {
    val supporting =
        supportingCharacters.mapIndexed { index, character ->
            KeywordCharacter(
                id = CreateKeywordUiState.FIRST_SUPPORTING_ID + index,
                name = character.name,
                gender = character.gender,
                selectedTagIds = character.selectedTagIds.toSet(),
                customTags = character.customTags.map { CustomTag(it.name, it.selected) },
            )
        }
    return base.copy(
        isRestoring = false,
        selectedGenreTagIds = selectedGenreTagIds.toSet(),
        customGenreTags = customGenreTags.map { CustomTag(it.name, it.selected) },
        protagonist =
            KeywordCharacter(
                id = CreateKeywordUiState.PROTAGONIST_ID,
                name = protagonist.name,
                gender = protagonist.gender,
                selectedTagIds = protagonist.selectedTagIds.toSet(),
                customTags = protagonist.customTags.map { CustomTag(it.name, it.selected) },
            ),
        // 저장본에 인물이 없으면 진입 때와 같이 빈 섹션 하나를 놓는다.
        supportingCharacters =
            supporting.ifEmpty {
                listOf(KeywordCharacter(id = CreateKeywordUiState.FIRST_SUPPORTING_ID))
            },
        nextSupportingId = CreateKeywordUiState.FIRST_SUPPORTING_ID + supporting.size.coerceAtLeast(1),
    )
}

private fun CreateKeywordUiState.toGenerationInput(): StorylineGenerationInput =
    StorylineGenerationInput(
        genreTagIds = selectedGenreTagIds.toList(),
        customGenreTags = customGenreTags.filter(CustomTag::selected).map(CustomTag::name),
        protagonist = protagonist.toCharacterInput(),
        // 퍼널 진입 시 놓이는 빈 주변 인물 섹션을 그대로 보내면 의도하지 않은 인물이 AI 로
        // 채워지므로, 아무것도 입력하지 않은 섹션은 인원 의사가 없는 것으로 보고 제외한다.
        supportingCharacters =
            supportingCharacters
                .map(KeywordCharacter::toCharacterInput)
                .filterNot(StoryCharacterInput::isEmpty),
    )

private fun KeywordCharacter.toCharacterInput(): StoryCharacterInput =
    StoryCharacterInput(
        name = name.trim().ifEmpty { null },
        gender = gender,
        featureTagIds = selectedTagIds.toList(),
        customTags = customTags.filter(CustomTag::selected).map(CustomTag::name),
    )

private fun StoryCharacterInput.isEmpty(): Boolean =
    name == null && gender == null && featureTagIds.isEmpty() && customTags.isEmpty()

val StoryTagCategory.previous: StoryTagCategory? get() = StoryTagCategory.entries.getOrNull(ordinal - 1)
val StoryTagCategory.next: StoryTagCategory? get() = StoryTagCategory.entries.getOrNull(ordinal + 1)

val StoryTagCategory.required: Boolean get() = this != StoryTagCategory.SUPPORTING_CHARACTER
