package app.manyak.create.keyword.presentation

import app.manyak.common.domain.error.DomainResult
import app.manyak.create.entity.GenreCatalog
import app.manyak.create.entity.StoryTag
import app.manyak.create.entity.StoryTagCategory
import app.manyak.create.testing.FakeStoryCreationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GenreSearchLoadingTest {
    @Test
    fun `빠른 응답은 입력 대기 중에도 응답 후에도 로딩 문구를 표시하지 않는다`() =
        runTest {
            val repository =
                object : FakeStoryCreationRepository() {
                    override suspend fun genres(query: String): DomainResult<GenreCatalog> {
                        delay(100)
                        return DomainResult.Success(loadingCatalog)
                    }
                }
            var state = loadedState()
            val loadingHistory = mutableListOf<Boolean>()
            val search =
                GenreSearch(repository, this, { state }) {
                    state = reduceKeywordState(state, it)
                    loadingHistory += state.genrePicker.showLoading
                }
            search.handle(CreateKeywordIntent.SearchGenres("로맨스"))
            advanceUntilIdle()
            assertFalse(loadingHistory.any { it })
            assertFalse(state.genrePicker.searching)
        }

    @Test
    fun `입력 대기를 제외한 요청 시간이 200ms에 도달해야 로딩 문구를 표시한다`() =
        runTest {
            val response = CompletableDeferred<DomainResult<GenreCatalog>>()
            val repository =
                object : FakeStoryCreationRepository() {
                    override suspend fun genres(query: String) = response.await()
                }
            var state = loadedState()
            val search = GenreSearch(repository, this, { state }) { state = reduceKeywordState(state, it) }
            search.handle(CreateKeywordIntent.SearchGenres("로맨스"))
            advanceTimeBy(250 + 199)
            runCurrent()
            assertTrue(state.genrePicker.searching)
            assertFalse(state.genrePicker.showLoading)
            advanceTimeBy(1)
            runCurrent()
            assertTrue(state.genrePicker.showLoading)
            response.complete(DomainResult.Success(loadingCatalog))
            advanceUntilIdle()
            assertFalse(state.genrePicker.showLoading)
            assertFalse(state.genrePicker.searching)
        }

    @Test
    fun `검색어를 바꾸거나 메뉴를 닫으면 이전 로딩 타이머를 취소한다`() =
        runTest {
            val repository =
                object : FakeStoryCreationRepository() {
                    override suspend fun genres(query: String): DomainResult<GenreCatalog> {
                        delay(5_000)
                        return DomainResult.Success(loadingCatalog)
                    }
                }
            var state = loadedState()
            val search = GenreSearch(repository, this, { state }) { state = reduceKeywordState(state, it) }
            search.handle(CreateKeywordIntent.SearchGenres("로"))
            advanceTimeBy(449)
            runCurrent()
            search.handle(CreateKeywordIntent.SearchGenres("로맨스"))
            advanceTimeBy(1)
            runCurrent()
            assertFalse(state.genrePicker.showLoading)
            advanceTimeBy(449)
            runCurrent()
            assertTrue(state.genrePicker.showLoading)
            search.handle(CreateKeywordIntent.ExpandGenres(false))
            advanceUntilIdle()
            assertFalse(state.genrePicker.showLoading)
            assertFalse(state.genrePicker.searching)
            assertFalse(state.genrePicker.expanded)
            assertEquals(loadingCatalog.genres, state.genrePicker.results)
        }

    @Test
    fun `처음 목록을 열었다 닫아도 카탈로그 요청은 완료되어 재개할 때 재사용한다`() =
        runTest {
            var requests = 0
            val response = CompletableDeferred<DomainResult<GenreCatalog>>()
            val repository =
                object : FakeStoryCreationRepository() {
                    override suspend fun genres(query: String): DomainResult<GenreCatalog> {
                        requests += 1
                        return response.await()
                    }
                }
            var state = CreateKeywordUiState(isRestoring = false)
            val search = GenreSearch(repository, this, { state }) { state = reduceKeywordState(state, it) }
            search.handle(CreateKeywordIntent.ExpandGenres(true))
            runCurrent()
            search.handle(CreateKeywordIntent.ExpandGenres(false))
            response.complete(DomainResult.Success(loadingCatalog))
            advanceUntilIdle()
            assertFalse(state.genrePicker.expanded)
            assertTrue(state.genrePicker.results.isEmpty())
            search.handle(CreateKeywordIntent.ExpandGenres(true))
            assertFalse(state.genrePicker.searching)
            assertEquals(loadingCatalog.genres, state.genrePicker.results)
            assertEquals(1, requests)
        }

    @Test
    fun `검색 캐시는 최근 성공한 20개까지만 유지하고 새 화면에는 전달하지 않는다`() =
        runTest {
            val requests = mutableListOf<String>()
            val repository =
                object : FakeStoryCreationRepository() {
                    override suspend fun genres(query: String): DomainResult<GenreCatalog> {
                        requests += query
                        return DomainResult.Success(loadingCatalog)
                    }
                }
            var state = loadedState()
            val search = GenreSearch(repository, this, { state }) { state = reduceKeywordState(state, it) }
            repeat(21) {
                search.handle(CreateKeywordIntent.SearchGenres("장르$it"))
                advanceUntilIdle()
            }
            search.handle(CreateKeywordIntent.SearchGenres("장르20"))
            assertFalse(state.genrePicker.searching)
            assertEquals(21, requests.size)
            search.handle(CreateKeywordIntent.SearchGenres("장르0"))
            advanceUntilIdle()
            assertEquals(22, requests.size)
            val nextScreen = GenreSearch(repository, this, { state }) { state = reduceKeywordState(state, it) }
            nextScreen.handle(CreateKeywordIntent.SearchGenres("장르20"))
            advanceUntilIdle()
            assertEquals(23, requests.size)
        }

    @Test
    fun `빈 검색어로 목록을 반복해 열어도 이미 받은 카탈로그를 즉시 사용한다`() =
        runTest {
            var requests = 0
            val repository =
                object : FakeStoryCreationRepository() {
                    override suspend fun genres(query: String): DomainResult<GenreCatalog> {
                        requests += 1
                        return DomainResult.Success(loadingCatalog)
                    }
                }
            var state = loadedState()
            val search = GenreSearch(repository, this, { state }) { state = reduceKeywordState(state, it) }
            repeat(2) {
                search.handle(CreateKeywordIntent.ExpandGenres(true))
                assertFalse(state.genrePicker.searching)
                assertEquals(loadingCatalog.genres, state.genrePicker.results)
                search.handle(CreateKeywordIntent.ExpandGenres(false))
            }
            advanceUntilIdle()
            assertEquals(0, requests)
        }

    @Test
    fun `초기 카탈로그를 받는 중 목록을 열면 같은 요청을 기다린다`() =
        runTest {
            var requests = 0
            val response = CompletableDeferred<DomainResult<GenreCatalog>>()
            val repository =
                object : FakeStoryCreationRepository() {
                    override suspend fun genres(query: String): DomainResult<GenreCatalog> {
                        requests += 1
                        return response.await()
                    }
                }
            var state = CreateKeywordUiState(isRestoring = false)
            val search = GenreSearch(repository, this, { state }) { state = reduceKeywordState(state, it) }
            search.loadCatalog()
            runCurrent()
            search.handle(CreateKeywordIntent.ExpandGenres(true))
            runCurrent()
            response.complete(DomainResult.Success(loadingCatalog))
            advanceUntilIdle()
            assertEquals(1, requests)
            assertEquals(loadingCatalog.genres, state.genrePicker.results)
            assertFalse(state.genrePicker.searching)
        }

    @Test
    fun `성공한 검색어는 빈 결과까지 재요청 없이 즉시 재사용한다`() =
        runTest {
            val requests = mutableListOf<String>()
            val repository =
                object : FakeStoryCreationRepository() {
                    override suspend fun genres(query: String): DomainResult<GenreCatalog> {
                        requests += query
                        return DomainResult.Success(
                            loadingCatalog.copy(genres = if (query == "없음") emptyList() else loadingCatalog.genres),
                        )
                    }
                }
            var state = loadedState()
            val search = GenreSearch(repository, this, { state }) { state = reduceKeywordState(state, it) }
            for (query in listOf("로맨스", "없음")) {
                search.handle(CreateKeywordIntent.SearchGenres(query))
                advanceUntilIdle()
            }
            for (query in listOf("로맨스", "없음")) {
                search.handle(CreateKeywordIntent.SearchGenres(query))
                assertFalse(state.genrePicker.searching)
                assertEquals(query == "없음", state.genrePicker.results.isEmpty())
            }
            advanceUntilIdle()
            assertEquals(listOf("로맨스", "없음"), requests)
        }

    @Test
    fun `새 검색 결과를 기다리는 동안 기존 목록을 비우지 않는다`() =
        runTest {
            val response = CompletableDeferred<DomainResult<GenreCatalog>>()
            val repository =
                object : FakeStoryCreationRepository() {
                    override suspend fun genres(query: String) = response.await()
                }
            var state = loadedState()
            val search = GenreSearch(repository, this, { state }) { state = reduceKeywordState(state, it) }
            search.handle(CreateKeywordIntent.SearchGenres("로맨스"))
            advanceTimeBy(250)
            runCurrent()
            assertTrue(state.genrePicker.searching)
            assertEquals(loadingCatalog.genres, state.genrePicker.results)
            response.complete(DomainResult.Success(loadingCatalog.copy(genres = loadingCatalog.genres.take(1))))
            advanceUntilIdle()
            assertEquals(loadingCatalog.genres.take(1), state.genrePicker.results)
        }
}

private val loadingCatalog =
    GenreCatalog(
        genres =
            listOf(
                StoryTag(1, "로맨스", StoryTagCategory.GENRE),
                StoryTag(2, "판타지", StoryTagCategory.GENRE),
            ),
        featuredGenres = emptyList(),
    )

private fun loadedState() =
    CreateKeywordUiState(
        isRestoring = false,
        genrePicker = GenrePickerState(catalog = loadingCatalog, results = loadingCatalog.genres, expanded = true),
    )
