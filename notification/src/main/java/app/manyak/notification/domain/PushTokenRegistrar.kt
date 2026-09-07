package app.manyak.notification.domain

/**
 * FCM 등록 토큰을 서버에 맡기는 앱 스코프 등록기.
 *
 * 앱은 토큰을 저장하지 않는다 — FCM SDK 가 설치본의 토큰을 들고 있으므로 필요할 때마다 읽는다.
 * 등록 계기는 세션이 회원으로 바뀔 때와 토큰이 갱신될 때 둘뿐이고, 실패해도 큐에 남기지 않는다.
 * 서버 등록이 멱등 upsert 라 같은 토큰을 다시 보내도 된다.
 */
interface PushTokenRegistrar {
    /** 세션 상태 관찰을 시작한다. Application 에서 한 번 부른다. */
    fun start()

    /** FCM 이 새 토큰을 발급했다. 회원이면 다시 등록한다. */
    fun onTokenRefreshed()

    /**
     * 사용자 로그아웃의 첫 단계. 새 등록을 막고 진행 중인 등록을 취소한 뒤 이 기기의 토큰 삭제를 한 번 시도한다.
     *
     * 세션 장벽 **앞**에서 불러야 한다 — 장벽 뒤에는 삭제 호출이 쓸 access 토큰을 재발급할 수 없다.
     * 실패해도 예외를 내지 않으며, 다음 세션 상태 발행에서 등록기가 다시 열린다.
     */
    suspend fun closeAndDeleteToken()
}
