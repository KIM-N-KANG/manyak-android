package app.manyak.core.data.api

import app.manyak.core.data.api.dto.MeResponseDto
import retrofit2.Response
import retrofit2.http.GET

/** 보호 경로. access 토큰을 붙이는 클라이언트로 호출한다. */
interface UserApi {
    @GET("auth/me")
    suspend fun me(): Response<MeResponseDto>
}
