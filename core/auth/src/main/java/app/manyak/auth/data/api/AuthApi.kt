package app.manyak.auth.data.api

import app.manyak.auth.data.api.dto.LogoutRequestDto
import app.manyak.auth.data.api.dto.RefreshTokenRequestDto
import app.manyak.auth.data.api.dto.SignupConsentRequestDto
import app.manyak.auth.data.api.dto.SocialAuthResponseDto
import app.manyak.auth.data.api.dto.SocialLoginRequestDto
import app.manyak.auth.data.api.dto.TokenResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 인증 경로. **access 토큰을 붙이지 않는 클라이언트**로 호출한다 — 재발급 요청이 다시 재발급 경로를
 * 타면 무한 재귀가 되기 때문이다.
 */
interface AuthApi {
    /** 필수 동의가 남았으면 계정과 토큰 대신 가입 대기 코드를 준다. */
    @POST("auth/social/{provider}")
    suspend fun startSocial(
        @Path("provider") provider: String,
        @Body request: SocialLoginRequestDto,
    ): Response<SocialAuthResponseDto>

    /** 400 은 대기 코드를 소비하지 않고, 401 은 코드가 없거나 만료·소비됐다는 뜻이다. */
    @POST("auth/social/complete")
    suspend fun completeSocial(
        @Header(HEADER_CONSENT_TOKEN) consentToken: String,
        @Body request: SignupConsentRequestDto,
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

private const val HEADER_CONSENT_TOKEN = "X-Manyak-Consent-Token"
