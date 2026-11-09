package app.manyak.my.credit.data.api

import app.manyak.my.credit.data.dto.TrialsResponseDto
import retrofit2.Response
import retrofit2.http.GET

/** 회원의 무료 체험 사용량·한도 조회. */
interface TrialsApi {
    @GET("users/me/trials")
    suspend fun trials(): Response<TrialsResponseDto>
}
