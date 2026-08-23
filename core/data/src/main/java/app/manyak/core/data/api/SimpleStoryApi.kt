package app.manyak.core.data.api

import app.manyak.core.data.api.dto.SimpleStoryTagDto
import retrofit2.Response
import retrofit2.http.GET

/** 간편 제작. 태그 목록은 인증이 필요 없는 경로라 토큰 없는 클라이언트로 호출한다. */
interface SimpleStoryApi {
    @GET("stories/simple/tags")
    suspend fun tags(): Response<List<SimpleStoryTagDto>>
}
