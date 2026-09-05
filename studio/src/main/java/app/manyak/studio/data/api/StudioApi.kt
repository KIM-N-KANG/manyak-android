package app.manyak.studio.data.api

import app.manyak.common.data.story.StorySummaryDto
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path

interface StudioApi {
    /** 내가 만든 스토리 목록. limit 을 생략해 서버 기본 상한(100건)을 그대로 쓴다. */
    @GET("users/me/stories")
    suspend fun myStories(): Response<List<StorySummaryDto>>

    /** 내 스토리 소프트 삭제. 성공은 본문 없는 204 다. */
    @DELETE("stories/{storyId}")
    suspend fun deleteStory(
        @Path("storyId") storyId: String,
    ): Response<Unit>
}
