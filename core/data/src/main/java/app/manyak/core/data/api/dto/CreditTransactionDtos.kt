package app.manyak.core.data.api.dto

import app.manyak.common.data.time.toDisplayDate
import app.manyak.common.entity.credit.CreditTransaction
import app.manyak.common.entity.credit.CreditTransactionPage
import app.manyak.common.entity.credit.CreditTransactionReason
import app.manyak.common.entity.credit.CreditTransactionType
import kotlinx.serialization.Serializable
import kotlin.math.absoluteValue

/**
 * 이프 내역 한 페이지. `nextCursor` 가 없으면 마지막 페이지다.
 */
@Serializable
data class CreditTransactionsResponseDto(
    val items: List<CreditTransactionDto> = emptyList(),
    val nextCursor: String? = null,
)

/**
 * 원장 한 줄. 서버가 사유를 늘려도 목록 전체가 실패로 떨어지지 않게 모든 필드에 기본값을 둔다.
 */
@Serializable
data class CreditTransactionDto(
    val type: String? = null,
    val reason: String? = null,
    val amount: Long = 0,
    val title: String? = null,
    val expiresAt: String? = null,
    val createdAt: String? = null,
)

fun CreditTransactionsResponseDto.toDomain(): CreditTransactionPage =
    CreditTransactionPage(
        // 분류를 모르는 줄은 부호도 색도 정할 수 없어 아예 그리지 않는다.
        items = items.mapNotNull { item -> item.toDomain() },
        nextCursor = nextCursor?.takeIf { cursor -> cursor.isNotBlank() },
    )

private fun CreditTransactionDto.toDomain(): CreditTransaction? {
    val type = type?.toTransactionType() ?: return null
    return CreditTransaction(
        type = type,
        reason = reason.toTransactionReason(),
        amount = amount.absoluteValue,
        title = title?.takeIf { value -> value.isNotBlank() },
        expiresDate = expiresAt?.toDisplayDate(),
        createdDate = createdAt?.toDisplayDate(),
    )
}

private fun String.toTransactionType(): CreditTransactionType? =
    CreditTransactionType.entries.firstOrNull { value -> value.name == this }

private fun String?.toTransactionReason(): CreditTransactionReason =
    CreditTransactionReason.entries.firstOrNull { value -> value.name == this }
        ?: CreditTransactionReason.UNKNOWN
