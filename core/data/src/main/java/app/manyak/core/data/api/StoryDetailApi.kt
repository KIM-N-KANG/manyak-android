package app.manyak.core.data.api

import app.manyak.core.data.api.dto.StoryDetailResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * 스토리 상세. 오리지널 목록([StoryApi])과 달리 **인증 클라이언트**를 쓴다 — 내가 만든 스토리는
 * 기본 비공개라 익명 요청에 404 가 오고, 본 엔딩(`reachedEndings`)도 회원 집계라 토큰이 없으면
 * 빈 배열로 온다.
 *
 * 같은 인증 클라이언트를 쓰는 [UserApi] 에 얹지 않은 이유는 상세가 본인 소유 자원이 아니어서다.
 */
interface StoryDetailApi {
    @GET("stories/{storyId}")
    suspend fun storyDetail(
        @Path("storyId") storyId: String,
    ): Response<StoryDetailResponseDto>
}
