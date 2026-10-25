package app.manyak.common.domain.credit

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.credit.AttendanceResult

/** 출석 보상 요청. 이프 충전 화면 밖(이프 부족 시트)에서도 출석을 처리하므로 계약을 여기 둔다. */
interface AttendanceClaimer {
    /** 출석 보상을 요청한다. 성공 뒤 잔액 반영은 호출부가 프로필 갱신으로 잇는다. */
    suspend fun claimAttendance(): DomainResult<AttendanceResult>
}
