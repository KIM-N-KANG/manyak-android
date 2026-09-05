package app.manyak.core.data.api

import app.manyak.common.data.story.StorySummaryDto
import app.manyak.core.data.api.dto.AttendanceRewardResponseDto
import app.manyak.core.data.api.dto.CreditTransactionsResponseDto
import app.manyak.core.data.api.dto.InviteResponseDto
import app.manyak.core.data.api.dto.MeResponseDto
import app.manyak.core.data.api.dto.RedeemInviteCodeRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** 회원 본인 소유 자원의 보호 경로. access 토큰을 붙이는 클라이언트로 호출한다. */
interface UserApi {
    @GET("auth/me")
    suspend fun me(): Response<MeResponseDto>

    /** 내가 만든 스토리 목록. limit 을 생략해 서버 기본 상한(100건)을 그대로 쓴다. */
    @GET("users/me/stories")
    suspend fun myStories(): Response<List<StorySummaryDto>>

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

    /** 내 초대 코드와 이번 달 보상 진행. 월 상한은 정책 값이라 응답이 함께 싣는다. */
    @GET("users/me/invite")
    suspend fun myInvite(): Response<InviteResponseDto>

    /** 받은 초대 코드 등록. 계정당 1회이며 재시도·본인 코드는 409 로 구분된다. */
    @POST("users/me/invite/redeem")
    suspend fun redeemInviteCode(
        @Body request: RedeemInviteCodeRequestDto,
    ): Response<Unit>

    /** 내 스토리 소프트 삭제. 성공은 본문 없는 204 다. */
    @DELETE("stories/{storyId}")
    suspend fun deleteStory(
        @Path("storyId") storyId: String,
    ): Response<Unit>
}
