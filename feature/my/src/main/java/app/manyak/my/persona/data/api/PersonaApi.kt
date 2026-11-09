package app.manyak.my.persona.data.api

import app.manyak.my.persona.data.dto.PersonaRequestDto
import app.manyak.my.persona.data.dto.PersonaResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/** 회원 페르소나 조회, 생성, 수정, 삭제. */
interface PersonaApi {
    @GET("users/me/personas")
    suspend fun personas(): Response<List<PersonaResponseDto>>

    @POST("users/me/personas")
    suspend fun create(
        @Body body: PersonaRequestDto,
    ): Response<PersonaResponseDto>

    @PATCH("users/me/personas/{personaId}")
    suspend fun update(
        @Path("personaId") personaId: String,
        @Body body: PersonaRequestDto,
    ): Response<PersonaResponseDto>

    @DELETE("users/me/personas/{personaId}")
    suspend fun delete(
        @Path("personaId") personaId: String,
    ): Response<Unit>
}
