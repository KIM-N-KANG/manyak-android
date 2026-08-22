package app.manyak.core.data.session

import app.manyak.core.domain.session.SessionEndNotice

/**
 * 데이터 계층이 "이 세션은 더 못 쓴다"고 판정했을 때 중앙 종료 흐름을 깨우는 통로.
 *
 * 구현은 `:app` 의 세션 종료 조정자가 소유한다 — 여러 `:core:*` 를 함께 알아야 하기 때문이다
 *. 데이터 계층은 이동 명령을 내리지 않고 신호만 보낸다.
 */
interface SessionEndSignal {
    /**
     * @param serverLogoutToken 회전에는 성공했지만 저장에 실패한 경우의 **새 refresh 토큰**.
     *  구 토큰은 서버에서 이미 폐기됐으므로 이 값으로 서버 로그아웃을 시도해야 한다.
     */
    fun onSessionInvalidated(
        notice: SessionEndNotice,
        serverLogoutToken: String?,
    )
}

/**
 * `BOOT_COUNT` 를 읽을 수 없는 기기에서 재발급 무한 루프를 막는 프로세스 한정 플래그.
 *
 * 프로세스가 다시 시작되면 사라지므로, 그런 기기는 시작 후 첫 보호 요청에서 한 번 더 선제 재발급한다.
 */
class ProcessAnchorState {
    @Volatile
    var isAnchorVerifiedInThisProcess: Boolean = false
        private set

    fun markVerified() {
        isAnchorVerifiedInThisProcess = true
    }

    fun reset() {
        isAnchorVerifiedInThisProcess = false
    }
}
