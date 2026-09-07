package app.manyak.chat.room.presentation.composer

import org.junit.Assert.assertEquals
import org.junit.Test

class EmphasisMarkersTest {
    @Test
    fun `고른 구간을 감싸고 선택을 마커 안쪽에 남긴다`() {
        val result = insertEmphasisMarkers("문이 열린다", selectionStart = 0, selectionEnd = 2)

        assertEquals("*문이* 열린다", result.text)
        assertEquals("문이", result.text.substring(result.selectionStart, result.selectionEnd))
    }

    @Test
    fun `고른 것이 없으면 커서 자리에 빈 마커를 넣는다`() {
        val result = insertEmphasisMarkers("문이 열린다", selectionStart = 3, selectionEnd = 3)

        assertEquals("문이 **열린다", result.text)
        assertEquals(4, result.selectionStart)
        assertEquals(4, result.selectionEnd)
    }

    @Test
    fun `범위를 벗어난 선택도 안전하게 다룬다`() {
        val result = insertEmphasisMarkers("문", selectionStart = 5, selectionEnd = 9)

        assertEquals("문**", result.text)
        assertEquals(2, result.selectionStart)
        assertEquals(2, result.selectionEnd)
    }
}
