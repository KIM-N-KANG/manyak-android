package app.manyak.designsystem.credit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.designsystem.component.ManyakBottomSheet
import app.manyak.designsystem.component.ManyakNeutralButton
import app.manyak.designsystem.component.ManyakProgressIndicator
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.designsystem.R as DesignsystemR

/**
 * 회원 이프 부족 안내. 채팅방과 간편 제작이 화면에 머문 채 이프가 모자란 것을 알았을 때 띄운다.
 *
 * @param attendanceVisible 오늘 출석하지 않았을 때만 참이다. 출석 여부를 모르면 출석 버튼을 두지 않는다.
 * @param isCharging 충전으로 떠나기 전에 입력을 보관하는 중. 충전 버튼에 진행 표시를 둔다.
 */
@Composable
fun CreditShortageSheet(
    attendanceVisible: Boolean,
    isClaimingAttendance: Boolean,
    isCharging: Boolean,
    onCharge: () -> Unit,
    onClaimAttendance: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val busy = isClaimingAttendance || isCharging
    ManyakBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        // 요청 중에는 닫히지 않는다. 결과를 못 본 채 시트만 사라진다.
        dismissEnabled = !busy,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
            Text(
                text = stringResource(DesignsystemR.string.credit_shortage_title),
                style = ManyakTheme.typography.titleLarge,
                color = ManyakTheme.colors.text,
            )
            Text(
                text = stringResource(DesignsystemR.string.credit_shortage_description),
                style = ManyakTheme.typography.bodyLarge,
                color = ManyakTheme.colors.textSubtle,
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        ) {
            ChargeButton(isCharging = isCharging, enabled = !busy, onClick = onCharge)
            if (attendanceVisible) {
                ManyakNeutralButton(
                    modifier = Modifier.fillMaxWidth(),
                    label =
                        stringResource(
                            if (isClaimingAttendance) {
                                DesignsystemR.string.credit_shortage_attendance_in_progress
                            } else {
                                DesignsystemR.string.credit_shortage_attendance
                            },
                        ),
                    onClick = onClaimAttendance,
                    enabled = !busy,
                )
            }
            // 다른 시트와 같이 주 동작 아래에 보조색 텍스트 닫기를 둔다.
            ManyakTextButton(
                modifier = Modifier.fillMaxWidth().heightIn(min = ManyakTheme.sizes.control),
                onClick = onDismiss,
                enabled = !busy,
            ) {
                Text(
                    text = stringResource(DesignsystemR.string.credit_shortage_close),
                    style = ManyakTheme.typography.labelLarge,
                    color = if (busy) ManyakTheme.colors.textDisabled else ManyakTheme.colors.textSubtle,
                )
            }
        }
    }
}

@Composable
private fun ChargeButton(
    isCharging: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // 진행 중에는 주 버튼 색을 유지하고, 출석 요청으로 잠긴 동안만 비활성 색을 쓴다.
    val colors = ManyakTheme.colors
    Button(
        modifier = Modifier.fillMaxWidth().heightIn(min = ManyakTheme.sizes.control),
        onClick = onClick,
        enabled = enabled,
        shape = ManyakTheme.shapes.control,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = colors.brand,
                contentColor = colors.textInverse,
                disabledContainerColor = if (isCharging) colors.brand else colors.backgroundDisabled,
                disabledContentColor = if (isCharging) colors.textInverse else colors.textDisabled,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 진행 중에도 라벨 자리를 유지해 버튼 폭이 스피너 폭으로 줄지 않게 한다.
            Text(
                modifier = Modifier.alpha(if (isCharging) 0f else 1f),
                text = stringResource(DesignsystemR.string.credit_shortage_charge),
                style = ManyakTheme.typography.labelLarge,
            )
            if (isCharging) {
                ManyakProgressIndicator(
                    modifier = Modifier.size(ManyakTheme.sizes.icon),
                    color = ManyakTheme.colors.textInverse,
                )
            }
        }
    }
}

@Preview(name = "이프 부족 · 미출석")
@Composable
private fun CreditShortageSheetPreview() {
    ManyakTheme(darkTheme = false) {
        CreditShortageSheet(
            attendanceVisible = true,
            isClaimingAttendance = false,
            isCharging = false,
            onCharge = {},
            onClaimAttendance = {},
            onDismiss = {},
        )
    }
}
