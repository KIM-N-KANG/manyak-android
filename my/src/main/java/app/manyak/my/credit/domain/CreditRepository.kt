package app.manyak.my.credit.domain

import app.manyak.common.domain.credit.AttendanceClaimer
import app.manyak.common.domain.error.DomainResult
import app.manyak.my.credit.entity.CreditTransactionPage

/** 이프 보상·사용. 잔액 자체는 프로필(`GET /auth/me`)이 정본이라 여기서 다시 들지 않는다. */
interface CreditRepository : AttendanceClaimer {
    /**
     * 이프 내역 한 페이지. 최신순이고 [cursor] 가 null 이면 첫 페이지다.
     *
     * 페이지 크기는 서버 기본값을 그대로 쓰고, 분류 필터는 화면에 없어 전체(`ALL`)만 받는다.
     */
    suspend fun getTransactions(cursor: String? = null): DomainResult<CreditTransactionPage>
}
