package app.manyak.common.domain.credit

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.credit.CreditTransactionPage

/** 이프 내역 조회. 이프 충전 화면 밖(채팅 메뉴)에서도 내역을 보이므로 계약을 여기 둔다. */
interface CreditTransactionReader {
    /**
     * 이프 내역 한 페이지. 최신순이고 [cursor] 가 null 이면 첫 페이지다.
     *
     * 페이지 크기는 서버 기본값을 그대로 쓰고, 분류 필터는 화면에 없어 전체(`ALL`)만 받는다.
     */
    suspend fun getTransactions(cursor: String? = null): DomainResult<CreditTransactionPage>
}
