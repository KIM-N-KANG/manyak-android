package app.manyak.my.profile.data.api

import app.manyak.my.profile.data.dto.MeResponseDto
import app.manyak.my.profile.data.dto.ProfilePresetResponseDto
import app.manyak.my.profile.data.dto.UpdateProfileRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

interface ProfileApi {
    @GET("auth/me")
    suspend fun me(): Response<MeResponseDto>

    /** 응답은 `auth/me` 와 같은 모양이다. */
    @PATCH("users/me")
    suspend fun updateProfile(
        @Body body: UpdateProfileRequestDto,
    ): Response<MeResponseDto>

    @GET("profile-presets")
    suspend fun presets(): Response<List<ProfilePresetResponseDto>>
}
