package app.manyak.my.profile.data.api

import app.manyak.my.profile.data.dto.MeResponseDto
import retrofit2.Response
import retrofit2.http.GET

interface ProfileApi {
    @GET("auth/me")
    suspend fun me(): Response<MeResponseDto>
}
