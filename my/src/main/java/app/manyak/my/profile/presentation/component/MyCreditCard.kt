package app.manyak.my.profile.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.manyak.common.entity.user.UserProfile
import app.manyak.designsystem.theme.ManyakTheme
import java.text.NumberFormat
import app.manyak.my.R as MyR

/**
 * 마이의 이프 카드. 잔액과 이프 충전으로 가는 입구만 둔다 —
 * 이프를 얻는 수단(출석·초대)은 모두 이프 충전 화면이 소유한다.
 */
@Composable
internal fun CreditBalanceCard(
    profile: UserProfile?,
    onOpenCharge: () -> Unit,
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
                text = stringResource(MyR.string.my_credit_label),
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
        Button(
            modifier = Modifier.heightIn(min = ManyakTheme.sizes.input),
            onClick = onOpenCharge,
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
                ),
        ) {
            Text(text = stringResource(MyR.string.my_credit_charge), style = ManyakTheme.typography.labelLarge)
        }
    }
}
