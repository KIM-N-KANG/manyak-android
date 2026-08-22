package app.manyak.core.data.session

import app.manyak.core.domain.session.SessionEndNotice

/**
 * 세션 종료 절차의 실행자. 구현은 여러 `:core:*` 를 조합해야 하므로 composition root 인 `:app` 이 소유한다
 *
 * 사용자가 누른 로그아웃과 서버가 밀어낸 종료는 **같은 절차를 탄다** — 정리 범위가 달라지면 안 된다.
 */
interface SessionTerminator {
    /**
     * 앱 스코프에서 종료 절차를 시작한다. 이미 진행 중이면 그 작업에 합류하고 새로 만들지 않는다.
     *
     * @param serverLogoutToken 회전 직후 저장에 실패한 경우의 새 refresh 토큰. 구 토큰은 서버에서
     *  이미 폐기됐으므로 이 값으로 서버 로그아웃을 시도한다.
     */
    suspend fun terminate(
        notice: SessionEndNotice,
        serverLogoutToken: String? = null,
    )
}

/** 앱 시작 시 저장된 세션을 읽어 공개 상태를 처음으로 확정한다. */
interface SessionBootstrap {
    suspend fun restore()
}
