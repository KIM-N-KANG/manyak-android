package app.manyak.create.general.presentation.form

import org.junit.Assert.assertEquals
import org.junit.Test

class GeneralInlineSliderMathTest {
    // 8px 에서 288px 까지 아홉 지점이면 간격이 35px 이다
    private val stops = InlineSliderMath.stops(1..9, startX = 8f, endX = 288f)

    @Test
    fun `반올림으로 겹친 값을 합치고 남은 지점을 고르게 편다`() {
        assertEquals((1..9).toList(), stops.map { it.value })
        assertEquals((0..8).map { 8f + it * 35f }, stops.map { it.x })
        assertEquals(
            listOf(0, 11, 22, 33, 44, 56, 67, 78, 89, 100),
            InlineSliderMath.stops(0..100, 0f, 90f).map { it.value },
        )
    }

    @Test
    fun `값과 위치는 이웃한 지점 사이를 같은 비율로 오간다`() {
        assertEquals(148f, InlineSliderMath.xOf(stops, 5), 0f)
        assertEquals(5, InlineSliderMath.valueAt(stops, 148f + 17f))
        assertEquals(6, InlineSliderMath.valueAt(stops, 148f + 18f))
        // 지점 사이에 값이 여럿이면 끄는 동안 중간 값도 낸다
        assertEquals(6, InlineSliderMath.valueAt(InlineSliderMath.stops(0..100, 0f, 90f), 5f))
    }

    @Test
    fun `가장 가까운 지점을 고르고 거리가 같으면 앞 지점을 고른다`() {
        assertEquals(5, InlineSliderMath.nearest(stops, 160f).value)
        assertEquals(5, InlineSliderMath.nearest(stops, 165.5f).value)
        assertEquals(9, InlineSliderMath.nearest(stops, 400f).value)
        assertEquals(1, InlineSliderMath.nearest(stops, -50f).value)
    }

    @Test
    fun `손잡이는 글자 구간 가장자리에서 서서히 갈라진다`() {
        val spans = listOf(20f..60f, 240f..280f)
        assertEquals(0f, InlineSliderMath.split(10f, 4f, 6f, spans), 0f)
        assertEquals(0.5f, InlineSliderMath.split(19f, 4f, 6f, spans), 1e-4f)
        assertEquals(1f, InlineSliderMath.split(40f, 4f, 6f, spans), 0f)
        assertEquals(0.5f, InlineSliderMath.split(57f, 4f, 6f, spans), 1e-4f)
        assertEquals(0f, InlineSliderMath.split(150f, 4f, 6f, spans), 0f)
        assertEquals(1f, InlineSliderMath.split(260f, 4f, 6f, spans), 0f)
    }

    @Test
    fun `글자와 겹치는 눈금만 숨긴다`() {
        val spans = listOf(20f..60f, 240f..280f)
        assertEquals(listOf(8f, 78f, 113f, 148f, 183f, 218f, 288f), InlineSliderMath.visibleTicks(stops, spans, 2f))
    }
}
