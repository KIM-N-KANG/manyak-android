package app.manyak.auth.domain

import app.manyak.auth.entity.PendingSignup
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.consent.ConsentItem
import kotlinx.coroutines.flow.StateFlow

/**
 * 로그인 중 필수 동의를 기다리는 가입. 대기는 프로세스 메모리에만 있어 재시작하면 사라진다.
 *
 * 로그인 화면 위 동의 시트만 이 계약을 쓴다. 이미 로그인한 세션의 재동의는 회원 동의 API로 받는다.
 */
interface SignupRepository {
    /** 동의를 기다리는 가입. 없으면 null 이다. */
    val pendingSignup: StateFlow<PendingSignup?>

    /**
     * 동의한 버전으로 가입을 완료하고 세션을 연다.
     *
     * 대기 코드가 만료됐거나 약관이 바뀌어 소셜 인증부터 다시 해야 하면 대기를 버리고 로그인 화면에 안내를 남긴다.
     * 실패 뒤에도 [pendingSignup] 이 남아 있을 때만 같은 시트에서 다시 제출할 수 있다.
     */
    suspend fun completeSignup(versions: Map<ConsentItem, String>): DomainResult<Unit>

    /** 동의하지 않고 나갔다. 서버의 대기 코드는 만료로 사라진다. */
    fun cancelSignup()
}
