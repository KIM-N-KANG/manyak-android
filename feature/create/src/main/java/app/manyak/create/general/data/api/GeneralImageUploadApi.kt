package app.manyak.create.general.data.api

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.PUT
import retrofit2.http.Url

interface GeneralImageUploadApi {
    @PUT
    suspend fun upload(
        @Url url: String,
        @Body body: RequestBody,
    ): Response<Unit>
}
