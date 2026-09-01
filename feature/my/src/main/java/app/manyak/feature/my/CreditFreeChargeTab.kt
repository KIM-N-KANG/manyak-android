package app.manyak.feature.my

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakProgressIndicator
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 무료 충전 탭. 이프를 결제 없이 얻는 두 수단(출석·친구 초대)만 모은다.
 *
 * 초대 줄은 마이 메뉴와 같은 전체 너비 행이라 가로 여백은 출석 상자 쪽에만 준다.
 */
@Composable
internal fun CreditFreeChargeTab(
    state: CreditChargeUiState,
    onIntent: (CreditChargeIntent) -> Unit,
    onOpenInvite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = ManyakTheme.spacing.gutter,
                    bottom = ManyakTheme.spacing.screenBottom,
                ),
    ) {
        AttendanceCard(state = state, onIntent = onIntent)
        Spacer(Modifier.height(ManyakTheme.spacing.block))
        InviteMenuItem(onClick = onOpenInvite)
    }
}

@Composable
private fun AttendanceCard(
    state: CreditChargeUiState,
    onIntent: (CreditChargeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter)
                .background(ManyakTheme.colors.backgroundNeutral, ManyakTheme.shapes.card)
                .padding(ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        Text(
            text = stringResource(R.string.my_attendance_headline),
            style = ManyakTheme.typography.titleMediumStrong,
            color = ManyakTheme.colors.text,
        )
        Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter)) {
            AttendanceButton(state = state, onClick = { onIntent(CreditChargeIntent.ClaimAttendance) })
            AttendanceNotes()
        }
    }
}

@Composable
private fun AttendanceButton(
    state: CreditChargeUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val attendedToday = state.attendedToday == true
    Button(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ManyakTheme.sizes.control),
        onClick = onClick,
        enabled = state.canClaimAttendance,
        shape = ManyakTheme.shapes.control,
        contentPadding =
            PaddingValues(
                horizontal = ManyakTheme.spacing.controlHorizontal,
                vertical = ManyakTheme.spacing.controlVertical,
            ),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ManyakTheme.colors.brand,
                contentColor = ManyakTheme.colors.textInverse,
                disabledContainerColor = ManyakTheme.colors.backgroundDisabled,
                disabledContentColor = ManyakTheme.colors.textDisabled,
            ),
    ) {
        if (state.isClaimingAttendance) {
            ManyakProgressIndicator(
                modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
                color = ManyakTheme.colors.textDisabled,
            )
        } else {
            val labelRes = if (attendedToday) R.string.my_attendance_done else R.string.my_attendance_claim
            Text(text = stringResource(labelRes), style = ManyakTheme.typography.bodyLargeStrong)
        }
    }
}

/** 버튼 아래 안내. 초기화 주기를 먼저 알리고 사용 기한을 뒤에 덧붙인다. */
@Composable
private fun AttendanceNotes(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.hairline),
        horizontalAlignment = Alignment.End,
    ) {
        listOf(R.string.my_attendance_note_reset, R.string.my_attendance_note_expiry).forEach { noteRes ->
            Text(
                text = stringResource(noteRes),
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textSubtle,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Preview(showBackground = true, name = "이프 충전 · 무료 충전")
@Composable
private fun CreditFreeChargeTabPreview() {
    ManyakTheme(darkTheme = false) {
        CreditFreeChargeTab(
            state = CreditChargeUiState(balance = 3230, attendedToday = false, isLoading = false),
            onIntent = {},
            onOpenInvite = {},
        )
    }
}
