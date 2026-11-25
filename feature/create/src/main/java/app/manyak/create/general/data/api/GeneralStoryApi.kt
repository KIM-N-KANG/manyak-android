package app.manyak.create.general.data.api

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface GeneralStoryApi {
    @POST("stories/general")
    suspend fun submit(
        @Body request: GeneralStoryRequestDto,
    ): Response<GeneralAcceptedDto>

    @GET("stories/submissions/{id}")
    suspend fun submission(
        @Path("id") submissionId: String,
    ): Response<GeneralSubmissionDto>

    @PUT("stories/submissions/{id}")
    suspend fun resubmit(
        @Path("id") submissionId: String,
        @Body request: GeneralStoryRequestDto,
    ): Response<GeneralAcceptedDto>

    @GET("stories/{storyId}/edit")
    suspend fun edit(
        @Path("storyId") storyId: String,
    ): Response<GeneralStoryEditDto>

    @PATCH("stories/{storyId}")
    suspend fun update(
        @Path("storyId") storyId: String,
        @Body request: GeneralStoryPatchDto,
    ): Response<JsonObject>

    @DELETE("stories/{storyId}/thumbnail")
    suspend fun deleteCover(
        @Path("storyId") storyId: String,
    ): Response<Unit>

    @POST("stories/images/presign")
    suspend fun presignDraft(
        @Body request: GeneralImagePresignRequestDto,
    ): Response<GeneralImagePresignDto>

    @POST("stories/{storyId}/images/presign")
    suspend fun presignStory(
        @Path("storyId") storyId: String,
        @Body request: GeneralImagePresignRequestDto,
    ): Response<GeneralImagePresignDto>
}
