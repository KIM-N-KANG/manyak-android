package app.manyak.common.domain.story

/**
 * 검수 결과를 화면에서 직접 확인하는 제출본. 화면이 이미 결과를 보여 주는 제출본의 검수 완료 알림은 띄우지 않는다.
 */
interface ReviewWatch {
    /** [submissionId] 의 결과를 보이는 화면이 기다리는 동안 [block] 을 실행한다. 끝나거나 취소되면 기다림을 푼다. */
    suspend fun <T> watching(
        submissionId: String,
        block: suspend () -> T,
    ): T

    /** 화면이 [submissionId] 의 검수 결과 [status] 를 보여 줬다. 뒤늦게 도착한 같은 결과 알림을 거를 때 쓴다. */
    fun shown(
        submissionId: String,
        status: String,
    )

    /** 이 검수 결과 알림을 띄우지 않을지. 화면이 기다리는 중이거나 같은 결과를 방금 보여 줬으면 참이다. */
    fun suppresses(
        submissionId: String,
        status: String?,
    ): Boolean
}
