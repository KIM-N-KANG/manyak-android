package app.manyak.common.presentation.credit

import org.junit.Assert.assertEquals
import org.junit.Test

class CreditAmountTest {
    @Test
    fun `수치가 있으면 한국어 천 단위 구분자를 넣는다`() {
        assertEquals("2,000", creditAmountText(2_000))
    }

    @Test
    fun `수치를 아직 모르면 자리표시 숫자를 돌려준다`() {
        assertEquals(CREDIT_AMOUNT_PLACEHOLDER, creditAmountText(null))
    }

    @Test
    fun `자리표시 숫자는 수치로 읽히지 않게 0 만 쓴다`() {
        assertEquals("000", CREDIT_AMOUNT_PLACEHOLDER)
    }
}
