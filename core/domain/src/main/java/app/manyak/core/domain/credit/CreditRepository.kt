package app.manyak.core.domain.credit

import app.manyak.core.domain.error.DomainResult

/** 이프 보상·사용. 잔액 자체는 프로필(`GET /auth/me`)이 정본이라 여기서 다시 들지 않는다. */
interface CreditRepository {
    /** 출석 보상을 요청한다. 성공 뒤 잔액 반영은 호출부가 프로필 갱신으로 잇는다. */
    suspend fun claimAttendance(): DomainResult<AttendanceResult>
}
