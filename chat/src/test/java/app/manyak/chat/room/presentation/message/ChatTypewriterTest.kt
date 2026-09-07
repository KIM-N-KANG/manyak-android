package app.manyak.chat.room.presentation.message

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTypewriterTest {
    private val image = ChatMessageSegment.CharacterImage(name = "아란", imageUrl = "https://cdn.manyak.app/a.png")

    @Test
    fun `공개 단위는 글자 수에 이미지당 1을 더한 값이다`() {
        val segments = listOf(ChatMessageSegment.Text("가나다"), image, ChatMessageSegment.Text("라마"))

        assertEquals(6, segments.revealUnitCount())
        assertEquals(0, emptyList<ChatMessageSegment>().revealUnitCount())
    }

    @Test
    fun `텍스트 조각은 글자 단위로 잘려 공개된다`() {
        val segments = listOf(ChatMessageSegment.Text("가나다라"))

        assertEquals(listOf(ChatMessageSegment.Text("가나")), segments.takeRevealUnits(2))
        assertEquals(segments, segments.takeRevealUnits(4))
        assertEquals(emptyList<ChatMessageSegment>(), segments.takeRevealUnits(0))
    }

    @Test
    fun `이미지는 앞 텍스트가 모두 공개된 뒤에야 나타난다`() {
        val segments = listOf(ChatMessageSegment.Text("가나다"), image, ChatMessageSegment.Text("라"))

        // 텍스트 3자까지는 이미지가 없다.
        assertEquals(listOf(ChatMessageSegment.Text("가나다")), segments.takeRevealUnits(3))
        // 4번째 단위가 이미지다.
        assertEquals(listOf(ChatMessageSegment.Text("가나다"), image), segments.takeRevealUnits(4))
        assertEquals(segments, segments.takeRevealUnits(5))
    }

    @Test
    fun `총량을 넘어선 공개 수는 이전 스트림의 복원 값이라 0에서 다시 시작한다`() {
        // 목록이 같은 키의 항목 상태를 보존해, 첫 턴의 공개 수가 다음 전송 블록에 복원된다.
        assertEquals(0, effectiveRevealedUnits(revealedUnits = 850, totalUnits = 60))
        assertEquals(0, effectiveRevealedUnits(revealedUnits = 850, totalUnits = 0))

        // 같은 스트림 안(총량 이하)에서는 그대로 이어 간다 — 회전 복원이 이 경우다.
        assertEquals(40, effectiveRevealedUnits(revealedUnits = 40, totalUnits = 60))
        assertEquals(60, effectiveRevealedUnits(revealedUnits = 60, totalUnits = 60))
    }

    @Test
    fun `공개 걸음은 시정수 비율로 밀린 분량을 비우되 1자 아래로 내려가지 않는다`() {
        val frame = 16_666_667L
        val steadyTau = 800_000_000L

        // 조금 밀렸을 땐 한 글자씩 — 타자기 느낌의 하한이다.
        assertEquals(1, typewriterStep(1, frame, steadyTau))
        assertEquals(1, typewriterStep(40, frame, steadyTau))

        // 서버 청크(수십 자)가 쌓여도 진행 중 시정수에서는 프레임당 몇 자씩만 열려 이어져 보인다.
        assertEquals(2, typewriterStep(100, frame, steadyTau))

        // 같은 밀림도 비우기 시정수에서는 빠르게 소화된다 — 확정 교체 전 점프 방지.
        assertEquals(12, typewriterStep(100, frame, 130_000_000L))
    }
}
