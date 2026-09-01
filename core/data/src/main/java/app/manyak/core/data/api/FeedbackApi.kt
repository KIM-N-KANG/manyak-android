package app.manyak.core.data.api

import app.manyak.core.data.api.dto.CreateFeedbackRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 피드백 등록. 회원 자원 경로는 아니지만 인증 클라이언트로 부른다 —
 * 토큰이 붙어야 서버가 보낸 사람을 함께 남긴다.
 */
interface FeedbackApi {
    /** 성공은 본문 없는 201 이다. */
    @POST("feedbacks")
    suspend fun createFeedback(
        @Body request: CreateFeedbackRequestDto,
    ): Response<Unit>
}
