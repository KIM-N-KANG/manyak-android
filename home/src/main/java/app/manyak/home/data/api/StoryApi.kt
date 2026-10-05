package app.manyak.home.data.api

import app.manyak.home.data.dto.StoryPageDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** 공개 목록은 서버가 인증을 요구하지 않으므로 토큰 없는 클라이언트를 쓴다. */
interface StoryApi {
    /** cursor 를 생략하면 첫 페이지다. 다음 페이지는 같은 filter·sort 에 응답의 nextCursor 를 싣는다. */
    @GET("stories")
    suspend fun publicStories(
        @Query("filter") filter: String,
        @Query("sort") sort: String,
        @Query("cursor") cursor: String?,
    ): Response<StoryPageDto>
}
