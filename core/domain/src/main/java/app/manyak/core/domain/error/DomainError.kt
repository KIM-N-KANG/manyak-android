package app.manyak.core.domain.error

import app.manyak.core.domain.auth.AuthProvider

/**
 * 계층을 지나는 오류의 타입. 문구를 모른다 — 사용자에게 보일 문자열로 바꾸는 것은 `:core:ui`의 몫이고,
 * 무엇을 보여 줄지 결정하는 것은 화면의 ViewModel 이다.
 */
sealed interface DomainError {
    /** 연결 실패·타임아웃. 세션을 끝내지 않는다. */
    data object Network : DomainError

    /** 응답을 해석하지 못했다. 계약 불일치이므로 재시도로 낫지 않는다. */
    data object Serialization : DomainError

    /** 서버가 오류로 응답했다. [requestId]는 서버 로그 상관용이며 사용자에게 보이지 않는다. */
    data class Server(
        val status: Int,
        val code: String?,
        val requestId: String?,
    ) : DomainError

    /** 인증이 거절됐다(401). 세션 종료 경로로 이어진다. */
    data object Unauthorized : DomainError

    /** 정지된 계정이다. 일반 로그아웃과 구분되는 안내를 보여야 한다. */
    data object AccountSuspended : DomainError

    /** 사용자가 제공자 화면을 스스로 닫았다. 실패 안내를 띄우지 않고 폴백도 하지 않는다. */
    data object ProviderCancelled : DomainError

    /** 제공자 인증이 실패했다. */
    data class ProviderFailed(
        val provider: AuthProvider,
        val diagnostic: String?,
    ) : DomainError

    /** 빌드에 제공자 키가 주입되지 않았다. 빈 키로 SDK 를 호출하지 않기 위해 시작 전에 걸러낸다. */
    data class ProviderNotConfigured(
        val provider: AuthProvider,
    ) : DomainError

    /** 분류하지 못한 실패. */
    data object Unknown : DomainError
}
