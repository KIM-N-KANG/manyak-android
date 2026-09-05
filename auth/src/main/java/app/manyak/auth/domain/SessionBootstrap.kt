package app.manyak.auth.domain

import app.manyak.auth.entity.SessionRestoreResult

/** 앱 시작 시 저장된 세션을 읽어 공개 상태를 처음으로 확정한다. */
interface SessionBootstrap {
    /**
     * 저장된 세션을 판정한다.
     *
     * 정리가 필요하다는 판정만 하고 **직접 시작하지 않는다** — 종료 조정자는 여러 `:core:*` 를
     * 조합해야 해서 `:app` 이 소유하므로, 결과를 올려 그쪽이 절차를 시작하게 한다.
     */
    suspend fun restore(): SessionRestoreResult
}
