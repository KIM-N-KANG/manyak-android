package app.manyak.feature.studio

import org.junit.Assert.assertEquals
import org.junit.Test

class StoryGenreBadgesTest {
    @Test
    fun `+N 자리를 남길 수 있는 지점까지만 센다`() {
        // 100 + 4 + 100 = 204 는 들어가지만, 셋째는 +N 자리(4 + 40)까지 더하면 넘친다.
        val count =
            visibleBadgeCount(
                widths = listOf(100, 100, 100, 100),
                gap = 4,
                overflowWidth = 40,
                maxWidth = 260,
            )
        assertEquals(2, count)
    }

    @Test
    fun `하나도 못 들어가는 폭에서도 최소 1개는 보인다`() {
        val count =
            visibleBadgeCount(
                widths = listOf(100, 100),
                gap = 4,
                overflowWidth = 40,
                maxWidth = 80,
            )
        assertEquals(1, count)
    }

    @Test
    fun `마지막까지 +N 자리를 남기며 들어가면 전부 센다`() {
        // 접힘이 없는 경우는 호출부가 걸러내지만, 계산 자체도 초과분만 접는다.
        val count =
            visibleBadgeCount(
                widths = listOf(50, 50, 50),
                gap = 4,
                overflowWidth = 40,
                maxWidth = 1_000,
            )
        assertEquals(3, count)
    }
}
