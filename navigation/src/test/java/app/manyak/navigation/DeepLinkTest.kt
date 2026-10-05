package app.manyak.navigation

import app.manyak.core.navigation.DeepLink
import app.manyak.core.navigation.MyCreditChargeRoute
import app.manyak.core.navigation.StoryDetailRoute
import app.manyak.core.navigation.StudioRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {
    @Test
    fun `허용한 제작 경로만 제작 탭으로 해석한다`() {
        assertEquals(StudioRoute, DeepLink.routeOf("https://manyak.app/studio"))
        assertEquals(StudioRoute, DeepLink.routeOf("https://www.manyak.app/studio/?source=push"))
        assertNull(DeepLink.routeOf("https://manyak.app/studio/submissions/s1"))
        assertNull(DeepLink.routeOf("https://evil.example/studio"))
    }

    @Test
    fun `스토리 상세와 이프 충전 경로를 해석한다`() {
        assertEquals(StoryDetailRoute("s1"), DeepLink.routeOf("https://manyak.app/stories/s1"))
        assertEquals(StoryDetailRoute("s1"), DeepLink.routeOf("https://www.manyak.app/stories/s1/"))
        assertEquals(MyCreditChargeRoute, DeepLink.routeOf("https://manyak.app/my/credits?tab=free"))
        assertEquals(MyCreditChargeRoute, DeepLink.routeOf("https://manyak.app/my/credits"))
    }

    @Test
    fun `허용하지 않는 호스트와 스킴은 해석하지 않는다`() {
        assertNull(DeepLink.routeOf("https://evil.example/stories/s1"))
        assertNull(DeepLink.routeOf("https://manyak.app.evil.example/stories/s1"))
        assertNull(DeepLink.routeOf("http://manyak.app/stories/s1"))
        assertNull(DeepLink.routeOf("manyak.app/stories/s1"))
        assertNull(DeepLink.routeOf("not a url"))
    }

    @Test
    fun `모르는 경로는 해석하지 않는다`() {
        assertNull(DeepLink.routeOf("https://manyak.app/"))
        assertNull(DeepLink.routeOf("https://manyak.app/stories"))
        assertNull(DeepLink.routeOf("https://manyak.app/stories/s1/extra"))
        assertNull(DeepLink.routeOf("https://manyak.app/my"))
        assertNull(DeepLink.routeOf("https://manyak.app/chats/c1"))
    }
}
