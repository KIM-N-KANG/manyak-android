package app.manyak.core.data.api

import app.manyak.core.data.api.dto.ChatChoicesResponseDto
import app.manyak.core.data.api.dto.ChatCreateRequestDto
import app.manyak.core.data.api.dto.ChatCreateResponseDto
import app.manyak.core.data.api.dto.ChatDetailResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 채팅 생성·조회 경로. 채팅이 회원에게 귀속되도록 토큰을 붙인다.
 * AI 동기 호출이 없는 경로라 기본 타임아웃의 인증 클라이언트를 그대로 쓴다.
 */
interface ChatApi {
    @POST("chats")
    suspend fun createChat(
        @Body body: ChatCreateRequestDto,
    ): Response<ChatCreateResponseDto>

    @GET("chats/{chatId}")
    suspend fun chatDetail(
        @Path("chatId") chatId: String,
    ): Response<ChatDetailResponseDto>

    /**
     * 마지막 턴의 선택지를 생성해 저장한다. 이어쓰기와 달리 동기 JSON 이고 이프를 쓰지 않는다.
     * 이미 선택지가 있으면 AI 호출 없이 기존 값이 돌아온다.
     */
    @POST("chats/{chatId}/turns/{turnId}/choices")
    suspend fun generateChoices(
        @Path("chatId") chatId: String,
        @Path("turnId") turnId: Long,
    ): Response<ChatChoicesResponseDto>

    /** 소프트 삭제. 성공은 본문 없는 204 다. */
    @DELETE("chats/{chatId}")
    suspend fun deleteChat(
        @Path("chatId") chatId: String,
    ): Response<Unit>
}
