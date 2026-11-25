package app.manyak.designsystem.text

import org.junit.Assert.assertEquals
import org.junit.Test

class CompactCountTest {
    @Test
    fun `1,000 미만은 그대로 둔다`() {
        assertEquals("0", formatCompactCount(0))
        assertEquals("999", formatCompactCount(999))
    }

    @Test
    fun `소수 첫째 자리 아래는 반올림하지 않고 버린다`() {
        assertEquals("1K", formatCompactCount(1_000))
        assertEquals("1.2K", formatCompactCount(1_299))
        assertEquals("12.3K", formatCompactCount(12_345))
        assertEquals("999.9K", formatCompactCount(999_999))
        assertEquals("1.2M", formatCompactCount(1_299_999))
        assertEquals("2B", formatCompactCount(2_000_000_000))
    }
}
