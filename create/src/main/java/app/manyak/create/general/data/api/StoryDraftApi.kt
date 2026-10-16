package app.manyak.create.general.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

interface StoryDraftApi {
    @GET("users/me/story-drafts")
    suspend fun list(): Response<List<StoryDraftCardDto>>

    @GET("users/me/story-drafts/{draftId}")
    suspend fun detail(
        @Path("draftId") draftId: String,
    ): Response<StoryDraftDetailDto>

    @PUT("users/me/story-drafts/{draftId}")
    suspend fun put(
        @Path("draftId") draftId: String,
        @Body request: PutStoryDraftDto,
    ): Response<StoryDraftSavedDto>

    @DELETE("users/me/story-drafts/{draftId}")
    suspend fun delete(
        @Path("draftId") draftId: String,
    ): Response<Unit>
}

/** 목록에는 생성 요청 항목도 섞여 와서 초안 판정에 쓰는 필드까지 모두 선택으로 받는다. */
@Serializable
data class StoryDraftCardDto(
    val kind: String? = null,
    val draftId: String? = null,
    val mode: String? = null,
    val title: String? = null,
    val oneLineIntro: String? = null,
    val thumbnailUrl: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class StoryDraftDetailDto(
    val draftId: String,
    val mode: String,
    val version: Int,
    val schemaVersion: Int,
    val payload: JsonObject,
    val imageUrls: Map<String, String> = emptyMap(),
)

/** 기본값은 직렬화되지 않으므로 모드와 단계도 매번 값으로 채운다. */
@Serializable
data class PutStoryDraftDto(
    val mode: String,
    val stage: String,
    val schemaVersion: Int,
    val payload: JsonObject,
    val baseVersion: Int?,
    val title: String?,
    val oneLineIntro: String?,
    val thumbnailObjectKey: String?,
)

@Serializable
data class StoryDraftSavedDto(
    val version: Int,
)

@Serializable
data class StoryDraftErrorDto(
    val code: String? = null,
    val details: List<StoryDraftErrorDetailDto> = emptyList(),
)

@Serializable
data class StoryDraftErrorDetailDto(
    val field: String? = null,
    val message: String? = null,
)
