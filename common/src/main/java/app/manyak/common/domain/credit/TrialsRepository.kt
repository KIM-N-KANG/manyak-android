package app.manyak.common.domain.credit

import app.manyak.common.entity.credit.Trials
import kotlinx.coroutines.flow.StateFlow

/**
 * 무료 체험 잔여의 출처. 회원 귀속 값이라 로그인 뒤 읽고 로그아웃 정리에서 비운다.
 *
 * 비용을 보이는 화면이 chat·create 에 흩어져 있어 조회 결과는 앱 수명 동안 한 곳에 둔다.
 * 소모 시점(턴 완료·스토리 완성)에 다시 읽는다.
 */
interface TrialsRepository {
    /** 마지막으로 받아 둔 잔여. 아직 받지 못했거나 조회에 실패했으면 null 이다. */
    val trials: StateFlow<Trials?>

    /** 잔여를 다시 읽는다. 실패해도 들고 있던 값을 지우지 않는다. */
    suspend fun refresh()
}
