package app.manyak.home.presentation.component

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSectionStepTest {
    @Test
    fun `한 장씩 옮기고 처음과 끝을 넘지 않는다`() {
        assertEquals(1, nextCardIndex(from = 0, direction = 1, cardCount = 5))
        assertEquals(4, nextCardIndex(from = 4, direction = 1, cardCount = 5))
        assertEquals(0, nextCardIndex(from = 0, direction = -1, cardCount = 5))
        assertEquals(0, nextCardIndex(from = 0, direction = 1, cardCount = 0))
    }
}
