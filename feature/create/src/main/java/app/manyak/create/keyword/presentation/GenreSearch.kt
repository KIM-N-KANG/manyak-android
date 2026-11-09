package app.manyak.create.keyword.presentation

import app.manyak.common.domain.error.DomainResult
import app.manyak.create.domain.StoryCreationRepository
import app.manyak.create.entity.GenreCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal class GenreSearch(
    private val repository: StoryCreationRepository,
    private val scope: CoroutineScope,
    private val state: () -> CreateKeywordUiState,
    private val onEvent: suspend (CreateKeywordEvent) -> Unit,
) {
    private var genreCatalogJob: Deferred<DomainResult<GenreCatalog>>? = null
    private var genreSearchJob: Job? = null
    private val cachedResults = linkedMapOf<String, GenreCatalog>()

    suspend fun handle(intent: CreateKeywordIntent.GenreInput) {
        val state = state()
        when (intent) {
            is CreateKeywordIntent.SearchGenres -> searchGenres(intent.query)
            is CreateKeywordIntent.ExpandGenres -> {
                onEvent(CreateKeywordEvent.GenreExpandedChanged(intent.expanded))
                if (intent.expanded) {
                    searchGenres(state.genrePicker.query, debounce = false)
                } else {
                    genreSearchJob?.cancel()
                }
            }
            is CreateKeywordIntent.SelectGenre -> selectGenre(intent.id)
            CreateKeywordIntent.RetryGenres -> {
                if (state.genrePicker.catalog == null) loadCatalog()
                searchGenres(state.genrePicker.query, debounce = false)
            }
        }
    }

    fun loadCatalog(): Deferred<DomainResult<GenreCatalog>> {
        genreCatalogJob?.takeIf { it.isActive }?.let { return it }
        return scope
            .async {
                val result = repository.genres()
                currentCoroutineContext().ensureActive()
                when (result) {
                    is DomainResult.Success -> onEvent(CreateKeywordEvent.GenreCatalogLoaded(result.value))
                    is DomainResult.Failure -> onEvent(CreateKeywordEvent.GenreCatalogFailed)
                }
                result
            }.also { genreCatalogJob = it }
    }

    private suspend fun searchGenres(
        query: String,
        debounce: Boolean = true,
    ) {
        genreSearchJob?.cancel()
        val limitedQuery = query.take(GENRE_QUERY_MAX_LENGTH)
        val cached = if (limitedQuery.isEmpty()) state().genrePicker.catalog else cachedResults[limitedQuery]
        onEvent(CreateKeywordEvent.GenreQueryChanged(limitedQuery))
        if (cached != null) {
            onEvent(CreateKeywordEvent.GenreSearchFinished(limitedQuery, DomainResult.Success(cached)))
            return
        }
        genreSearchJob =
            scope.launch {
                if (debounce) delay(GENRE_SEARCH_DELAY_MS)
                val loading =
                    launch {
                        delay(GENRE_LOADING_DELAY_MS)
                        onEvent(CreateKeywordEvent.GenreSearchLoading(limitedQuery))
                    }
                try {
                    val result = if (limitedQuery.isEmpty()) loadCatalog().await() else repository.genres(limitedQuery)
                    currentCoroutineContext().ensureActive()
                    if (result is DomainResult.Success && limitedQuery.isNotEmpty()) {
                        cachedResults[limitedQuery] = result.value
                        if (cachedResults.size >
                            GENRE_SEARCH_CACHE_SIZE
                        ) {
                            cachedResults.remove(cachedResults.keys.first())
                        }
                    }
                    onEvent(CreateKeywordEvent.GenreSearchFinished(limitedQuery, result))
                } finally {
                    loading.cancel()
                }
            }
    }

    private suspend fun selectGenre(id: Long) {
        val state = state()
        if (state.isRestoring ||
            state.genrePicker.catalog
                ?.genres
                ?.none { it.id == id } != false
        ) {
            return
        }
        if (id !in state.selectedGenreTagIds && state.isAtSelectionCap(KeywordTarget.Genre)) return
        genreSearchJob?.cancel()
        onEvent(CreateKeywordEvent.GenreSelected(id))
    }
}

private const val GENRE_QUERY_MAX_LENGTH = 30
private const val GENRE_SEARCH_DELAY_MS = 250L
private const val GENRE_LOADING_DELAY_MS = 200L
private const val GENRE_SEARCH_CACHE_SIZE = 20
