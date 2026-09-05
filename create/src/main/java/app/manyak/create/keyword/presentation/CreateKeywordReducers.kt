package app.manyak.create.keyword.presentation

import app.manyak.create.entity.StoryTagCategory
import app.manyak.create.presentation.state.DraftSaveStatus

internal fun reduceKeywordState(
    state: CreateKeywordUiState,
    event: CreateKeywordEvent,
): CreateKeywordUiState =
    when (event) {
        // 복원한 화면은 디스크와 같은 상태다. 되살린 결과에서 다시 뽑아 기준선으로 삼는다 —
        // 저장본에 없던 빈 인물 섹션처럼 복원이 채워 넣은 것까지 변경으로 세지 않기 위해서다.
        is CreateKeywordEvent.SnapshotRestored ->
            event.snapshot.toKeywordUiState(state).let { it.copy(savedSnapshot = it.toKeywordSnapshot()) }

        CreateKeywordEvent.RestoreFinished ->
            state.copy(isRestoring = false, savedSnapshot = state.toKeywordSnapshot())

        CreateKeywordEvent.DraftSaveStarted -> state.copy(draftSaveStatus = DraftSaveStatus.SAVING)

        is CreateKeywordEvent.DraftSaveFinished ->
            if (event.saved) {
                state.copy(draftSaveStatus = DraftSaveStatus.SAVED, savedSnapshot = event.snapshot)
            } else {
                state.copy(draftSaveStatus = DraftSaveStatus.IDLE)
            }

        CreateKeywordEvent.DraftSavedDisplayExpired ->
            if (state.draftSaveStatus == DraftSaveStatus.SAVED) {
                state.copy(draftSaveStatus = DraftSaveStatus.IDLE)
            } else {
                state
            }

        is CreateKeywordEvent.ExitWarningChanged -> state.copy(exitWarning = event.warning)

        else -> reduceKeywordContent(state, event)
    }

private fun reduceKeywordContent(
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
                supportingCharacters = state.supportingCharacters + KeywordCharacter(id = state.nextSupportingId),
                nextSupportingId = state.nextSupportingId + 1,
            )

        is CreateKeywordEvent.SupportingCharacterRemoved ->
            state.copy(supportingCharacters = state.supportingCharacters.filterNot { it.id == event.characterId })

        else -> state
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
