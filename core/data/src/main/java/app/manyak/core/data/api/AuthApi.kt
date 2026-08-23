package app.manyak.core.data.api

import app.manyak.core.data.api.dto.LogoutRequestDto
import app.manyak.core.data.api.dto.RefreshTokenRequestDto
import app.manyak.core.data.api.dto.SocialLoginRequestDto
import app.manyak.core.data.api.dto.TokenResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 인증 경로. **access 토큰을 붙이지 않는 클라이언트**로 호출한다 — 재발급 요청이 다시 재발급 경로를
 * 타면 무한 재귀가 되기 때문이다.
 */
interface AuthApi {
    @POST("auth/login/{provider}")
    suspend fun login(
        @Path("provider") provider: String,
        @Body request: SocialLoginRequestDto,
    ): Response<TokenResponseDto>

    @POST("auth/token/refresh")
    suspend fun refresh(
        @Body request: RefreshTokenRequestDto,
    ): Response<TokenResponseDto>

    @POST("auth/logout")
    suspend fun logout(
        @Body request: LogoutRequestDto,
    ): Response<Unit>
}
