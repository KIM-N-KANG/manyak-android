package app.manyak.feature.chat.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val IMAGE_URL = "https://cdn.manyak.app/characters/generated/watchmaker.png"

class ChatMessageSegmentsTest {
    @Test
    fun `마커가 없으면 본문 전체가 텍스트 한 조각이다`() {
        assertEquals(
            listOf(ChatMessageSegment.Text("문이 열린다.")),
            parseChatMessageSegments("문이 열린다."),
        )
        assertEquals(emptyList<ChatMessageSegment>(), parseChatMessageSegments(""))
    }

    @Test
    fun `세 조건을 만족하면 이미지로 바꾼다`() {
        val content = "문이 열린다.\n[[$IMAGE_URL]]\n\n시계공: 오셨군요."

        assertEquals(
            listOf(
                ChatMessageSegment.Text("문이 열린다."),
                ChatMessageSegment.CharacterImage(name = "시계공", imageUrl = IMAGE_URL),
                ChatMessageSegment.Text("시계공: 오셨군요."),
            ),
            parseChatMessageSegments(content),
        )
    }

    @Test
    fun `마커 줄에 다른 글자가 섞이면 평문으로 남는다`() {
        // 조용히 지우면 서버가 보낸 본문이 사라진 것을 아무도 알 수 없다.
        val content = "앞 [[$IMAGE_URL]]\n\n시계공: 오셨군요."

        assertEquals(listOf(ChatMessageSegment.Text(content)), parseChatMessageSegments(content))
    }

    @Test
    fun `마커 뒤에 빈 줄이 없으면 평문으로 남는다`() {
        val content = "[[$IMAGE_URL]]\n시계공: 오셨군요."

        assertEquals(listOf(ChatMessageSegment.Text(content)), parseChatMessageSegments(content))
    }

    @Test
    fun `빈 줄 다음이 이름 라벨이 아니면 평문으로 남는다`() {
        val content = "[[$IMAGE_URL]]\n\n오셨군요."

        assertEquals(listOf(ChatMessageSegment.Text(content)), parseChatMessageSegments(content))
    }

    @Test
    fun `허용하지 않는 주소는 평문으로 남는다`() {
        val content = "[[https://evil.example/characters/generated/a.png]]\n\n시계공: 오셨군요."

        assertEquals(listOf(ChatMessageSegment.Text(content)), parseChatMessageSegments(content))
    }

    @Test
    fun `CRLF 본문도 같게 읽는다`() {
        val content = "문이 열린다.\r\n[[$IMAGE_URL]]\r\n\r\n시계공: 오셨군요."

        assertEquals(
            listOf(
                ChatMessageSegment.Text("문이 열린다."),
                ChatMessageSegment.CharacterImage(name = "시계공", imageUrl = IMAGE_URL),
                ChatMessageSegment.Text("시계공: 오셨군요."),
            ),
            parseChatMessageSegments(content),
        )
    }

    @Test
    fun `운영과 개발 CDN 의 생성 인물 경로만 허용한다`() {
        assertTrue(isAllowedChatCharacterImageUrl(IMAGE_URL))
        assertTrue(isAllowedChatCharacterImageUrl("https://dev-cdn.manyak.app/characters/generated/a.png"))

        // 다른 호스트·평문·포트·자격 증명·다른 경로는 모두 막는다.
        assertFalse(isAllowedChatCharacterImageUrl("https://cdn.manyak.app.evil.example/characters/generated/a.png"))
        assertFalse(isAllowedChatCharacterImageUrl("http://cdn.manyak.app/characters/generated/a.png"))
        assertFalse(isAllowedChatCharacterImageUrl("https://cdn.manyak.app:8443/characters/generated/a.png"))
        assertFalse(isAllowedChatCharacterImageUrl("https://user:pw@cdn.manyak.app/characters/generated/a.png"))
        assertFalse(isAllowedChatCharacterImageUrl("https://cdn.manyak.app/covers/a.png"))
        assertFalse(isAllowedChatCharacterImageUrl("https://cdn.manyak.app/characters/generated/"))
        assertFalse(isAllowedChatCharacterImageUrl("주소가 아님"))
    }

    @Test
    fun `토큰은 마지막 텍스트 조각에 이어 붙는다`() {
        // 조각을 새로 만들면 강조 마커가 조각 경계에서 끊겨 파싱되지 않는다.
        val segments = emptyList<ChatMessageSegment>().appendText("문이 ").appendText("열린다")

        assertEquals(listOf(ChatMessageSegment.Text("문이 열린다")), segments)
    }

    @Test
    fun `이미지 앞 텍스트의 마지막 줄바꿈 하나를 지운다`() {
        val segments =
            emptyList<ChatMessageSegment>()
                .appendText("문이 열린다\n")
                .appendCharacterImage(name = "시계공", imageUrl = IMAGE_URL)

        assertEquals(
            listOf(
                ChatMessageSegment.Text("문이 열린다"),
                ChatMessageSegment.CharacterImage(name = "시계공", imageUrl = IMAGE_URL),
            ),
            segments,
        )
    }

    @Test
    fun `줄바꿈만 남은 조각은 통째로 버린다`() {
        val segments =
            emptyList<ChatMessageSegment>()
                .appendText("\n")
                .appendCharacterImage(name = "시계공", imageUrl = IMAGE_URL)

        assertEquals(listOf(ChatMessageSegment.CharacterImage(name = "시계공", imageUrl = IMAGE_URL)), segments)
    }

    @Test
    fun `이름이 없거나 허용하지 않는 주소면 이미지를 붙이지 않는다`() {
        val base = emptyList<ChatMessageSegment>().appendText("문이 열린다")

        assertEquals(base, base.appendCharacterImage(name = "  ", imageUrl = IMAGE_URL))
        assertEquals(base, base.appendCharacterImage(name = "시계공", imageUrl = "https://evil.example/a.png"))
    }
}
