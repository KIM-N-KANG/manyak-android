package app.manyak.core.data.provider

import android.app.Activity
import app.manyak.core.domain.auth.AuthProvider
import app.manyak.core.domain.error.DomainResult

/**
 * 제공자 SDK 인증만 담당한다. **서버 로그인은 호출하지 않는다**.
 *
 * 두 단계를 한 함수에 묶으면 계정 연동이 로그인 경로를 타고 세션이 연동 대상 계정으로 교체되는 사고가
 * 구조적으로 열린다. 연동은 이 어댑터의 [requestIdToken] 만 재사용하고 서버 호출은 따로 한다.
 */
interface SocialIdTokenProvider {
    val provider: AuthProvider

    /** OIDC `idToken` 을 얻는다. 사용자가 창을 닫으면 취소 오류를 돌려준다. */
    suspend fun requestIdToken(): DomainResult<String>

    /**
     * **방금 발급된** `idToken` 을 얻는다.
     *
     * 계정 연동 재인증은 서버가 `iat` 신선도(10분)를 요구하는데, 제공자 SDK 는 유효기간이 남은 토큰을
     * 캐시에서 그대로 돌려줄 수 있다. 로그인은 신선도를 요구하지 않으므로 [requestIdToken] 을 쓰고,
     * 연동만 이 경로를 쓴다 — 강제 재발급은 사용자에게 인증 절차를 한 번 더 보이게 하는 비용이 있다.
     */
    suspend fun requestFreshIdToken(): DomainResult<String>

    /** 로그아웃 정리 5단계. 실패의 의미는 제공자마다 다르다(각 구현의 문서 참고). */
    suspend fun clearLocalState(): ProviderCleanupResult
}

enum class ProviderCleanupResult {
    /** 로컬 인증 상태가 지워진 것을 확인했다. */
    CLEARED,

    /** 확인하지 못했다. 정리 대기로 남겨 다음 로그인 SDK 를 열기 전에 다시 시도한다. */
    RETRY_REQUIRED,
}

/**
 * 제공자 SDK 는 화면을 띄우므로 Activity 가 필요하다. 구현은 현재 화면을 아는 `:app` 이 소유한다.
 *
 * 도메인 계약(`SessionRepository.signIn`)에는 Activity 가 드러나지 않는다 — 순수 Kotlin 모듈이
 * 안드로이드 타입을 알면 안 되기 때문이다.
 */
interface ActivityProvider {
    fun currentActivity(): Activity?
}
