package app.manyak.auth.data.api

import retrofit2.Response
import retrofit2.http.DELETE

interface AccountApi {
    /** 회원 탈퇴. 성공은 본문 없는 204 다. */
    @DELETE("users/me")
    suspend fun withdraw(): Response<Unit>
}
