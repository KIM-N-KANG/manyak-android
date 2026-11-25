package app.manyak.home.explore.presentation.component

import org.junit.Assert.assertEquals
import org.junit.Test

class NextPageSkeletonCountTest {
    @Test
    fun `마지막 줄이 찼으면 골격 두 줄을 잇는다`() {
        assertEquals(4, nextPageSkeletonCount(storyCount = 20))
        assertEquals(4, nextPageSkeletonCount(storyCount = 0))
    }

    @Test
    fun `마지막 줄이 한 칸만 찼으면 빈 칸부터 채워 두 줄을 온전히 잇는다`() {
        assertEquals(5, nextPageSkeletonCount(storyCount = 21))
        assertEquals(5, nextPageSkeletonCount(storyCount = 1))
    }
}
