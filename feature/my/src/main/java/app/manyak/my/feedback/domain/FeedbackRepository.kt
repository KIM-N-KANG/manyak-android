package app.manyak.my.feedback.domain

import app.manyak.common.domain.error.DomainResult

/** 사용자 피드백 전송. */
interface FeedbackRepository {
    /**
     * 피드백을 보낸다. 플랫폼·앱 버전은 화면 입력이 아니라 구현이 채운다.
     *
     * @param email 답변이 필요할 때만 받는 값이다. 비우면 익명 피드백으로 간다.
     */
    suspend fun submitFeedback(
        body: String,
        email: String?,
    ): DomainResult<Unit>
}
