package app.manyak.notification.consent.domain

/**
 * 광고 알림 동의를 이 회원에게 언제 물을지 기억한다. 동의 자체의 정본은 서버이고 이 값은 "묻는 차례" 만 뜻한다.
 *
 * 한 번 거절하면 세 번째 재진입에 한 번 더 묻고, 두 번째 거절이나 허용 뒤에는 다시 묻지 않는다.
 * 사용자 귀속이라 세션 종료 정리 대상이다.
 */
interface MarketingConsentPromptRepository {
    /** 이번 진입에 물을 차례인지 판정한다. 호출 자체가 재진입 한 번으로 세어진다. */
    suspend fun claimPrompt(): Boolean

    suspend fun markDeclined()

    /** 허용했다. 서버가 정본이지만 매 진입마다 조회하지 않도록 더 묻지 않는 쪽으로 닫는다. */
    suspend fun markSettled()
}
