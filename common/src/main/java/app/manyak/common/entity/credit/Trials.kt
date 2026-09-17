package app.manyak.common.entity.credit

/**
 * 무료 체험 항목 하나의 사용량. [limit] 이 null 이면 무제한이다.
 *
 * 남은 횟수를 앱이 세지 않는다 — 서버가 소모 시점마다 정본을 내려주므로 완료 뒤 다시 읽는다.
 */
data class TrialUsage(
    val used: Long,
    val limit: Long?,
) {
    /** 체험이 남아 이 항목이 무료인지. 무제한도 무료다. */
    val isFree: Boolean
        get() = limit == null || limit - used > 0
}

/** `GET /users/me/trials` 응답. 항목이 null 이면 서버가 그 항목을 내려주지 않은 것이다. */
data class Trials(
    val chatTurn: TrialUsage? = null,
    val chatImage: TrialUsage? = null,
    val storyCreation: TrialUsage? = null,
    val storylineGeneration: TrialUsage? = null,
)
