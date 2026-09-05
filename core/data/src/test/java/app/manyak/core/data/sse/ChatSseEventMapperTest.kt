package app.manyak.core.data.sse

import app.manyak.common.domain.error.DomainError
import app.manyak.common.entity.chat.ChatStreamEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 사건 매핑은 앱이 소유하는 유일한 SSE 규칙이라 웹과 갈리면 같은 스트림이 두 클라이언트에서 다르게
 * 읽힌다. 웹 구현과 같은 판정을 여기서 고정한다.
 */
class ChatSseEventMapperTest {
    @Test
    fun `started 는 그대로 사건이 된다`() {
        assertEquals(ChatStreamEvent.Started, chatStreamEventOf("started", ""))
    }

    @Test
    fun `토큰은 JSON 의 text 필드를 쓴다`() {
        assertEquals(
            ChatStreamEvent.Token("문이 열린다"),
            chatStreamEventOf("token", """{"text":"문이 열린다"}"""),
        )
    }

    @Test
    fun `JSON 이 아닌 토큰은 원문 전체를 본문으로 쓴다`() {
        // 서버가 평문으로 보내는 경우가 계약에 함께 있다.
        assertEquals(ChatStreamEvent.Token("문이 열린다"), chatStreamEventOf("token", "문이 열린다"))
    }

    @Test
    fun `JSON 객체인데 text 가 없으면 버린다`() {
        assertNull(chatStreamEventOf("token", """{"delta":"문이 열린다"}"""))
        assertNull(chatStreamEventOf("token", """{"text":null}"""))
    }

    @Test
    fun `JSON 으로 읽히지만 객체가 아니면 버린다`() {
        // 웹과 같은 판정이다. 여기서만 갈리면 숫자로만 이루어진 조각이 한쪽에서만 사라진다.
        assertNull(chatStreamEventOf("token", "123"))
    }

    @Test
    fun `인물 이미지는 이름과 URL 이 둘 다 있어야 한다`() {
        assertEquals(
            ChatStreamEvent.CharacterImage(name = "시계공", imageUrl = "https://cdn.example/a.png"),
            chatStreamEventOf("character_image", """{"name":"시계공","imageUrl":"https://cdn.example/a.png"}"""),
        )
        assertNull(chatStreamEventOf("character_image", """{"name":"시계공"}"""))
        assertNull(chatStreamEventOf("character_image", """{"name":"","imageUrl":"https://cdn.example/a.png"}"""))
        assertNull(chatStreamEventOf("character_image", "시계공"))
    }

    @Test
    fun `완료 사건은 서버가 준 확정 본문을 싣지 않는다`() {
        // 화면은 상세를 다시 읽어 교체하므로 여기서 받은 본문을 쓰지 않는다.
        assertEquals(ChatStreamEvent.Completed, chatStreamEventOf("completed", """{"aiOutput":"..."}"""))
    }

    @Test
    fun `오류 사건은 서버 문구를 싣고 상태 없는 실패로 올린다`() {
        assertEquals(
            ChatStreamEvent.Failed(DomainError.Unknown, "생성에 실패했어요"),
            chatStreamEventOf("error", """{"message":"생성에 실패했어요"}"""),
        )
        assertEquals(ChatStreamEvent.Failed(DomainError.Unknown, null), chatStreamEventOf("error", ""))
    }

    @Test
    fun `알 수 없는 이름은 버린다`() {
        // 서버가 사건을 하나 늘릴 때마다 앱이 멈추면 안 된다.
        assertNull(chatStreamEventOf("heartbeat", "{}"))
        assertNull(chatStreamEventOf(null, "{}"))
    }
}
