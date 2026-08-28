package app.manyak.core.data.api

import app.manyak.core.data.api.dto.ChatSummaryDto
import app.manyak.core.data.api.dto.MeResponseDto
import app.manyak.core.data.api.dto.StorySummaryDto
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path

/** 회원 본인 소유 자원의 보호 경로. access 토큰을 붙이는 클라이언트로 호출한다. */
interface UserApi {
    @GET("auth/me")
    suspend fun me(): Response<MeResponseDto>

    /** 내가 만든 스토리 목록. limit 을 생략해 서버 기본 상한(100건)을 그대로 쓴다. */
    @GET("users/me/stories")
    suspend fun myStories(): Response<List<StorySummaryDto>>

    /**
     * 내 채팅 목록(최근 활동순). 스토리 목록과 같이 limit 을 생략해 서버 기본 상한(100건)을 쓴다 —
     * 커서도 오프셋도 없는 계약이라 더 받을 수단이 없고, 페이징은 서버 확장을 기다린다.
     */
    @GET("users/me/chats")
    suspend fun myChats(): Response<List<ChatSummaryDto>>

    /** 내 스토리 소프트 삭제. 성공은 본문 없는 204 다. */
    @DELETE("stories/{storyId}")
    suspend fun deleteStory(
        @Path("storyId") storyId: String,
    ): Response<Unit>
}
