package app.manyak.my.invite.entity

/**
 * 내 초대 코드와 이번 달 보상 진행.
 *
 * 셋 다 없을 수 있다 — 서버가 코드를 아직 만들지 않았거나 보상 집계를 싣지 않은 응답이 온다.
 * 월 상한은 정책 수치라 화면이 갖지 않고 응답이 싣는 값을 그대로 쓴다.
 */
data class Invite(
    val code: String?,
    val monthlyRewardCount: Int?,
    val monthlyRewardLimit: Int?,
)
