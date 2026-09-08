package app.manyak.notification.settings.data.api

import app.manyak.notification.settings.data.api.dto.PushSettingsDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

interface PushSettingsApi {
    @GET("users/me/push-settings")
    suspend fun get(): Response<PushSettingsDto>

    /** 세 필드 전부 필수인 전체 교체다. 성공은 갱신 후 상태를 담은 200 이다. */
    @PUT("users/me/push-settings")
    suspend fun update(
        @Body request: PushSettingsDto,
    ): Response<PushSettingsDto>
}
