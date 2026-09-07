package app.manyak.my.credit.data.api

import app.manyak.my.credit.data.dto.AttendanceRewardResponseDto
import app.manyak.my.credit.data.dto.CreditTransactionsResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface CreditApi {
    /** 출석 보상 지급. KST 자정 기준 1일 1회이며 오늘 이미 받았으면 rewarded=false 로 200 이다(멱등). */
    @POST("users/me/credits/attendance")
    suspend fun claimAttendance(): Response<AttendanceRewardResponseDto>

    /**
     * 이프 내역(원장) 최신순 한 페이지. cursor 를 생략하면 첫 페이지이고, 응답의 nextCursor 를
     * 그대로 실어 다음 페이지를 잇는다. limit·type 은 서버 기본값(50건·전체)을 쓴다.
     */
    @GET("users/me/credits/transactions")
    suspend fun creditTransactions(
        @Query("cursor") cursor: String?,
    ): Response<CreditTransactionsResponseDto>
}
