package app.manyak.notice.data.api

import app.manyak.notice.data.api.dto.NoticeDetailDto
import app.manyak.notice.data.api.dto.NoticePageDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** 둘 다 인증이 필요 없는 공개 API 다. `limit` 은 서버 기본값(20)을 쓴다. */
interface NoticeApi {
    @GET("notices")
    suspend fun list(
        @Query("cursor") cursor: String?,
    ): Response<NoticePageDto>

    @GET("notices/{noticeId}")
    suspend fun detail(
        @Path("noticeId") noticeId: String,
    ): Response<NoticeDetailDto>
}
