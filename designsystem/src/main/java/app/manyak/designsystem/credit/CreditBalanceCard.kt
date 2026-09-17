package app.manyak.designsystem.credit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import app.manyak.designsystem.theme.ManyakTheme
import java.text.NumberFormat
import app.manyak.designsystem.R as DesignsystemR

/**
 * 내 이프 카드. 잔액과 이프 충전으로 가는 입구만 둔다 — 이프를 얻는 수단(출석·초대)은 모두
 * 이프 충전 화면이 소유한다. 마이와 채팅 메뉴 시트가 같은 카드를 쓴다.
 *
 * @param balance 잔액. 아직 읽지 못했으면 null 이고 라벨만 남는다 — 임의의 수치를 보이지 않는다.
 */
@Composable
fun CreditBalanceCard(
    balance: Long?,
    onOpenCharge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = ManyakTheme.colors.backgroundNeutral, shape = ManyakTheme.shapes.card)
                .padding(ManyakTheme.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        // 라벨이 위, 잔액이 아래 — 이프 충전 화면의 잔액 상자와 같은 세로 배치다.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        ) {
            Text(
                text = stringResource(DesignsystemR.string.credit_balance_label),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textSubtle,
            )
            if (balance != null) {
                CreditAmountText(
                    amount = remember(balance) { NumberFormat.getInstance().format(balance) },
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
            Text(
                text = stringResource(DesignsystemR.string.credit_balance_charge),
                style = ManyakTheme.typography.labelLarge,
            )
        }
    }
}
