package app.manyak.home.data.repository

import app.manyak.common.domain.error.DomainResult
import app.manyak.home.data.api.StoryApi
import app.manyak.home.data.dto.StoryPageDto
import app.manyak.home.entity.StoryListFilter
import app.manyak.home.entity.StoryListQuery
import app.manyak.home.entity.StoryListSort
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class HomeRepositoryImplTest {
    @Test
    fun `조건을 서버 값으로 보내고 응답의 오리지널 여부와 다음 커서를 옮긴다`() =
        runTest {
            val body =
                """
                {"items":[{"id":"s1","title":"t","isOriginal":true},{"id":"s2","title":"u"}],"nextCursor":"c2"}
                """.trimIndent()
            val api = RecordingStoryApi(Json.decodeFromString<StoryPageDto>(body))

            val result =
                HomeRepositoryImpl(api).publicStories(
                    StoryListQuery(StoryListFilter.ORIGINAL, StoryListSort.CHATS),
                    cursor = "c1",
                )

            assertEquals(listOf("original", "chats", "c1"), api.lastRequest)
            val page = (result as DomainResult.Success).value
            assertEquals(listOf(true, false), page.items.map { story -> story.isOriginal })
            assertEquals("c2", page.nextCursor)
        }

    @Test
    fun `첫 페이지는 커서 없이 전체·인기순을 보낸다`() =
        runTest {
            val api = RecordingStoryApi(StoryPageDto(nextCursor = ""))

            val result = HomeRepositoryImpl(api).publicStories(StoryListQuery())

            assertEquals(listOf("all", "likes", null), api.lastRequest)
            assertEquals(null, (result as DomainResult.Success).value.nextCursor)
        }
}

private class RecordingStoryApi(
    private val page: StoryPageDto,
) : StoryApi {
    var lastRequest: List<String?> = emptyList()

    override suspend fun publicStories(
        filter: String,
        sort: String,
        cursor: String?,
    ): Response<StoryPageDto> {
        lastRequest = listOf(filter, sort, cursor)
        return Response.success(page)
    }
}
