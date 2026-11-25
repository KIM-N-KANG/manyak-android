package app.manyak.home.data.repository

import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.home.entity.StoryListFilter
import app.manyak.home.entity.StoryListQuery
import app.manyak.home.entity.StoryListSort
import app.manyak.home.testing.FakeStoryRepository
import app.manyak.home.testing.sampleStories
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PopularOriginalStoriesImplTest {
    private val repository = FakeStoryRepository()
    private val stories = PopularOriginalStoriesImpl(repository)

    @Test
    fun `오리지널 채팅순 첫 페이지의 스토리만 돌려준다`() =
        runTest {
            assertEquals(DomainResult.Success(sampleStories()), stories.firstPage())
            assertEquals(
                listOf(StoryListQuery(StoryListFilter.ORIGINAL, StoryListSort.CHATS) to null),
                repository.requests,
            )
        }

    @Test
    fun `조회 실패는 그대로 전달한다`() =
        runTest {
            repository.queuedResults += DomainResult.Failure(DomainError.Network)

            assertEquals(DomainResult.Failure(DomainError.Network), stories.firstPage())
        }
}
