package app.manyak.studio.data.repository

import app.manyak.common.data.story.StorySummaryDto
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.studio.data.api.StudioApi
import app.manyak.studio.data.dto.StorySubmissionDto
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class StudioRepositoryImplTest {
    @Test
    fun `본문 없는 삭제 응답과 이미 삭제된 스토리는 모두 성공이다`() =
        runTest {
            for (response in listOf(
                Response.success<Unit>(204, null),
                Response.error<Unit>(404, "".toResponseBody()),
            )) {
                val api = DeleteApi(response)
                val result = StudioRepositoryImpl(api).deleteStory("story-1")

                assertEquals(DomainResult.Success(Unit), result)
                assertEquals("story-1", api.deletedId)
            }
        }

    @Test
    fun `제출본 삭제는 204만 성공이며 승인 경합과 누락은 실패다`() =
        runTest {
            for (code in listOf(204, 404, 409, 500)) {
                val response =
                    if (code == 204) {
                        Response.success<Unit>(204, null)
                    } else {
                        Response.error<Unit>(code, "".toResponseBody())
                    }
                val api = DeleteApi(response)
                val result = StudioRepositoryImpl(api).deleteSubmission("submission-1")
                assertEquals("submission-1", api.deletedId)
                assertEquals(code == 204, result is DomainResult.Success)
            }
        }

    @Test
    fun `서버 오류를 삭제 성공으로 바꾸지 않는다`() =
        runTest {
            val api = DeleteApi(Response.error(500, "".toResponseBody()))
            val result = StudioRepositoryImpl(api).deleteStory("story-1")

            assertTrue(result is DomainResult.Failure)
            assertEquals(500, ((result as DomainResult.Failure).error as DomainError.Server).status)
        }
}

private class DeleteApi(
    private val response: Response<Unit>,
) : StudioApi {
    var deletedId: String? = null

    override suspend fun submissions(): Response<List<StorySubmissionDto>> = error("Not used by deletion")

    override suspend fun deleteSubmission(submissionId: String): Response<Unit> {
        deletedId = submissionId
        return response
    }

    override suspend fun myStories(): Response<List<StorySummaryDto>> = error("Not used by deletion")

    override suspend fun deleteStory(storyId: String): Response<Unit> {
        deletedId = storyId
        return response
    }
}
