package app.manyak.my.invite.data.api

import app.manyak.my.invite.data.dto.InviteResponseDto
import app.manyak.my.invite.data.dto.RedeemInviteCodeRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface InviteApi {
    /** 내 초대 코드와 이번 달 보상 진행. 월 상한은 정책 값이라 응답이 함께 싣는다. */
    @GET("users/me/invite")
    suspend fun myInvite(): Response<InviteResponseDto>

    /** 받은 초대 코드 등록. 계정당 1회이며 재시도·본인 코드는 409 로 구분된다. */
    @POST("users/me/invite/redeem")
    suspend fun redeemInviteCode(
        @Body request: RedeemInviteCodeRequestDto,
    ): Response<Unit>
}
