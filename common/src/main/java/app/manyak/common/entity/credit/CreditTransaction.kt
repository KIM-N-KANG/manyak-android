package app.manyak.common.entity.credit

/**
 * 이프 내역 한 줄.
 *
 * 사유의 한국어 라벨과 부호는 화면이 붙인다 — 서버는 원장 원문(`reason`)과 분류(`type`)만 준다.
 */
data class CreditTransaction(
    val type: CreditTransactionType,
    val reason: CreditTransactionReason,
    /** 절대값. 부호는 [type] 이 정하므로 원장 부호를 그대로 들고 있지 않는다. */
    val amount: Long,
    /** 대상 스토리 제목. 보상·소멸이거나 스토리가 지워졌으면 null 이다. */
    val title: String?,
    /** 획득은 만료 예정일, 소멸은 회수된 로트의 실제 만료일 `YYYY-MM-DD`. 소모는 null 이다. */
    val expiresDate: String?,
    /** 발생일 `YYYY-MM-DD`. 형식이 예상과 다르면 null 이라 화면이 그 줄을 그리지 않는다. */
    val createdDate: String?,
)

/** 서버가 계산해 내려주는 분류. 화면이 부호나 사유로 다시 나누지 않는다. */
enum class CreditTransactionType {
    EARN,
    SPEND,
    EXPIRE,
}

/**
 * 원장 사유. 서버가 값을 늘려도 목록 전체가 실패로 떨어지지 않도록 모르는 값은 [UNKNOWN] 이다.
 */
enum class CreditTransactionReason {
    SIGNUP_REWARD,
    ATTENDANCE_REWARD,
    INVITE_REWARD,
    REFUND,
    STORY_CREATION,
    CHAT_TURN,
    EXPIRE,
    UNKNOWN,
}

/**
 * 최신순 한 페이지. [nextCursor] 는 서버가 봉인한 불투명 문자열이라 그대로 되돌려 주기만 한다 —
 * 파싱하거나 조립하지 않는다. null 이면 마지막 페이지다.
 */
data class CreditTransactionPage(
    val items: List<CreditTransaction>,
    val nextCursor: String?,
)
