package app.manyak.core.data.api

import app.manyak.core.data.api.dto.CreationRequestStatusDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * 생성 요청 복구 조회. 소유 주체(회원) 판정이 있어 토큰을 붙이고, 상태 읽기라
 * 생성 계열과 달리 기본 시간 상한을 쓴다.
 */
interface CreationRequestApi {
    @GET("stories/simple/creation-requests/{requestId}")
    suspend fun creationRequest(
        @Path("requestId") requestId: String,
    ): Response<CreationRequestStatusDto>
}
