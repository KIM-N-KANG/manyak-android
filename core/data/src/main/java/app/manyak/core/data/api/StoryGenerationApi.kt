package app.manyak.core.data.api

import app.manyak.core.data.api.dto.StorylineGenerationRequestDto
import app.manyak.core.data.api.dto.StorylineGenerationResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * AI 를 동기 호출하는 생성 계열 경로. 생성된 진행이 회원에게 귀속되도록 토큰을 붙이고,
 * 응답까지 수십 초가 걸릴 수 있어 읽기 시간 상한을 늘린 클라이언트를 쓴다.
 */
interface StoryGenerationApi {
    @POST("stories/simple/storylines")
    suspend fun generateStorylines(
        @Body body: StorylineGenerationRequestDto,
    ): Response<StorylineGenerationResponseDto>
}
