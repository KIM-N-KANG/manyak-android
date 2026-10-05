package app.manyak.create.keyword.presentation

import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.create.entity.GenreCatalog
import app.manyak.create.entity.StoryTag
import app.manyak.create.entity.StoryTagCategory
import app.manyak.create.testing.FakeStoryCreationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GenreSearchTest {
    @Test
    fun `대표 칩에서 선택한 장르를 검색에서 누르면 해제하고 원래 위치를 유지한다`() =
        runTest {
            var state = reduceKeywordState(readyState(), CreateKeywordEvent.ProvidedTagToggled(KeywordTarget.Genre, 1))
            val search =
                GenreSearch(FakeStoryCreationRepository(), this, { state }) {
                    state = reduceKeywordState(state, it)
                }
            search.handle(CreateKeywordIntent.SelectGenre(1))
            assertTrue(state.selectedGenreTagIds.isEmpty())
            assertTrue(state.addedGenreTagIds.isEmpty())
            assertEquals(listOf(1L, 2L), state.genreChips().map { it.id })
            assertFalse(state.genrePicker.expanded)
        }

    @Test
    fun `선택하지 않은 대표 장르를 검색에서 선택해도 추가 칩이 되지 않는다`() =
        runTest {
            var state = readyState()
            val search =
                GenreSearch(FakeStoryCreationRepository(), this, { state }) {
                    state = reduceKeywordState(state, it)
                }
            search.handle(CreateKeywordIntent.SelectGenre(1))
            assertEquals(setOf(1L), state.selectedGenreTagIds)
            assertTrue(state.addedGenreTagIds.isEmpty())
            assertEquals(listOf(1L, 2L), state.genreChips().map { it.id })
            state = reduceKeywordState(state, CreateKeywordEvent.ProvidedTagToggled(KeywordTarget.Genre, 1))
            assertTrue(state.selectedGenreTagIds.isEmpty())
        }

    @Test
    fun `이전 초안의 추가 목록에 대표 장르가 있어도 원래 대표 위치로 복원한다`() {
        val snapshot =
            readyState()
                .copy(
                    selectedGenreTagIds = setOf(1L, 3L),
                    addedGenreTagIds = listOf(2L, 3L, 1L),
                ).toKeywordSnapshot()
        for (catalogFirst in listOf(true, false)) {
            val initial = if (catalogFirst) readyState() else CreateKeywordUiState()
            val restored = reduceKeywordState(initial, CreateKeywordEvent.SnapshotRestored(snapshot))
            val loaded = reduceKeywordState(restored, CreateKeywordEvent.GenreCatalogLoaded(catalog))
            assertEquals(listOf(1L, 2L, 3L), loaded.genreChips().map { it.id })
            assertEquals(listOf(3L), loaded.toKeywordSnapshot().addedGenreTagIds)
            assertEquals(setOf(1L, 3L), loaded.selectedGenreTagIds)
        }
    }

    @Test
    fun `이전 제공 장르가 대표 목록에서 빠져도 선택 해제한 칩은 남는다`() {
        val restored = readyState().copy(selectedGenreTagIds = setOf(3)).reconcileGenres()
        val deselected =
            reduceKeywordState(
                restored,
                CreateKeywordEvent.ProvidedTagToggled(KeywordTarget.Genre, 3),
            )
        assertTrue(deselected.selectedGenreTagIds.isEmpty())
        assertEquals(listOf(3L), deselected.toKeywordSnapshot().addedGenreTagIds)
        assertEquals(3L, deselected.genreChips().last().id)
    }

    @Test
    fun `검색 응답 순서가 뒤집혀도 최신 검색만 반영한다`() =
        runTest {
            val oldResponse = CompletableDeferred<DomainResult<GenreCatalog>>()
            val calls = mutableListOf<String>()
            val repository =
                object : FakeStoryCreationRepository() {
                    override suspend fun genres(query: String): DomainResult<GenreCatalog> {
                        calls += query
                        return if (query == "로") {
                            withContext(NonCancellable) { oldResponse.await() }
                        } else {
                            DomainResult.Success(catalog.copy(genres = listOf(catalog.genres.last())))
                        }
                    }
                }
            var state = readyState()
            val search = GenreSearch(repository, this, { state }) { state = reduceKeywordState(state, it) }
            search.handle(CreateKeywordIntent.SearchGenres("로"))
            advanceTimeBy(250)
            runCurrent()
            search.handle(CreateKeywordIntent.SearchGenres("회"))
            advanceTimeBy(100)
            search.handle(CreateKeywordIntent.SearchGenres("회귀"))
            advanceTimeBy(250)
            runCurrent()
            oldResponse.complete(DomainResult.Success(catalog))
            advanceUntilIdle()
            assertEquals(listOf("로", "회귀"), calls)
            assertEquals("회귀", state.genrePicker.query)
            assertEquals(listOf(catalog.genres.last()), state.genrePicker.results)
        }

    @Test
    fun `추가 장르는 검색에서 토글해도 처음 추가한 칩 순서를 유지한다`() =
        runTest {
            var state = readyState()
            val search =
                GenreSearch(FakeStoryCreationRepository(), this, { state }) {
                    state = reduceKeywordState(state, it)
                }
            search.handle(CreateKeywordIntent.SelectGenre(3))
            search.handle(CreateKeywordIntent.SelectGenre(4))
            search.handle(CreateKeywordIntent.SelectGenre(3))
            assertEquals(listOf(1L, 2L, 3L, 4L), state.genreChips().map { it.id })
            assertEquals(setOf(4L), state.selectedGenreTagIds)
            search.handle(CreateKeywordIntent.SelectGenre(3))
            assertEquals(listOf(1L, 2L, 3L, 4L), state.genreChips().map { it.id })
            assertEquals(setOf(3L, 4L), state.selectedGenreTagIds)
            assertFalse(state.genrePicker.expanded)
            assertEquals("", state.genrePicker.query)
            val restored = state.toKeywordSnapshot().toKeywordUiState(readyState()).reconcileGenres()
            assertEquals(state.genreChips(), restored.genreChips())
            assertEquals(state.selectedGenreTagIds, restored.selectedGenreTagIds)
        }

    @Test
    fun `상한과 카탈로그 검증을 통과한 장르만 선택한다`() =
        runTest {
            var state = readyState().copy(selectedGenreTagIds = setOf(1, 2, 3))
            val search =
                GenreSearch(FakeStoryCreationRepository(), this, { state }) {
                    state = reduceKeywordState(state, it)
                }
            search.handle(CreateKeywordIntent.SelectGenre(4))
            search.handle(CreateKeywordIntent.SelectGenre(999))
            assertEquals(setOf(1L, 2L, 3L), state.selectedGenreTagIds)
            search.handle(CreateKeywordIntent.SelectGenre(2))
            assertEquals(listOf(1L, 2L, 3L), state.genreChips().map { it.id })
            assertEquals(setOf(1L, 3L), state.selectedGenreTagIds)
            assertEquals(2, state.genreSelectedCount)
            search.handle(CreateKeywordIntent.SelectGenre(4))
            assertEquals(setOf(1L, 3L, 4L), state.selectedGenreTagIds)
        }

    @Test
    fun `선택 뒤 늦은 응답이 와도 메뉴를 열거나 검색 결과를 바꾸지 않는다`() {
        val selected = reduceKeywordState(readyState(), CreateKeywordEvent.GenreSelected(3))
        val late =
            reduceKeywordState(selected, CreateKeywordEvent.GenreSearchFinished("", DomainResult.Success(catalog)))
        assertEquals(selected, late)
    }

    @Test
    fun `이전 직접 입력은 정식 이름만 변환하고 알 수 없는 입력은 전송하지 않는다`() {
        val restored =
            readyState()
                .copy(
                    selectedGenreTagIds = setOf(999),
                    customGenreTags = listOf(CustomTag("회귀", true), CustomTag("나만의 장르", true)),
                ).reconcileGenres()
        assertEquals(setOf(4L), restored.selectedGenreTagIds)
        assertEquals(listOf(CustomTag("나만의 장르", true)), restored.customGenreTags)
        assertTrue(restored.genrePicker.needsReselection)
        assertTrue(restored.toGenerationInput().customGenreTags.isEmpty())
        assertEquals(listOf(4L), restored.toGenerationInput().genreTagIds)
    }

    @Test
    fun `검색 실패는 재시도하고 빈 결과도 구분한다`() =
        runTest {
            var failed = true
            val repository =
                object : FakeStoryCreationRepository() {
                    override suspend fun genres(query: String): DomainResult<GenreCatalog> =
                        if (failed) {
                            DomainResult.Failure(DomainError.Network)
                        } else {
                            DomainResult.Success(catalog.copy(genres = emptyList()))
                        }
                }
            var state = readyState()
            val search = GenreSearch(repository, this, { state }) { state = reduceKeywordState(state, it) }
            search.handle(CreateKeywordIntent.SearchGenres("없는 장르"))
            advanceUntilIdle()
            assertTrue(state.genrePicker.searchFailed)
            failed = false
            search.handle(CreateKeywordIntent.RetryGenres)
            advanceUntilIdle()
            assertFalse(state.genrePicker.searchFailed)
            assertFalse(state.genrePicker.searching)
            assertTrue(state.genrePicker.results.isEmpty())
        }
}

private val catalog =
    listOf("로맨스", "판타지", "추리", "회귀")
        .mapIndexed { index, name ->
            StoryTag(index + 1L, name, StoryTagCategory.GENRE)
        }.let { GenreCatalog(it, it.take(2)) }

private fun readyState() =
    CreateKeywordUiState(
        isRestoring = false,
        genrePicker = GenrePickerState(catalog = catalog, expanded = true),
    )
