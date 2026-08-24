package app.manyak.core.data.api

import app.manyak.core.data.api.dto.SimpleStoryTagDto
import retrofit2.Response
import retrofit2.http.GET

/** 인증이 필요 없는 태그 경로이므로 토큰 없는 클라이언트를 사용한다. */
interface SimpleStoryApi {
    @GET("stories/simple/tags")
    suspend fun tags(): Response<List<SimpleStoryTagDto>>
}
