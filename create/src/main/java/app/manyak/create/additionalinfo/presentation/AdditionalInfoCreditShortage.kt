package app.manyak.create.additionalinfo.presentation

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import app.manyak.common.presentation.credit.AttendanceOutcome
import app.manyak.common.presentation.credit.CreditShortageUiState
import app.manyak.designsystem.credit.CreditShortageSheet
import app.manyak.designsystem.R as DesignsystemR

/** 완성 전 잔액 선검사가 연 이프 부족 시트. */
@Composable
internal fun AdditionalInfoCreditShortageSheet(
    sheet: CreditShortageUiState,
    onIntent: (CreateAdditionalInfoIntent) -> Unit,
) {
    CreditShortageSheet(
        attendanceVisible = sheet.canClaimAttendance,
        isClaimingAttendance = sheet.isClaimingAttendance,
        isCharging = sheet.isCharging,
        onCharge = { onIntent(CreateAdditionalInfoIntent.CreditShortageCharge) },
        onClaimAttendance = { onIntent(CreateAdditionalInfoIntent.CreditShortageAttendance) },
        onDismiss = { onIntent(CreateAdditionalInfoIntent.CreditShortageDismissed) },
    )
}

internal fun Context.showAttendanceToast(outcome: AttendanceOutcome) {
    val text =
        when (outcome) {
            is AttendanceOutcome.Rewarded ->
                getString(DesignsystemR.string.credit_shortage_attendance_rewarded, outcome.amount)
            AttendanceOutcome.AlreadyDone -> getString(DesignsystemR.string.credit_shortage_attendance_already)
            AttendanceOutcome.Failed -> getString(DesignsystemR.string.credit_shortage_attendance_failed)
        }
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
