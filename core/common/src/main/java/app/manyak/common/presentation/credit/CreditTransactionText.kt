package app.manyak.common.presentation.credit

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.manyak.common.entity.credit.CreditTransaction
import app.manyak.common.entity.credit.CreditTransactionReason
import app.manyak.common.entity.credit.CreditTransactionType
import java.text.NumberFormat
import app.manyak.common.R as CommonR

/** 부호와 색은 서버가 계산한 분류(`type`)만 보고 정한다. 금액 부호로 다시 판단하지 않는다. */
val CreditTransaction.isEarned: Boolean get() = type == CreditTransactionType.EARN

@Composable
fun CreditTransaction.reasonLabel(): String = stringResource(reason.labelRes())

/** 소모는 어느 스토리에 썼는지가 정보의 전부다. 제목이 없는 소모는 지워진 스토리다. */
@Composable
fun CreditTransaction.subtitle(): String? =
    when {
        title != null -> title
        type == CreditTransactionType.SPEND -> stringResource(CommonR.string.credit_history_deleted_story)
        else -> null
    }

/** 발생일과 만료일. 소멸 줄의 만료일은 발생일이 아니라 회수된 로트의 실제 만료일이다. */
@Composable
fun CreditTransaction.dateLine(): String? {
    val expires = expiresDate?.let { date -> stringResource(CommonR.string.credit_history_expires, date) }
    return listOfNotNull(createdDate, expires).joinToString(DATE_SEPARATOR).takeIf { line -> line.isNotEmpty() }
}

@Composable
fun CreditTransaction.amountText(): String {
    val amount = NumberFormat.getInstance().format(amount)
    val amountRes =
        if (isEarned) CommonR.string.credit_history_amount_earned else CommonR.string.credit_history_amount_spent
    return stringResource(amountRes, amount)
}

@StringRes
private fun CreditTransactionReason.labelRes(): Int =
    when (this) {
        CreditTransactionReason.SIGNUP_REWARD -> CommonR.string.credit_reason_signup
        CreditTransactionReason.ATTENDANCE_REWARD -> CommonR.string.credit_reason_attendance
        CreditTransactionReason.INVITE_REWARD -> CommonR.string.credit_reason_invite
        CreditTransactionReason.REFUND -> CommonR.string.credit_reason_refund
        CreditTransactionReason.STORY_CREATION -> CommonR.string.credit_reason_story_creation
        CreditTransactionReason.CHAT_TURN -> CommonR.string.credit_reason_chat_turn
        CreditTransactionReason.CHAT_IMAGE -> CommonR.string.credit_reason_chat_image
        CreditTransactionReason.PURCHASE -> CommonR.string.credit_reason_purchase
        CreditTransactionReason.PURCHASE_REVERSAL -> CommonR.string.credit_reason_purchase_reversal
        CreditTransactionReason.EXPIRE -> CommonR.string.credit_reason_expire
        CreditTransactionReason.UNKNOWN -> CommonR.string.credit_reason_unknown
    }

private const val DATE_SEPARATOR = " · "
