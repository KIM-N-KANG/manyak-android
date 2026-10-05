package app.manyak.create.keyword.presentation

import app.manyak.common.domain.error.DomainResult
import app.manyak.create.entity.GenreCatalog
import app.manyak.create.entity.StoryTag
import app.manyak.create.entity.StoryTagCategory

data class GenrePickerState(
    val catalog: GenreCatalog? = null,
    val catalogFailed: Boolean = false,
    val query: String = "",
    val expanded: Boolean = false,
    val results: List<StoryTag> = emptyList(),
    val searching: Boolean = false,
    val showLoading: Boolean = false,
    val searchFailed: Boolean = false,
    val needsReselection: Boolean = false,
)

internal fun CreateKeywordUiState.genreChips(): List<StoryTag> {
    val catalog = genrePicker.catalog ?: return emptyList()
    val restored =
        selectedGenreTagIds.filter { id ->
            id !in addedGenreTagIds && catalog.featuredGenres.none { it.id == id }
        }
    val added = catalog.additionalGenreIds(restored + addedGenreTagIds)
    return catalog.featuredGenres +
        added.mapNotNull { id -> catalog.genres.firstOrNull { it.id == id } }
}

private fun GenreCatalog.additionalGenreIds(ids: List<Long>): List<Long> {
    val featuredIds = featuredGenres.map(StoryTag::id).toSet()
    val validIds = genres.map(StoryTag::id).toSet()
    return ids.filter { it in validIds && it !in featuredIds }.distinct()
}

/** 이전 직접 입력 중 정식 이름과 같은 항목만 제공 ID로 복원한다. 나머지 원문은 초안에 보존한다. */
internal fun CreateKeywordUiState.reconcileGenres(): CreateKeywordUiState {
    val catalog = genrePicker.catalog ?: return this
    val byName = catalog.genres.associateBy { it.name }
    val validIds = catalog.genres.map { it.id }.toSet()
    val matched = customGenreTags.mapNotNull { tag -> byName[tag.name]?.let { it to tag.selected } }
    val selected =
        (selectedGenreTagIds.filter { it in validIds } + matched.filter { it.second }.map { it.first.id })
            .distinct()
            .take(CreateKeywordUiState.GENRE_MAX_SELECTION)
            .toSet()
    val unmatched = customGenreTags.filter { it.name !in byName }
    val restored =
        selectedGenreTagIds.filter { id ->
            id in validIds && id !in addedGenreTagIds && catalog.featuredGenres.none { it.id == id }
        }
    return copy(
        selectedGenreTagIds = selected,
        addedGenreTagIds =
            catalog.additionalGenreIds(restored + addedGenreTagIds + matched.map { it.first.id }),
        customGenreTags = unmatched,
        genrePicker =
            genrePicker.copy(
                needsReselection =
                    genrePicker.needsReselection ||
                        unmatched.any { it.selected } ||
                        selectedGenreTagIds.any { it !in validIds },
            ),
    )
}

internal fun reduceGenrePicker(
    state: CreateKeywordUiState,
    event: CreateKeywordEvent,
): CreateKeywordUiState =
    when (event) {
        is CreateKeywordEvent.GenreCatalogLoaded ->
            state
                .copy(
                    genrePicker = state.genrePicker.copy(catalog = event.catalog, catalogFailed = false),
                ).reconcileGenres()
        CreateKeywordEvent.GenreCatalogFailed ->
            state.copy(
                genrePicker = state.genrePicker.copy(catalogFailed = true),
            )
        is CreateKeywordEvent.GenreQueryChanged ->
            state.copy(
                genrePicker =
                    state.genrePicker.copy(
                        query = event.query,
                        expanded = true,
                        searching = true,
                        showLoading = false,
                        searchFailed = false,
                    ),
            )
        is CreateKeywordEvent.GenreExpandedChanged ->
            state.copy(
                genrePicker =
                    state.genrePicker.copy(
                        expanded = event.expanded,
                        searching = event.expanded && state.genrePicker.searching,
                        showLoading = event.expanded && state.genrePicker.showLoading,
                    ),
            )
        is CreateKeywordEvent.GenreSearchLoading ->
            if (state.genrePicker.expanded && state.genrePicker.searching && state.genrePicker.query == event.query) {
                state.copy(genrePicker = state.genrePicker.copy(showLoading = true))
            } else {
                state
            }
        is CreateKeywordEvent.GenreSearchFinished -> state.applyGenreSearch(event)
        is CreateKeywordEvent.GenreSelected -> state.selectGenre(event.id)
        else -> state
    }

private fun CreateKeywordUiState.applyGenreSearch(event: CreateKeywordEvent.GenreSearchFinished): CreateKeywordUiState {
    if (event.query != genrePicker.query || !genrePicker.expanded) return this
    return copy(
        genrePicker =
            when (val result = event.result) {
                is DomainResult.Success ->
                    genrePicker.copy(
                        results = result.value.genres,
                        searching = false,
                        showLoading = false,
                        searchFailed = false,
                    )
                is DomainResult.Failure ->
                    genrePicker.copy(
                        results = emptyList(),
                        searching = false,
                        showLoading = false,
                        searchFailed = true,
                    )
            },
    )
}

private fun CreateKeywordUiState.selectGenre(id: Long): CreateKeywordUiState {
    val catalog = genrePicker.catalog ?: return this
    if (isRestoring || catalog.genres.none { it.id == id }) return this
    if (id !in selectedGenreTagIds && isAtSelectionCap(KeywordTarget.Genre)) return this
    val selected = if (id in selectedGenreTagIds) selectedGenreTagIds - id else selectedGenreTagIds + id
    return copy(
        selectedGenreTagIds = selected,
        addedGenreTagIds = catalog.additionalGenreIds(addedGenreTagIds + id),
        genrePicker =
            genrePicker.copy(
                query = "",
                expanded = false,
                searching = false,
                showLoading = false,
                searchFailed = false,
            ),
        validationErrorCategory =
            validationErrorCategory.takeUnless { it == StoryTagCategory.GENRE && selected.isNotEmpty() },
    )
}
