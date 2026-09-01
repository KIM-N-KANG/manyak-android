package app.manyak.core.ui.text

import org.junit.Assert.assertEquals
import org.junit.Test

class StoryTextSegmentsTest {
    @Test
    fun `이중 별표는 볼드로, 단일 별표는 강조로 나눈다`() {
        val segments = parseTextSegments("그는 **두 번째 시계공**이다. *믿을 수 없었다.*")

        assertEquals(
            listOf(
                TextSegment(text = "그는 "),
                TextSegment(text = "두 번째 시계공", bold = true),
                TextSegment(text = "이다. "),
                TextSegment(text = "믿을 수 없었다.", emphasis = true),
            ),
            segments,
        )
    }

    @Test
    fun `마크업이 없으면 전체를 한 조각으로 돌려준다`() {
        assertEquals(
            listOf(TextSegment(text = "평범한 한 문장")),
            parseTextSegments("평범한 한 문장"),
        )
    }

    @Test
    fun `짝이 맞지 않는 별표는 문자 그대로 남긴다`() {
        assertEquals(
            listOf(TextSegment(text = "별 *하나만 있는 문장")),
            parseTextSegments("별 *하나만 있는 문장"),
        )
    }

    @Test
    fun `줄바꿈을 가로지르는 마크업은 매칭하지 않는다`() {
        assertEquals(
            listOf(TextSegment(text = "첫 줄 *열림\n닫힘* 둘째 줄")),
            parseTextSegments("첫 줄 *열림\n닫힘* 둘째 줄"),
        )
    }
}
