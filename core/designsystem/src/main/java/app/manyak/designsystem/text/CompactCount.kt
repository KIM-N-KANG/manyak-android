package app.manyak.designsystem.text

/**
 * 채팅·좋아요 수를 1K·1.2K·3.4M 처럼 축약한다. 1,000 미만은 그대로 둔다.
 * 반올림하면 실제보다 큰 수가 보이므로 소수 첫째 자리 아래는 버린다.
 */
fun formatCompactCount(count: Long): String {
    val value = count.coerceAtLeast(0)
    val (unit, suffix) =
        CompactUnits.firstOrNull { (unit, _) -> value >= unit } ?: return value.toString()
    val tenths = value / (unit / TENTHS)
    val whole = tenths / TENTHS
    val fraction = tenths % TENTHS
    return if (fraction == 0L) "$whole$suffix" else "$whole.$fraction$suffix"
}

private const val TENTHS = 10L

private val CompactUnits =
    listOf(
        1_000_000_000L to "B",
        1_000_000L to "M",
        1_000L to "K",
    )
