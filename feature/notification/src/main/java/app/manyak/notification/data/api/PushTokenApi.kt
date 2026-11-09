package app.manyak.notification.data.api

import app.manyak.notification.data.api.dto.PushTokenDeleteRequestDto
import app.manyak.notification.data.api.dto.PushTokenRegisterRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.PUT

interface PushTokenApi {
    /** 성공은 본문 없는 204 다. 같은 토큰 재등록은 갱신이고, 다른 회원이 보내면 소유자가 옮겨간다. */
    @PUT("users/me/push-tokens")
    suspend fun register(
        @Body request: PushTokenRegisterRequestDto,
    ): Response<Unit>

    /** 토큰을 경로가 아니라 본문에 싣는다 — 경로에 실으면 요청 로그에 기기의 푸시 주소가 남는다. */
    @HTTP(method = "DELETE", path = "users/me/push-tokens", hasBody = true)
    suspend fun delete(
        @Body request: PushTokenDeleteRequestDto,
    ): Response<Unit>
}
