package app.manyak.chat.room.presentation.message

import app.manyak.designsystem.component.isAllowedCharacterImageUrl
import app.manyak.designsystem.text.PassageSegment
import app.manyak.designsystem.text.parsePassageSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val IMAGE_URL = "https://cdn.manyak.app/characters/generated/watchmaker.png"

class PassageSegmentAppendTest {
    @Test
    fun `업로드 이미지가 확정 턴과 스트리밍에서 같은 이미지로 표시된다`() {
        listOf("cdn.manyak.app", "dev-cdn.manyak.app").forEach { host ->
            val url = "https://$host/characters/uploaded/watchmaker.webp"
            val expected = PassageSegment.CharacterImage("시계공", url)
            assertTrue(isAllowedCharacterImageUrl(url))
            assertEquals(expected, parsePassageSegments("[[$url]]\n\n시계공: 오셨군요.").first())
            assertEquals(listOf(expected), emptyList<PassageSegment>().appendCharacterImage("시계공", url))
            assertFalse(isAllowedCharacterImageUrl("https://$host/characters/uploaded/"))
        }
        assertFalse(isAllowedCharacterImageUrl("https://evil.example/characters/uploaded/a.webp"))
    }

    @Test
    fun `토큰은 마지막 텍스트 조각에 이어 붙는다`() {
        // 조각을 새로 만들면 강조 마커가 조각 경계에서 끊겨 파싱되지 않는다.
        val segments = emptyList<PassageSegment>().appendText("문이 ").appendText("열린다")

        assertEquals(listOf(PassageSegment.Text("문이 열린다")), segments)
    }

    @Test
    fun `이미지 앞 텍스트 끝의 줄바꿈을 지운다`() {
        val segments =
            emptyList<PassageSegment>()
                .appendText("문이 열린다\n")
                .appendCharacterImage(name = "시계공", imageUrl = IMAGE_URL)

        assertEquals(
            listOf(
                PassageSegment.Text("문이 열린다"),
                PassageSegment.CharacterImage(name = "시계공", imageUrl = IMAGE_URL),
            ),
            segments,
        )
    }

    @Test
    fun `문단 사이 빈 줄 뒤의 이미지는 저장 본문과 같은 조각이 된다`() {
        // 대사 앞 이미지는 빈 줄 뒤에 온다. 줄바꿈이 하나라도 남으면 이미지 위에 빈 줄이 한 줄 더 그려진다.
        val streamed =
            emptyList<PassageSegment>()
                .appendText("문이 열린다.\n\n")
                .appendCharacterImage(name = "시계공", imageUrl = IMAGE_URL)
                .appendText("시계공: 오셨군요.")

        assertEquals(parsePassageSegments("문이 열린다.\n\n[[$IMAGE_URL]]\n\n시계공: 오셨군요."), streamed)
    }

    @Test
    fun `줄바꿈만 남은 조각은 통째로 버린다`() {
        val segments =
            emptyList<PassageSegment>()
                .appendText("\n\n")
                .appendCharacterImage(name = "시계공", imageUrl = IMAGE_URL)

        assertEquals(listOf(PassageSegment.CharacterImage(name = "시계공", imageUrl = IMAGE_URL)), segments)
    }

    @Test
    fun `이름이 없거나 허용하지 않는 주소면 이미지를 붙이지 않는다`() {
        val base = emptyList<PassageSegment>().appendText("문이 열린다")

        assertEquals(base, base.appendCharacterImage(name = "  ", imageUrl = IMAGE_URL))
        assertEquals(base, base.appendCharacterImage(name = "시계공", imageUrl = "https://evil.example/a.png"))
    }
}
