package app.manyak.core.domain.session

import app.manyak.core.domain.auth.AuthProvider
import app.manyak.core.domain.error.DomainResult
import kotlinx.coroutines.flow.StateFlow

/**
 * 인증 세션의 도메인 계약. 화면과 ViewModel 은 토큰을 보지 않고 이 계약만 호출한다.
 *
 * 로그아웃은 화면이 사라져도 끝나야 하므로 앱 스코프에서 실행된다.
 * 구현이 그 수명을 책임지며, 호출자는 완료를 기다리지 않아도 된다.
 */
interface SessionRepository {
    /** 재구독자는 항상 최신값을 받는다. */
    val sessionState: StateFlow<SessionState>

    /**
     * 제공자 SDK 인증과 서버 로그인을 순서대로 수행한다.
     *
     * 두 단계를 하나로 묶는 것은 로그인 경로에서만이다 — 계정 연동은 같은 SDK 인증을 쓰되
     * 서버 로그인을 호출하지 않는다.
     */
    suspend fun signIn(provider: AuthProvider): DomainResult<SignInOutcome>

    /** 종료 절차를 시작한다. 이미 진행 중이면 그 작업에 합류하고 새로 만들지 않는다. */
    suspend fun signOut()

    /** [SessionState.SignedOut.notice]를 사용자가 확인했음을 알린다. */
    suspend fun acknowledgeSessionEndNotice()
}

/**
 * 로그인 성공 결과.
 *
 * [isNewUser]가 참이면 신규 가입 초대 코드 안내 대상이다.
 */
data class SignInOutcome(
    val isNewUser: Boolean,
)
