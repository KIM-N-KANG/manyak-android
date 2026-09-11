package app.manyak.navigation

import app.manyak.core.navigation.MainTabsRoute
import app.manyak.core.navigation.MyCreditChargeRoute
import app.manyak.core.navigation.PushEntry
import app.manyak.core.navigation.StoryDetailRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class PushEntryTest {
    @Test
    fun `스토리 완성은 상세로, 출석 리마인드는 이프 충전으로 간다`() {
        assertEquals(
            StoryDetailRoute("s1"),
            PushEntry(PushEntry.TYPE_STORY_COMPLETED, "s1", "me").routeFor("me"),
        )
        assertEquals(
            MyCreditChargeRoute,
            PushEntry(PushEntry.TYPE_ATTENDANCE_REMINDER, "2026-09-08", "me").routeFor("me"),
        )
    }

    @Test
    fun `알 수 없는 type과 식별자 누락은 홈이다`() {
        assertEquals(MainTabsRoute, PushEntry("SOMETHING_NEW", "x", "me").routeFor("me"))
        assertEquals(MainTabsRoute, PushEntry(PushEntry.TYPE_PROMOTION, "c1", "me").routeFor("me"))
        assertEquals(MainTabsRoute, PushEntry(PushEntry.TYPE_STORY_COMPLETED, null, "me").routeFor("me"))
    }

    @Test
    fun `수신자가 현재 회원과 다르면 홈이다`() {
        assertEquals(MainTabsRoute, PushEntry(PushEntry.TYPE_STORY_COMPLETED, "s1", "other").routeFor("me"))
        assertEquals(MainTabsRoute, PushEntry(PushEntry.TYPE_STORY_COMPLETED, "s1", null).routeFor("me"))
    }
}
