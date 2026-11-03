package app.manyak.home.data.repository

import app.manyak.common.domain.error.DomainResult
import app.manyak.home.data.api.ExploreApi
import app.manyak.home.data.api.StoryApi
import app.manyak.home.data.dto.StoryHomeDto
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
                HomeRepositoryImpl(api, api).publicStories(
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

            val result = HomeRepositoryImpl(api, api).publicStories(StoryListQuery())

            assertEquals(listOf("all", "popular", null), api.lastRequest)
            assertEquals(null, (result as DomainResult.Success).value.nextCursor)
        }

    @Test
    fun `홈 섹션은 기본 섹션의 빈 id 와 빈 섹션까지 서버 순서대로 옮긴다`() =
        runTest {
            val body =
                """
                {"sections":[
                  {"id":null,"name":"인기 스토리","items":[{"id":"s1","title":"t","author":null}]},
                  {"id":"9b2f","name":"새 이야기","items":[]}
                ]}
                """.trimIndent()
            val api = RecordingStoryApi(StoryPageDto(), Json.decodeFromString<StoryHomeDto>(body))

            val sections = (HomeRepositoryImpl(api, api).homeSections() as DomainResult.Success).value

            assertEquals(listOf(null, "9b2f"), sections.map { section -> section.id })
            assertEquals(listOf("인기 스토리", "새 이야기"), sections.map { section -> section.name })
            assertEquals(
                null,
                sections
                    .first()
                    .stories
                    .single()
                    .authorNickname,
            )
            assertEquals(emptyList<Any>(), sections.last().stories)
        }

    @Test
    fun `탐색은 받은 커서를 그대로 보낸다`() =
        runTest {
            val api = RecordingStoryApi(StoryPageDto(nextCursor = "c2"))

            val result = HomeRepositoryImpl(api, api).exploreStories(cursor = "c1")

            assertEquals(listOf("explore", "c1"), api.lastRequest)
            assertEquals("c2", (result as DomainResult.Success).value.nextCursor)
        }
}

private class RecordingStoryApi(
    private val page: StoryPageDto,
    private val home: StoryHomeDto = StoryHomeDto(),
) : StoryApi,
    ExploreApi {
    var lastRequest: List<String?> = emptyList()

    override suspend fun publicStories(
        filter: String,
        sort: String,
        cursor: String?,
    ): Response<StoryPageDto> {
        lastRequest = listOf(filter, sort, cursor)
        return Response.success(page)
    }

    override suspend fun home(): Response<StoryHomeDto> = Response.success(home)

    override suspend fun explore(cursor: String?): Response<StoryPageDto> {
        lastRequest = listOf("explore", cursor)
        return Response.success(page)
    }
}
