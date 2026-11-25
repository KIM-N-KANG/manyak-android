package app.manyak.chat.room.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatAnchorPadTest {
    @Test
    fun `앵커부터 목록 끝까지가 한 화면에 모자란 만큼이 패드가 된다`() {
        // 보낸 직후: 사용자 밴드만 있어 거의 한 화면이 통째로 모자라다.
        assertEquals(800, requiredAnchorPad(viewportHeight = 1000, anchorOffset = 0, contentEndOffset = 200))

        // 조각이 붙어 콘텐츠가 자랄수록 줄어든다.
        assertEquals(300, requiredAnchorPad(viewportHeight = 1000, anchorOffset = 0, contentEndOffset = 700))

        // 한 화면을 넘어서면 자리를 만들 필요가 없다.
        assertEquals(0, requiredAnchorPad(viewportHeight = 1000, anchorOffset = 0, contentEndOffset = 1400))
    }

    @Test
    fun `패드 크기는 스크롤 위치와 무관하다`() {
        // 사용자가 스트리밍 중 스크롤을 옮겨도 앵커와 콘텐츠 끝이 함께 밀리므로 필요한 자리는 그대로다.
        val atTop = requiredAnchorPad(viewportHeight = 1000, anchorOffset = 0, contentEndOffset = 400)
        val scrolledDown = requiredAnchorPad(viewportHeight = 1000, anchorOffset = -250, contentEndOffset = 150)

        assertEquals(atTop, scrolledDown)
    }

    @Test
    fun `패드는 화면 안에 보이는 만큼만 남기고 회수한다`() {
        // 패드 위쪽 300px 이 화면에 걸쳐 있다 — 여기까지 줄이면 화면은 움직이지 않는다.
        assertEquals(300, anchorPadFloor(viewportEndOffset = 1000, padOffset = 700))

        // 화면 아래로 완전히 넘어갔으면 전부 회수해도 보이는 것이 없다.
        assertEquals(0, anchorPadFloor(viewportEndOffset = 1000, padOffset = 1200))

        // 경계에서 음수로 내려가지 않는다.
        assertEquals(0, anchorPadFloor(viewportEndOffset = 1000, padOffset = 1000))
    }
}
