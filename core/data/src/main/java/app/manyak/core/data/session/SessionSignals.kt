package app.manyak.core.data.session

import app.manyak.core.domain.session.SessionEndNotice

/**
 * 세션을 끝내야 한다는 판정을 중앙 종료 흐름에 전달하는 통로.
 *
 * 사용자가 누른 로그아웃, 재발급 401·403, 토큰 손상, 정지 계정이 모두 이 신호를 쓴다 —
 * 정리 범위가 경로마다 달라지면 안 된다.
 *
 * 구현은 `:app` 의 세션 종료 조정자가 소유한다 — 여러 `:core:*` 를 함께 알아야 하기 때문이다.
 * 데이터 계층은 이동 명령을 내리지 않고 신호만 보낸다.
 *
 * **기다리지 않는 통로다.** 인증 작업 안에서 종료 완료를 기다리면 종료 장벽이 그 작업을 취소하고
 * 완료를 기다리면서 자기 자신을 기다리게 된다.
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
