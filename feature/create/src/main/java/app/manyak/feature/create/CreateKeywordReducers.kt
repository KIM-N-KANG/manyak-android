package app.manyak.feature.create

import app.manyak.core.domain.story.StoryTagCategory

internal fun reduceKeywordState(
    state: CreateKeywordUiState,
    event: CreateKeywordEvent,
): CreateKeywordUiState =
    when (event) {
        is CreateKeywordEvent.SnapshotRestored -> event.snapshot.toKeywordUiState(state)
        CreateKeywordEvent.RestoreFinished -> state.copy(isRestoring = false)
        CreateKeywordEvent.DraftSavePending -> state.copy(draftSaveStatus = DraftSaveStatus.SAVING)
        is CreateKeywordEvent.DraftSaveFinished ->
            if (state.toKeywordSnapshot() == event.snapshot) {
                state.copy(
                    draftSaveStatus =
                        if (event.saved) DraftSaveStatus.SAVED else DraftSaveStatus.HIDDEN,
                )
            } else {
                state
            }

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
        else ->
            reduceKeywordInput(state, event).let { updated ->
                if (updated == state) updated else updated.copy(draftSaveStatus = DraftSaveStatus.SAVING)
            }
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
