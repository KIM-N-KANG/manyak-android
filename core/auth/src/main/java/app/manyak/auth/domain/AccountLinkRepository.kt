package app.manyak.auth.domain

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.auth.AuthProvider

/**
 * 로그인된 계정에 다른 제공자를 더한다. 연동 해제는 서버가 제공하지 않으므로 이 계약에도 없다.
 *
 * 실패 사유 구분은 [app.manyak.core.domain.error.DomainError.Server] 의 상태·코드가 싣는다 —
 * 이미 연동된 계정(409 `PROVIDER_ALREADY_LINKED`)과 다른 회원에게 붙은 계정
 * (409 `SOCIAL_ACCOUNT_LINKED_TO_OTHER_USER`)은 안내가 다르다.
 */
interface AccountLinkRepository {
    /**
     * [current] 로 재인증한 뒤 [target] 을 연동한다.
     *
     * 재인증은 서버 계약이라 뺄 수 없다 — 공용 기기에 남은 세션이 그대로 영구 로그인 수단을 얻는 것을
     * 막는다. 두 단계 사이의 링크 코드는 구현 안에만 머물고 이 계약에는 드러나지 않는다.
     */
    suspend fun link(
        current: AuthProvider,
        target: AuthProvider,
    ): DomainResult<Unit>
}
