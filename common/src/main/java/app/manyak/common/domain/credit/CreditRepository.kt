package app.manyak.common.domain.credit

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.credit.AttendanceResult
import app.manyak.common.entity.credit.CreditTransactionPage

/** 이프 보상·사용. 잔액 자체는 프로필(`GET /auth/me`)이 정본이라 여기서 다시 들지 않는다. */
interface CreditRepository {
    /** 출석 보상을 요청한다. 성공 뒤 잔액 반영은 호출부가 프로필 갱신으로 잇는다. */
    suspend fun claimAttendance(): DomainResult<AttendanceResult>

    /**
     * 이프 내역 한 페이지. 최신순이고 [cursor] 가 null 이면 첫 페이지다.
     *
     * 페이지 크기는 서버 기본값을 그대로 쓰고, 분류 필터는 화면에 없어 전체(`ALL`)만 받는다.
     */
    suspend fun getTransactions(cursor: String? = null): DomainResult<CreditTransactionPage>
}
