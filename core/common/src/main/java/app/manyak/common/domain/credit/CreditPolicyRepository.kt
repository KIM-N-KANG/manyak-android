package app.manyak.common.domain.credit

import app.manyak.common.entity.credit.CreditPolicy
import kotlinx.coroutines.flow.StateFlow

/**
 * 이프 수치의 출처. 인증이 필요 없는 공개 조회라 세션 상태와 무관하게 읽을 수 있다.
 *
 * 수치를 쓰는 화면이 세 기능 모듈에 흩어져 있어 조회 결과는 앱 수명 동안 한 곳에 둔다 —
 * 화면마다 따로 부르면 같은 값을 여러 번 읽는다.
 */
interface CreditPolicyRepository {
    /** 마지막으로 받아 둔 정책. 아직 받지 못했거나 조회에 실패했으면 null 이다. */
    val policy: StateFlow<CreditPolicy?>

    /** 정책을 다시 읽는다. 실패해도 들고 있던 값을 지우지 않는다 — 낡은 값이 빈 값보다 낫다. */
    suspend fun refresh()
}
