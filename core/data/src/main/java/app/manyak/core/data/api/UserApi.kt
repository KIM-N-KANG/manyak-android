package app.manyak.core.data.api

import app.manyak.core.data.api.dto.MeResponseDto
import app.manyak.core.data.api.dto.StorySummaryDto
import retrofit2.Response
import retrofit2.http.GET

/** 보호 경로. access 토큰을 붙이는 클라이언트로 호출한다. */
interface UserApi {
    @GET("auth/me")
    suspend fun me(): Response<MeResponseDto>

    /** 내가 만든 스토리 목록. limit 을 생략해 서버 기본 상한(100건)을 그대로 쓴다. */
    @GET("users/me/stories")
    suspend fun myStories(): Response<List<StorySummaryDto>>
}
