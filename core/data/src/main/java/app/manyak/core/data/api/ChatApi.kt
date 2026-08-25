package app.manyak.core.data.api

import app.manyak.core.data.api.dto.ChatCreateRequestDto
import app.manyak.core.data.api.dto.ChatCreateResponseDto
import app.manyak.core.data.api.dto.ChatDetailResponseDto
import retrofit2.Response
import retrofit2.http.Body
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
}
