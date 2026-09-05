package app.manyak.home.data.api

import app.manyak.common.data.story.StorySummaryDto
import retrofit2.Response
import retrofit2.http.GET

/** 오리지널 목록은 서버가 인증을 요구하지 않으므로 토큰 없는 클라이언트를 쓴다. */
interface StoryApi {
    @GET("stories/originals")
    suspend fun originalStories(): Response<List<StorySummaryDto>>
}
