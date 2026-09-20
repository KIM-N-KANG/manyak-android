package app.manyak.legal.consent.data.api

import app.manyak.legal.consent.data.api.dto.UserConsentRequestDto
import app.manyak.legal.consent.data.api.dto.UserConsentResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ConsentApi {
    @GET("users/me/consents")
    suspend fun get(): Response<UserConsentResponseDto>

    @POST("users/me/consents")
    suspend fun record(
        @Body request: UserConsentRequestDto,
    ): Response<UserConsentResponseDto>
}
