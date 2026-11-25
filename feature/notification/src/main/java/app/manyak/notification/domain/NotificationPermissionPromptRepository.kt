package app.manyak.notification.domain

/** 알림 권한을 이미 물었는지 기억한다. 기기 귀속 값이라 로그아웃 정리 대상이 아니다. */
interface NotificationPermissionPromptRepository {
    suspend fun wasPrompted(): Boolean

    suspend fun markPrompted()
}
