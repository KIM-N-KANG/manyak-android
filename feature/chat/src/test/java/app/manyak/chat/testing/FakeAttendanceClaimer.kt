package app.manyak.chat.testing

import app.manyak.common.domain.credit.AttendanceClaimer
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.credit.AttendanceResult

/** 부를 때마다 [result] 를 돌려준다. 기본은 보상 지급이다. */
internal class FakeAttendanceClaimer(
    var result: DomainResult<AttendanceResult> = DomainResult.Success(AttendanceResult(rewarded = true, amount = 250)),
) : AttendanceClaimer {
    var calls = 0
        private set

    override suspend fun claimAttendance(): DomainResult<AttendanceResult> {
        calls++
        return result
    }
}
