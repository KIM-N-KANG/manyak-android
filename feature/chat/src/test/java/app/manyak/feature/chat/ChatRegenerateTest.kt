package app.manyak.feature.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRegenerateTest {
    @Test
    fun `AI 출력이 있고 엔딩에 도달하지 않은 턴만 다시 만들 수 있다`() {
        assertTrue(canRegenerate(turn(aiOutput = "문이 열린다")))

        // 대체할 본문이 없으면 다시 만들 것도 없다.
        assertFalse(canRegenerate(turn(aiOutput = "")))
        assertFalse(canRegenerate(turn(aiOutput = "   ")))

        // 엔딩 도달 턴의 재생성은 서버가 409 로 거절한다 — 버튼을 두면 누를 수 있는 실패가 된다.
        assertFalse(canRegenerate(turn(aiOutput = "문이 열린다", reachedEnding = "멈춘 도시")))
    }

    private fun turn(
        aiOutput: String,
        reachedEnding: String? = null,
    ): ChatRoomTurn =
        ChatRoomTurn(
            id = 1,
            userInput = "문을 연다",
            aiOutput = aiOutput,
            reachedEnding = reachedEnding,
        )
}
