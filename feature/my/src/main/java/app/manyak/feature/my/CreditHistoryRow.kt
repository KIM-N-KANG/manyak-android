package app.manyak.feature.my

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.manyak.core.domain.credit.CreditTransaction
import app.manyak.core.domain.credit.CreditTransactionReason
import app.manyak.core.domain.credit.CreditTransactionType
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakProgressIndicator
import app.manyak.core.ui.component.SkeletonPlaceholder
import app.manyak.core.ui.component.rememberSkeletonPulseAlpha
import app.manyak.core.ui.theme.ManyakTheme
import java.text.NumberFormat

/**
 * 내역 한 줄. 사유·대상·날짜가 왼쪽에, 금액이 오른쪽에 온다.
 *
 * 부호와 색은 서버가 계산한 분류(`type`)만 보고 정한다 — 금액 부호로 다시 판단하지 않는다.
 */
@Composable
internal fun CreditTransactionRow(
    transaction: CreditTransaction,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = ManyakTheme.spacing.compact),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        ) {
            Text(
                text = stringResource(transaction.reason.labelRes()),
                style = ManyakTheme.typography.bodyLargeStrong,
                color = ManyakTheme.colors.text,
            )
            transaction.subtitle()?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.textSubtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            transaction.dateLine()?.let { dateLine ->
                Text(
                    text = dateLine,
                    style = ManyakTheme.typography.bodySmall,
                    color = ManyakTheme.colors.textSubtlest,
                )
            }
        }
        CreditTransactionAmount(transaction = transaction)
    }
}

@Composable
private fun CreditTransactionAmount(
    transaction: CreditTransaction,
    modifier: Modifier = Modifier,
) {
    val amount = NumberFormat.getInstance().format(transaction.amount)
    val isEarned = transaction.type == CreditTransactionType.EARN
    val amountRes =
        if (isEarned) R.string.my_credit_history_amount_earned else R.string.my_credit_history_amount_spent

    Text(
        modifier = modifier,
        text = stringResource(amountRes, amount),
        style = ManyakTheme.typography.bodyLargeStrong.copy(fontFeatureSettings = "tnum"),
        color = if (isEarned) ManyakTheme.colors.textBrand else ManyakTheme.colors.text,
    )
}

/** 소모는 어느 스토리에 썼는지가 정보의 전부다. 제목이 없는 소모는 지워진 스토리다. */
@Composable
private fun CreditTransaction.subtitle(): String? =
    when {
        title != null -> title
        type == CreditTransactionType.SPEND -> stringResource(R.string.my_credit_history_deleted_story)
        else -> null
    }

/** 발생일과 만료일. 소멸 줄의 만료일은 발생일이 아니라 회수된 로트의 실제 만료일이다. */
@Composable
private fun CreditTransaction.dateLine(): String? {
    val expires = expiresDate?.let { date -> stringResource(R.string.my_credit_history_expires, date) }
    return listOfNotNull(createdDate, expires).joinToString(DATE_SEPARATOR).takeIf { line -> line.isNotEmpty() }
}

@StringRes
private fun CreditTransactionReason.labelRes(): Int =
    when (this) {
        CreditTransactionReason.SIGNUP_REWARD -> R.string.my_credit_reason_signup
        CreditTransactionReason.ATTENDANCE_REWARD -> R.string.my_credit_reason_attendance
        CreditTransactionReason.INVITE_REWARD -> R.string.my_credit_reason_invite
        CreditTransactionReason.REFUND -> R.string.my_credit_reason_refund
        CreditTransactionReason.STORY_CREATION -> R.string.my_credit_reason_story_creation
        CreditTransactionReason.CHAT_TURN -> R.string.my_credit_reason_chat_turn
        CreditTransactionReason.EXPIRE -> R.string.my_credit_reason_expire
        CreditTransactionReason.UNKNOWN -> R.string.my_credit_reason_unknown
    }

/**
 * 다음 페이지 자리. 받는 중에는 진행 표시, 실패하면 재시도 버튼이고 **이미 그린 목록은 그대로 둔다**.
 */
@Composable
internal fun CreditHistoryLoadMoreFooter(
    isLoading: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = ManyakTheme.spacing.component),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            ManyakProgressIndicator(modifier = Modifier.size(ManyakTheme.sizes.icon))
        } else {
            TextButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.common_retry),
                    style = ManyakTheme.typography.labelLarge,
                    color = ManyakTheme.colors.textBrand,
                )
            }
        }
    }
}

/** 조회 중 자리를 잡아 두는 골격. 내역 줄과 같은 구조라 목록이 도착해도 요소가 튀지 않는다. */
@Composable
internal fun CreditHistorySkeleton(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.my_credit_history_loading)
    val alpha = rememberSkeletonPulseAlpha()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                // 골격 줄 하나하나가 아니라 목록 전체가 "조회 중"이다.
                .semantics { contentDescription = description },
    ) {
        repeat(PLACEHOLDER_ROW_COUNT) { RowPlaceholder(alpha = alpha) }
    }
}

@Composable
private fun RowPlaceholder(
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    // 글줄 높이를 dp 로 박아 두면 시스템 글자 크기를 키웠을 때 골격만 제자리에 남는다.
    val density = LocalDensity.current
    val typography = ManyakTheme.typography
    val labelHeight = with(density) { typography.bodyLargeStrong.fontSize.toDp() }
    val metaHeight = with(density) { typography.bodySmall.fontSize.toDp() }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = ManyakTheme.spacing.compact),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        ) {
            SkeletonPlaceholder(
                modifier = Modifier.fillMaxWidth(LABEL_WIDTH_FRACTION).heightIn(min = labelHeight),
                alpha = alpha,
            )
            SkeletonPlaceholder(
                modifier = Modifier.width(DateWidth).height(metaHeight),
                alpha = alpha,
            )
        }
        SkeletonPlaceholder(modifier = Modifier.width(AmountWidth).height(labelHeight), alpha = alpha)
    }
}

private const val DATE_SEPARATOR = " · "

private const val PLACEHOLDER_ROW_COUNT = 8

private const val LABEL_WIDTH_FRACTION = 0.45f

private val DateWidth = 96.dp

private val AmountWidth = 48.dp
