package app.manyak.common.entity.credit

/**
 * 출석 보상 요청의 결과. 오늘 이미 받았으면 서버가 [rewarded] false 로 200 을 준다(멱등).
 */
data class AttendanceResult(
    val rewarded: Boolean,
    /** 지급된 이프. [rewarded] 가 false 면 null 이다. */
    val amount: Long?,
)
