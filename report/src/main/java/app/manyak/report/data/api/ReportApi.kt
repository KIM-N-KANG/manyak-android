package app.manyak.report.data.api

import app.manyak.report.data.api.dto.CreateStoryReportRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface ReportApi {
    /** 성공은 본문 없는 201 이다. 같은 회원의 같은 스토리 재신고도 서버가 성공으로 흡수한다. */
    @POST("stories/{storyId}/reports")
    suspend fun reportStory(
        @Path("storyId") storyId: String,
        @Body request: CreateStoryReportRequestDto,
    ): Response<Unit>
}
