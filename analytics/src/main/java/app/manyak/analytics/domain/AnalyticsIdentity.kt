package app.manyak.analytics.domain

/**
 * 식별자 배선. `:app` 의 세션 조율자만 부른다.
 *
 * 앱 소유 `device_id` 를 SDK 에 주입하기 전까지는 어떤 이벤트도 내보내지 않는다 — SDK 가 스스로 만든
 * 값으로 첫 이벤트가 나가면 API 헤더의 `device_id` 와 갈라져 서버 로그와 잇지 못한다.
 */
interface AnalyticsIdentity {
    /** 앱이 만든 UUID. 첫 호출이 이벤트 발행을 연다. 로그아웃 재발급도 같은 함수로 넘긴다. */
    fun setDeviceId(deviceId: String)

    fun setUser(userId: String)

    fun clearUser()
}
