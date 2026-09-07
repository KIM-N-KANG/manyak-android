package app.manyak.chat.data.api

import app.manyak.chat.data.api.dto.ChatSummaryDto
import retrofit2.Response
import retrofit2.http.GET

interface ChatUserApi {
    /**
     * 내 채팅 목록(최근 활동순). 스토리 목록과 같이 limit 을 생략해 서버 기본 상한(100건)을 쓴다 —
     * 커서도 오프셋도 없는 계약이라 더 받을 수단이 없고, 페이징은 서버 확장을 기다린다.
     */
    @GET("users/me/chats")
    suspend fun myChats(): Response<List<ChatSummaryDto>>
}
