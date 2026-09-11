package app.manyak.notification.consent.domain

/**
 * 광고 알림 동의를 이 회원에게 이미 물었는지 기억한다. 동의 자체의 정본은 서버이고 이 값은 "묻는 차례가
 * 지났는가"만 뜻한다. 사용자 귀속이라 세션 종료 정리 대상이다.
 */
interface MarketingConsentPromptRepository {
    suspend fun wasPrompted(): Boolean

    suspend fun markPrompted()
}
