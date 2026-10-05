package app.manyak.create.general.presentation

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

data class GeneralGenreSearchState(
    val query: String = "",
    val expanded: Boolean = false,
    val results: List<String> = emptyList(),
    val searching: Boolean = false,
    val showLoading: Boolean = false,
    val failed: Boolean = false,
)

internal class GeneralGenreSearch(
    private val repository: StoryCreationRepository,
    private val scope: CoroutineScope,
    private val state: () -> GeneralGenreSearchState,
    private val onState: suspend (GeneralGenreSearchState) -> Unit,
    private val onCatalog: suspend (List<String>, List<String>, Boolean) -> Unit,
) {
    private var catalog: GenreCatalog? = null
    private var catalogRequest: Deferred<DomainResult<GenreCatalog>>? = null
    private var searchJob: Job? = null
    private var query = ""
    private var expanded = false
    private val cached = linkedMapOf<String, List<String>>()

    fun loadCatalog() {
        scope.launch { fetchCatalog() }
    }

    private suspend fun fetchCatalog(): DomainResult<GenreCatalog> {
        val pending =
            catalogRequest?.takeIf { it.isActive } ?: scope
                .async {
                    repository.genres().also { result ->
                        when (result) {
                            is DomainResult.Success -> {
                                catalog = result.value
                                onCatalog(
                                    result.value.genres.map { it.name },
                                    result.value.featuredGenres.map { it.name },
                                    false,
                                )
                            }
                            is DomainResult.Failure -> onCatalog(emptyList(), emptyList(), true)
                        }
                    }
                }.also { catalogRequest = it }
        return pending.await()
    }

    fun query(value: String) {
        query = value.take(QUERY_MAX_LENGTH)
        expanded = true
        search(debounce = true)
    }

    fun expand(value: Boolean) {
        expanded = value
        if (value) {
            search(debounce = false)
        } else {
            searchJob?.cancel()
            scope.launch { onState(state().copy(expanded = false, searching = false, showLoading = false)) }
        }
    }

    fun retry() {
        if (catalog == null) loadCatalog()
        if (expanded) search(debounce = false)
    }

    private fun search(debounce: Boolean) {
        searchJob?.cancel()
        val requested = query
        searchJob =
            scope.launch {
                val names = if (requested.isEmpty()) catalog?.genres?.map { it.name } else cached[requested]
                if (names != null) {
                    onState(GeneralGenreSearchState(query = requested, expanded = true, results = names))
                    return@launch
                }
                onState(
                    state().copy(
                        query = requested,
                        expanded = true,
                        searching = true,
                        showLoading = false,
                        failed = false,
                    ),
                )
                if (debounce) delay(QUERY_DELAY_MS)
                val spinner =
                    launch {
                        delay(LOADING_DELAY_MS)
                        if (expanded &&
                            query == requested
                        ) {
                            onState(
                                state().copy(query = requested, expanded = true, searching = true, showLoading = true),
                            )
                        }
                    }
                try {
                    val result = if (requested.isEmpty()) fetchCatalog() else repository.genres(requested)
                    currentCoroutineContext().ensureActive()
                    if (!expanded || query != requested) return@launch
                    when (result) {
                        is DomainResult.Success -> {
                            val results = result.value.genres.map { it.name }
                            if (requested.isNotEmpty()) {
                                cached[requested] = results
                                if (cached.size > CACHE_LIMIT) cached.remove(cached.keys.first())
                            }
                            onState(GeneralGenreSearchState(requested, true, results))
                        }
                        is DomainResult.Failure -> onState(GeneralGenreSearchState(requested, true, failed = true))
                    }
                } finally {
                    spinner.cancel()
                }
            }
    }
}

private const val CACHE_LIMIT = 20

private const val QUERY_MAX_LENGTH = 30
private const val QUERY_DELAY_MS = 250L
private const val LOADING_DELAY_MS = 200L
