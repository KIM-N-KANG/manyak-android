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
     * 진행 중인 로그인의 제공자. **프로세스 수명**이라 화면 회전이나 ViewModel 재생성에도 유지되고,
     * 프로세스가 재생성되면 사라진다.
     *
     * 외부 제공자 화면에 다녀오는 동안 프로세스가 죽으면 기다리던 continuation 도 함께 사라진다.
     * 진행 표시를 화면 상태로 들고 있으면 그 뒤 화면이 영구 로딩으로 남을 수 있어, 진행 여부의
     * 정본을 여기에 둔다.
     */
    val signInInProgress: StateFlow<AuthProvider?>

    /**
     * 제공자 SDK 인증과 서버 로그인을 순서대로 수행한다.
     *
     * 두 단계를 하나로 묶는 것은 로그인 경로에서만이다 — 계정 연동은 같은 SDK 인증을 쓰되
     * 서버 로그인을 호출하지 않는다.
     */
    suspend fun signIn(provider: AuthProvider): DomainResult<SignInOutcome>

    /** 종료 절차를 시작한다. 이미 진행 중이면 그 작업에 합류하고 새로 만들지 않는다. */
    suspend fun signOut()

    /**
     * 계정을 지우고 세션을 끝낸다.
     *
     * 서버 삭제가 성공한 뒤에만 종료 절차를 시작한다 — 먼저 지우면 삭제 요청에 붙일 토큰이 없다.
     * 종료 자체는 [signOut] 과 같은 흐름을 타므로 로컬 정리 범위가 경로마다 갈리지 않는다.
     */
    suspend fun withdraw(): DomainResult<Unit>

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
