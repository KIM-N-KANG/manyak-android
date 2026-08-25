package app.manyak.root

import org.junit.Assert.assertEquals
import org.junit.Test

class FunnelSceneTransitionTest {
    @Test
    fun `홈에서 퍼널로 들어가면 올라온다`() {
        assertEquals(
            FunnelTransitionDirection.ENTER,
            funnelTransitionDirection(fromFunnel = false, toFunnel = true),
        )
    }

    @Test
    fun `퍼널에서 나가면 내려간다`() {
        assertEquals(
            FunnelTransitionDirection.EXIT,
            funnelTransitionDirection(fromFunnel = true, toFunnel = false),
        )
    }

    @Test
    fun `퍼널 단계 사이 이동은 수직 전환이 아니다`() {
        assertEquals(
            FunnelTransitionDirection.NONE,
            funnelTransitionDirection(fromFunnel = true, toFunnel = true),
        )
    }

    @Test
    fun `퍼널 밖끼리의 이동은 수직 전환이 아니다`() {
        assertEquals(
            FunnelTransitionDirection.NONE,
            funnelTransitionDirection(fromFunnel = false, toFunnel = false),
        )
    }
}
