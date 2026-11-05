package app.manyak.home.data.api

import app.manyak.home.data.dto.GenreCatalogDto
import app.manyak.home.data.dto.StoryHomeDto
import app.manyak.home.data.dto.StoryPageDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** 공개 목록과 홈은 서버가 인증을 요구하지 않으므로 토큰 없는 클라이언트를 쓴다. */
interface StoryApi {
    /** cursor 를 생략하면 첫 페이지다. 다음 페이지는 같은 filter·sort 에 응답의 nextCursor 를 싣는다. */
    @GET("stories")
    suspend fun publicStories(
        @Query("filter") filter: String,
        @Query("sort") sort: String,
        @Query("cursor") cursor: String?,
    ): Response<StoryPageDto>

    /** 홈 섹션 목록. 섹션과 카드 모두 서버가 정한 순서다. */
    @GET("stories/home")
    suspend fun home(): Response<StoryHomeDto>

    /**
     * 공개 스토리 검색. 관련도순이고 다음 페이지는 같은 [q] 에 응답의 nextCursor 를 싣는다. 탐색과 달리 본인 스토리도
     * 결과에 들어가 토큰이 필요 없다.
     */
    @GET("stories/search")
    suspend fun search(
        @Query("q") q: String,
        @Query("cursor") cursor: String?,
    ): Response<StoryPageDto>

    /** 제공 장르 목록. 검색 화면은 대표 장르만 쓴다. */
    @GET("stories/genres")
    suspend fun genres(): Response<GenreCatalogDto>
}

/**
 * 탐색 목록. 서버가 로그인한 회원 본인의 스토리를 빼므로 토큰을 싣는 클라이언트로 부른다.
 * cursor 를 생략하면 서버가 새로 섞은 순서의 첫 페이지이고, 다음 페이지는 받은 nextCursor 로 같은 순서를 잇는다.
 */
interface ExploreApi {
    @GET("stories/explore")
    suspend fun explore(
        @Query("cursor") cursor: String?,
    ): Response<StoryPageDto>
}
