package app.manyak.create.general.presentation

import app.manyak.common.domain.error.DomainResult
import app.manyak.create.entity.GenreCatalog
import app.manyak.create.entity.StoryTag
import app.manyak.create.entity.StoryTagCategory
import app.manyak.create.testing.FakeStoryCreationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeneralGenreSearchTest {
    @Test
    fun `빈 검색어와 성공한 서버 검색 결과는 다시 조회하지 않는다`() =
        runTest {
            val queries = mutableListOf<String>()
            val repository =
                object : FakeStoryCreationRepository() {
                    override suspend fun genres(query: String): DomainResult<GenreCatalog> {
                        queries += query
                        return DomainResult.Success(catalog)
                    }
                }
            var state = GeneralGenreSearchState()
            val search = GeneralGenreSearch(repository, this, { state }, { state = it }, { _, _, _ -> })
            search.loadCatalog()
            advanceUntilIdle()
            search.expand(true)
            advanceUntilIdle()
            assertEquals(listOf(""), queries)
            search.query("ㄹㅁㅅ")
            advanceUntilIdle()
            search.expand(false)
            advanceUntilIdle()
            search.expand(true)
            advanceUntilIdle()
            assertEquals(listOf("", "ㄹㅁㅅ"), queries)
            assertEquals(listOf("로맨스"), state.results)
        }

    @Test
    fun `200ms 로딩 표시는 250ms 입력 대기 이후부터 계산한다`() =
        runTest {
            val response = CompletableDeferred<DomainResult<GenreCatalog>>()
            val repository =
                object : FakeStoryCreationRepository() {
                    override suspend fun genres(query: String) = response.await()
                }
            var state = GeneralGenreSearchState()
            val search = GeneralGenreSearch(repository, this, { state }, { state = it }, { _, _, _ -> })
            search.query("로맨스")
            advanceTimeBy(449)
            runCurrent()
            assertTrue(state.searching)
            assertFalse(state.showLoading)
            advanceTimeBy(1)
            runCurrent()
            assertTrue(state.showLoading)
            response.complete(DomainResult.Success(catalog))
            advanceUntilIdle()
            assertFalse(state.showLoading)
        }

    @Test
    fun `검색 닫기는 요청과 늦은 로딩을 취소한다`() =
        runTest {
            val response = CompletableDeferred<DomainResult<GenreCatalog>>()
            val repository =
                object : FakeStoryCreationRepository() {
                    override suspend fun genres(query: String) = response.await()
                }
            var state = GeneralGenreSearchState()
            val search = GeneralGenreSearch(repository, this, { state }, { state = it }, { _, _, _ -> })
            search.query("로맨스")
            advanceTimeBy(300)
            search.expand(false)
            runCurrent()
            response.complete(DomainResult.Success(catalog))
            advanceUntilIdle()
            assertFalse(state.expanded)
            assertFalse(state.showLoading)
            assertTrue(state.results.isEmpty())
        }
}

private val catalog =
    GenreCatalog(
        listOf(StoryTag(1, "로맨스", StoryTagCategory.GENRE)),
        listOf(StoryTag(1, "로맨스", StoryTagCategory.GENRE)),
    )
