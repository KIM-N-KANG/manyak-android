package app.manyak.feature.my

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.manyak.core.domain.user.UserProfile
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakProgressIndicator
import app.manyak.core.ui.theme.ManyakTheme
import java.text.NumberFormat

@Composable
internal fun CreditBalanceCard(
    profile: UserProfile?,
    isClaiming: Boolean,
    onClaimAttendance: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter)
                .padding(bottom = ManyakTheme.spacing.gutter)
                .background(
                    color = ManyakTheme.colors.backgroundNeutral,
                    shape = ManyakTheme.shapes.card,
                ).padding(ManyakTheme.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        ) {
            Text(
                text = stringResource(R.string.my_credit_label),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textSubtle,
            )
            profile?.let {
                Text(
                    text = remember(it.creditBalance) { NumberFormat.getInstance().format(it.creditBalance) },
                    // 잔액 자릿수가 바뀌어도 흔들리지 않게 고정폭 숫자를 쓴다.
                    style = ManyakTheme.typography.titleMediumStrong.copy(fontFeatureSettings = "tnum"),
                    color = ManyakTheme.colors.text,
                )
            }
        }
        CreditHistoryButton(onClick = onOpenHistory)
        AttendanceButton(profile = profile, isClaiming = isClaiming, onClick = onClaimAttendance)
    }
}

@Composable
private fun CreditHistoryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier.heightIn(min = ManyakTheme.sizes.input),
        onClick = onClick,
        shape = ManyakTheme.shapes.control,
        border = BorderStroke(1.dp, ManyakTheme.colors.border),
        contentPadding =
            PaddingValues(
                horizontal = ManyakTheme.spacing.controlHorizontal,
                vertical = ManyakTheme.spacing.controlVertical,
            ),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ManyakTheme.colors.surfaceRaised,
                contentColor = ManyakTheme.colors.text,
            ),
    ) {
        Text(text = stringResource(R.string.my_credit_history), style = ManyakTheme.typography.labelLarge)
    }
}

@Composable
private fun AttendanceButton(
    profile: UserProfile?,
    isClaiming: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val attendedToday = profile?.attendedToday == true
    Button(
        modifier = modifier.heightIn(min = ManyakTheme.sizes.input),
        onClick = onClick,
        enabled = profile != null && !attendedToday && !isClaiming,
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
        if (isClaiming) {
            ManyakProgressIndicator(modifier = Modifier.size(ManyakTheme.sizes.iconSmall))
        } else {
            val labelRes = if (attendedToday) R.string.my_attendance_done else R.string.my_attendance_check
            Text(text = stringResource(labelRes), style = ManyakTheme.typography.labelLarge)
        }
    }
}
