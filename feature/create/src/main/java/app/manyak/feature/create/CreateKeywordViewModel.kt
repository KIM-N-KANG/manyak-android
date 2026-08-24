package app.manyak.feature.create

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.CharacterGender
import app.manyak.core.domain.story.StoryCreationRepository
import app.manyak.core.domain.story.StoryTag
import app.manyak.core.domain.story.StoryTagCategory
import app.manyak.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val activeCategory: StoryTagCategory = StoryTagCategory.GENRE,
    /** "다음"을 눌러 검증에 실패한 카테고리. 그 카테고리가 활성일 때만 푸터에 오류를 표시한다. */
    val validationErrorCategory: StoryTagCategory? = null,
    /** "스토리라인 만들기"를 한 번이라도 눌렀는지. 이름 중복 푸터 오류는 이 뒤에만 노출한다. */
    val hasAttemptedGenerate: Boolean = false,
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

@HiltViewModel
class CreateKeywordViewModel
    @Inject
    constructor(
        private val storyCreationRepository: StoryCreationRepository,
    ) : MviViewModel<CreateKeywordIntent, CreateKeywordUiState, CreateKeywordEvent, Nothing>(CreateKeywordUiState()) {
        private var tagsLoadJob: Job? = null

        init {
            startTagsLoad()
        }

        private fun startTagsLoad(showLoading: Boolean = false) {
            if (tagsLoadJob?.isActive == true) return
            tagsLoadJob =
                viewModelScope.launch {
                    if (showLoading) dispatchEvent(CreateKeywordEvent.TagsReloadStarted)
                    loadTags()
                }
        }

        private suspend fun loadTags() {
            when (val result = storyCreationRepository.tags()) {
                is DomainResult.Success ->
                    dispatchEvent(CreateKeywordEvent.TagsLoaded(result.value.groupBy(StoryTag::category)))

                is DomainResult.Failure -> dispatchEvent(CreateKeywordEvent.TagsLoadFailed)
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

        private suspend fun generateStorylines(state: CreateKeywordUiState) {
            dispatchEvent(CreateKeywordEvent.GenerateAttempted)
            if (!state.canGenerateStorylines) return
            // 스토리라인 생성 요청과 다음 단계 전환은 스토리라인 선택 화면 구현과 함께 붙는다.
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
                is CreateKeywordEvent.TagsLoaded -> state.copy(providedTags = ProvidedTags.Loaded(event.byCategory))
                CreateKeywordEvent.TagsLoadFailed -> state.copy(providedTags = ProvidedTags.Failed)
                CreateKeywordEvent.TagsReloadStarted -> state.copy(providedTags = ProvidedTags.Loading)
                is CreateKeywordEvent.CategoryChanged -> state.copy(activeCategory = event.category)
                is CreateKeywordEvent.ValidationFailed -> state.copy(validationErrorCategory = event.category)
                CreateKeywordEvent.GenerateAttempted -> state.copy(hasAttemptedGenerate = true)
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
