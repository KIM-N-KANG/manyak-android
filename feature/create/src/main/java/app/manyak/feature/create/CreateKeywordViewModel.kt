package app.manyak.feature.create

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.CharacterGender
import app.manyak.core.domain.story.KeywordCharacterSnapshot
import app.manyak.core.domain.story.KeywordCustomTagSnapshot
import app.manyak.core.domain.story.KeywordDraftSnapshot
import app.manyak.core.domain.story.PendingStoryCreation
import app.manyak.core.domain.story.PendingStoryCreationStore
import app.manyak.core.domain.story.StoryCharacterInput
import app.manyak.core.domain.story.StoryCreationRepository
import app.manyak.core.domain.story.StoryTag
import app.manyak.core.domain.story.StoryTagCategory
import app.manyak.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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

    /** 시스템·헤더 뒤로가기 — 퍼널 이탈. 남은 내용의 임시 저장 처리 뒤 나간다. */
    data object LeaveFunnel : CreateKeywordIntent

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

    /** 퍼널 이탈 확정. 내용이 남았으면 "임시 저장되었어요" 토스트를 함께 띄운다. */
    data class ExitFunnel(
        val contentPreserved: Boolean,
    ) : CreateKeywordEffect
}

@HiltViewModel
class CreateKeywordViewModel
    @Inject
    constructor(
        private val storyCreationRepository: StoryCreationRepository,
        private val storylineGenerationStore: StorylineGenerationStore,
        private val pendingCreationStore: PendingStoryCreationStore,
    ) : MviViewModel<CreateKeywordIntent, CreateKeywordUiState, CreateKeywordEvent, CreateKeywordEffect>(
            CreateKeywordUiState(),
        ) {
        private var tagsLoadJob: Job? = null

        init {
            startTagsLoad()
            viewModelScope.launch {
                // 레코드가 남아 있는 진입은 곧 재개다. 재개 의도를 따로 저장하지 않는다.
                val record = pendingCreationStore.read()
                if (record is PendingStoryCreation.KeywordDraft) {
                    // 복원은 레코드를 소비한다 — 재개 후 다시 이탈하면 그 시점 상태로 새로 저장된다.
                    pendingCreationStore.clear()
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
                        is DomainResult.Success ->
                            dispatchEvent(CreateKeywordEvent.TagsLoaded(result.value.groupBy(StoryTag::category)))

                        is DomainResult.Failure -> dispatchEvent(CreateKeywordEvent.TagsLoadFailed)
                    }
                }
        }

        override suspend fun handleIntent(intent: CreateKeywordIntent) {
            val state = uiState.value
            when (intent) {
                is CreateKeywordIntent.SelectCategory ->
                    if (state.isUnlocked(intent.category)) {
                        dispatchEvent(CreateKeywordEvent.CategoryChanged(intent.category))
                    }

                CreateKeywordIntent.GoPrevious ->
                    state.activeCategory.previous?.let { dispatchEvent(CreateKeywordEvent.CategoryChanged(it)) }

                CreateKeywordIntent.GoNext -> goNext(state)
                CreateKeywordIntent.GenerateStorylines -> generateStorylines(state)
                CreateKeywordIntent.LeaveFunnel -> leaveFunnel()

                CreateKeywordIntent.RetryTags ->
                    if (state.providedTags is ProvidedTags.Failed) {
                        startTagsLoad(showLoading = true)
                    }

                else -> handleKeywordInput(state, intent)
            }
        }

        private suspend fun handleKeywordInput(
            state: CreateKeywordUiState,
            intent: CreateKeywordIntent,
        ) {
            when (intent) {
                is CreateKeywordIntent.ToggleProvidedTag -> toggleProvidedTag(state, intent)
                is CreateKeywordIntent.ToggleCustomTag -> toggleCustomTag(state, intent)
                is CreateKeywordIntent.AddCustomTag -> addCustomTag(state, intent)

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

        private suspend fun goNext(state: CreateKeywordUiState) {
            if (state.isComplete(state.activeCategory)) {
                state.activeCategory.next?.let { dispatchEvent(CreateKeywordEvent.CategoryChanged(it)) }
            } else {
                dispatchEvent(CreateKeywordEvent.ValidationFailed(state.activeCategory))
            }
        }

        /**
         * 키워드 단계 이탈. 생성 전이라 소실 경고는 없고, 입력이 남아 있으면 조용히 저장한다.
         *
         * 복원이 화면에 반영될 때까지 기다린다 — 헤더는 복원 중에도 눌리므로, 기다리지 않으면
         * 아직 비어 있는 상태를 스냅숏해 방금 소비한 저장분을 잃는다.
         *
         * 뒤 단계의 진행 중 레코드가 슬롯에 있으면 덮지 않는다 — 서버에서 실제로 돌고 있는
         * 복구 대상이 편집 스냅숏보다 우선한다. 뒤 단계에서 시작한 생성 결과의 임시 저장도
         * 스토어가 이미 처리하므로 여기서는 판정만 승계한다.
         */
        private suspend fun leaveFunnel() {
            val restored = uiState.first { !it.isRestoring }
            val storePreserved = storylineGenerationStore.leaveFunnel()
            if (storePreserved) {
                dispatchEffect(CreateKeywordEffect.ExitFunnel(contentPreserved = true))
                return
            }
            val snapshot = restored.toKeywordSnapshot()
            if (!snapshot.hasInput) {
                dispatchEffect(CreateKeywordEffect.ExitFunnel(contentPreserved = false))
                return
            }
            pendingCreationStore.write(PendingStoryCreation.KeywordDraft(snapshot))
            dispatchEffect(CreateKeywordEffect.ExitFunnel(contentPreserved = true))
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
            dispatchEvent(CreateKeywordEvent.StorylineGenerationStarted)
            storylineGenerationStore.generate(state.toGenerationInput())
            dispatchEffect(CreateKeywordEffect.NavigateToStoryline)
        }

        private suspend fun toggleProvidedTag(
            state: CreateKeywordUiState,
            intent: CreateKeywordIntent.ToggleProvidedTag,
        ) {
            val selecting =
                when (intent.target) {
                    KeywordTarget.Genre -> intent.tagId !in state.selectedGenreTagIds
                    else -> state.character(intent.target)?.let { intent.tagId !in it.selectedTagIds } ?: return
                }
            if (selecting && state.isAtSelectionCap(intent.target)) return
            dispatchEvent(CreateKeywordEvent.ProvidedTagToggled(intent.target, intent.tagId))
        }

        private suspend fun toggleCustomTag(
            state: CreateKeywordUiState,
            intent: CreateKeywordIntent.ToggleCustomTag,
        ) {
            val customTags =
                when (intent.target) {
                    KeywordTarget.Genre -> state.customGenreTags
                    else -> state.character(intent.target)?.customTags ?: return
                }
            val tag = customTags.getOrNull(intent.index) ?: return
            if (!tag.selected && state.isAtSelectionCap(intent.target)) return
            dispatchEvent(CreateKeywordEvent.CustomTagToggled(intent.target, intent.index))
        }

        private suspend fun addCustomTag(
            state: CreateKeywordUiState,
            intent: CreateKeywordIntent.AddCustomTag,
        ) {
            val name = intent.name.trim().take(CreateKeywordUiState.CUSTOM_TAG_MAX_LENGTH)
            if (name.isEmpty()) return
            if (state.isAtSelectionCap(intent.target)) return
            dispatchEvent(CreateKeywordEvent.CustomTagAdded(intent.target, name))
        }

        override fun reduce(
            state: CreateKeywordUiState,
            event: CreateKeywordEvent,
        ): CreateKeywordUiState =
            when (event) {
                is CreateKeywordEvent.SnapshotRestored -> event.snapshot.toKeywordUiState(state)
                CreateKeywordEvent.RestoreFinished -> state.copy(isRestoring = false)
                is CreateKeywordEvent.TagsLoaded -> state.copy(providedTags = ProvidedTags.Loaded(event.byCategory))
                CreateKeywordEvent.TagsLoadFailed -> state.copy(providedTags = ProvidedTags.Failed)
                CreateKeywordEvent.TagsReloadStarted -> state.copy(providedTags = ProvidedTags.Loading)
                is CreateKeywordEvent.CategoryChanged -> state.copy(activeCategory = event.category)
                is CreateKeywordEvent.ValidationFailed -> state.copy(validationErrorCategory = event.category)
                CreateKeywordEvent.GenerateAttempted -> state.copy(hasAttemptedGenerate = true)
                CreateKeywordEvent.StorylineGenerationStarted -> state.copy(isGeneratingStorylines = true)
                else -> reduceKeywordInput(state, event)
            }

        private fun reduceKeywordInput(
            state: CreateKeywordUiState,
            event: CreateKeywordEvent,
        ): CreateKeywordUiState =
            when (event) {
                is CreateKeywordEvent.ProvidedTagToggled ->
                    state
                        .updateTarget(event.target) { character ->
                            character.copy(selectedTagIds = character.selectedTagIds.toggle(event.tagId))
                        }.let {
                            if (event.target == KeywordTarget.Genre) {
                                it.copy(selectedGenreTagIds = it.selectedGenreTagIds.toggle(event.tagId))
                            } else {
                                it
                            }
                        }.clearValidationErrorIfComplete(event.target.category)

                is CreateKeywordEvent.CustomTagToggled ->
                    state
                        .updateCustomTags(event.target) { tags ->
                            tags.mapIndexed { index, tag ->
                                if (index == event.index) tag.copy(selected = !tag.selected) else tag
                            }
                        }.clearValidationErrorIfComplete(event.target.category)

                is CreateKeywordEvent.CustomTagAdded ->
                    state
                        .updateCustomTags(event.target) { tags -> tags + CustomTag(name = event.name, selected = true) }
                        .clearValidationErrorIfComplete(event.target.category)

                is CreateKeywordEvent.CharacterNameChanged ->
                    state.updateTarget(event.target) { it.copy(name = event.name) }

                is CreateKeywordEvent.CharacterGenderChanged ->
                    state.updateTarget(event.target) { it.copy(gender = event.gender) }

                CreateKeywordEvent.SupportingCharacterAdded ->
                    state.copy(
                        supportingCharacters =
                            state.supportingCharacters + KeywordCharacter(id = state.nextSupportingId),
                        nextSupportingId = state.nextSupportingId + 1,
                    )

                is CreateKeywordEvent.SupportingCharacterRemoved ->
                    state.copy(
                        supportingCharacters = state.supportingCharacters.filterNot { it.id == event.characterId },
                    )

                else -> state
            }
    }

/** 이탈 시 저장할 편집 상태. 선택 해제된 커스텀 키워드도 그대로 담는다. */
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

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

private fun CreateKeywordUiState.updateTarget(
    target: KeywordTarget,
    transform: (KeywordCharacter) -> KeywordCharacter,
): CreateKeywordUiState =
    when (target) {
        KeywordTarget.Genre -> this
        KeywordTarget.Protagonist -> copy(protagonist = transform(protagonist))
        is KeywordTarget.Supporting ->
            copy(
                supportingCharacters =
                    supportingCharacters.map { if (it.id == target.characterId) transform(it) else it },
            )
    }

private fun CreateKeywordUiState.updateCustomTags(
    target: KeywordTarget,
    transform: (List<CustomTag>) -> List<CustomTag>,
): CreateKeywordUiState =
    when (target) {
        KeywordTarget.Genre -> copy(customGenreTags = transform(customGenreTags))
        else -> updateTarget(target) { it.copy(customTags = transform(it.customTags)) }
    }

private fun CreateKeywordUiState.clearValidationErrorIfComplete(category: StoryTagCategory): CreateKeywordUiState =
    if (validationErrorCategory == category && isComplete(category)) {
        copy(validationErrorCategory = null)
    } else {
        this
    }

val StoryTagCategory.previous: StoryTagCategory? get() = StoryTagCategory.entries.getOrNull(ordinal - 1)
val StoryTagCategory.next: StoryTagCategory? get() = StoryTagCategory.entries.getOrNull(ordinal + 1)

val StoryTagCategory.required: Boolean get() = this != StoryTagCategory.SUPPORTING_CHARACTER
