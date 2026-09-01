package app.manyak.core.data.api

import app.manyak.core.data.api.dto.CreditPolicyResponseDto
import retrofit2.Response
import retrofit2.http.GET

/** 이프 수치의 공개 조회. 인증이 필요 없어 토큰을 붙이지 않는 클라이언트로 호출한다. */
interface CreditPolicyApi {
    @GET("credits/policies")
    suspend fun creditPolicies(): Response<CreditPolicyResponseDto>
}
