package app.manyak.auth.data.api

import app.manyak.auth.data.api.dto.LinkReauthRequestDto
import app.manyak.auth.data.api.dto.LinkReauthResponseDto
import app.manyak.auth.data.api.dto.SocialLoginRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/** 계정 연동. 로그인된 세션 위에서만 성립하므로 인증 클라이언트로 호출한다. */
interface AccountLinkApi {
    /** 이미 연동된 제공자로 계정 소유를 다시 증명하고 일회용 링크 코드를 받는다. */
    @POST("auth/links/reauth")
    suspend fun reauth(
        @Body request: LinkReauthRequestDto,
    ): Response<LinkReauthResponseDto>

    /**
     * 링크 코드는 URL 이 아니라 헤더로만 보낸다 — 서버가 모든 요청 URI 를 구조화 로그와 크래시
     * 리포트에 남기기 때문이다. 성공은 본문 없는 201 이다.
     */
    @POST("auth/links/{provider}")
    suspend fun link(
        @Path("provider") provider: String,
        @Header(HEADER_LINK_CODE) linkCode: String,
        @Body request: SocialLoginRequestDto,
    ): Response<Unit>
}

const val HEADER_LINK_CODE = "X-Manyak-Link-Code"
