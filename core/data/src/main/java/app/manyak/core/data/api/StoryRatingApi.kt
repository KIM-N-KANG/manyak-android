package app.manyak.core.data.api

import app.manyak.core.data.api.dto.StorylineRatingRequestDto
import app.manyak.core.data.api.dto.StorylineRatingResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.PUT
import retrofit2.http.Path

/** 스토리라인 평가 경로. 소유자 판정을 위해 토큰을 붙이며 일반 시간 상한을 쓴다. */
interface StoryRatingApi {
    @PUT("stories/simple/storylines/{storylineId}/rating")
    suspend fun setRating(
        @Path("storylineId") storylineId: Long,
        @Body body: StorylineRatingRequestDto,
    ): Response<StorylineRatingResponseDto>

    /** 평가가 없어도 204 로 성공하는 멱등 동작이다. */
    @DELETE("stories/simple/storylines/{storylineId}/rating")
    suspend fun deleteRating(
        @Path("storylineId") storylineId: Long,
    ): Response<Unit>
}
