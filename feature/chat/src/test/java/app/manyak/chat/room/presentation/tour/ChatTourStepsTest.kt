package app.manyak.chat.room.presentation.tour

import androidx.compose.ui.geometry.Rect
import app.manyak.chat.entity.ChatInputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTourStepsTest {
    @Test
    fun `첫 스텝만 입력 모드에 따라 갈린다`() {
        assertEquals(
            listOf("add-blocks", "settings", "random-send"),
            chatTourSteps(ChatInputMode.BLOCK).map(ChatTourStep::wire),
        )
        assertEquals(
            listOf("add-emphasis", "settings", "random-send"),
            chatTourSteps(ChatInputMode.PLAIN).map(ChatTourStep::wire),
        )
    }

    @Test
    fun `블럭 모드 첫 스텝은 두 추가 버튼을 함께 감싼다`() {
        val targets = ChatTourTargets()
        targets.update(ChatTourTarget.ADD_SITUATION, Rect(10f, 100f, 60f, 140f))
        targets.update(ChatTourTarget.ADD_DIALOGUE, Rect(70f, 102f, 120f, 142f))

        assertEquals(Rect(10f, 100f, 120f, 142f), targets.stepBounds(ChatTourStep.ADD_BLOCKS))
        assertNull(targets.stepBounds(ChatTourStep.SETTINGS))
    }

    @Test
    fun `대상이 없거나 화면 밖인 스텝은 건너뛰고 남은 스텝이 없으면 null 이다`() {
        val steps = chatTourSteps(ChatInputMode.BLOCK)
        val bounds =
            mapOf(
                ChatTourStep.ADD_BLOCKS to Rect(0f, 900f, 10f, 950f),
                ChatTourStep.RANDOM_SEND to Rect(0f, 700f, 10f, 740f),
            )

        // 첫 스텝은 화면 아래로 밀려났고 설정 버튼은 그려지지 않았다.
        assertEquals(2, nextChatTourStep(steps, from = 0, viewportHeight = 800f, bounds = bounds::get))
        assertNull(nextChatTourStep(steps, from = 3, viewportHeight = 800f, bounds = bounds::get))
        assertNull(nextChatTourStep(steps, from = 0, viewportHeight = 800f) { null })
    }

    @Test
    fun `아래 공간이 카드와 간격에 모자랄 때만 카드를 위에 둔다`() {
        val highlight = Rect(0f, 600f, 100f, 640f)

        assertTrue(isTourCardAbove(highlight, viewportHeight = 800f, cardHeight = 150f, gap = 12f))
        assertFalse(isTourCardAbove(highlight, viewportHeight = 1000f, cardHeight = 150f, gap = 12f))
    }

    @Test
    fun `카드는 하이라이트 중앙이 기본이고 가장자리에서는 그쪽 변에 맞춘다`() {
        val width = 400f
        val card = 288f

        assertEquals(56f, tourCardLeft(Rect(150f, 0f, 250f, 10f), card, width, margin = 16f))
        // 왼쪽 끝 버튼 — 하이라이트 왼쪽 변이 여백보다 바깥이라 그 변에 맞춘다.
        assertEquals(10f, tourCardLeft(Rect(10f, 0f, 50f, 10f), card, width, margin = 16f))
        // 오른쪽 끝 버튼 — 카드 오른쪽 변을 하이라이트 오른쪽 변에 맞춘다.
        assertEquals(395f - card, tourCardLeft(Rect(340f, 0f, 395f, 10f), card, width, margin = 16f))
    }
}
