package app.manyak.notification.inbox.presentation

import app.manyak.core.navigation.NoticeDetailRoute
import app.manyak.core.navigation.StoryDetailRoute
import app.manyak.core.navigation.StudioRoute
import app.manyak.notification.inbox.entity.InboxNotification
import app.manyak.notification.inbox.entity.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import app.manyak.notification.R as NotificationR

class NotificationDisplayTest {
    private val item =
        InboxNotification("n", NotificationCategory.NEWS, "NOTICE", "title", noticeId = "notice", storyId = "story")

    @Test fun `허용 링크 공지 스토리 순서로 이동하고 외부 링크는 열지 않는다`() {
        assertEquals(StudioRoute, item.copy(deepLink = "https://manyak.app/studio").destination())
        assertEquals(NoticeDetailRoute("notice"), item.copy(deepLink = "https://evil.example/studio").destination())
        assertEquals(NoticeDetailRoute("notice"), item.copy(deepLink = "https://manyak.app:8443/studio").destination())
        assertEquals(StoryDetailRoute("story"), item.copy(noticeId = " ").destination())
        assertNull(item.copy(noticeId = null, storyId = null).destination())
    }

    @Test fun `공지 태그는 유형보다 우선하고 알 수 없는 값은 숨긴다`() {
        assertEquals(
            NotificationR.string.inbox_tag_promotion,
            item.copy(type = "PROMOTION", noticeTag = "PROMOTION").labelRes(),
        )
        assertEquals(NotificationR.string.inbox_notice, item.copy(noticeTag = "UNKNOWN").labelRes())
        assertNull(item.copy(type = "UNKNOWN").labelRes())
    }

    @Test fun `웹과 같은 상대 시각 경계를 표시한다`() {
        val zone = ZoneId.of("Asia/Seoul")
        val now = Instant.parse("2026-10-24T14:00:00Z").toEpochMilli()

        fun at(minutes: Long) = notificationTimeValue(now - minutes * 60_000, now, zone)
        assertEquals(NotificationR.string.inbox_just_now, at(0).label)
        assertEquals(NotificationTime(NotificationR.string.inbox_minutes_ago, 59), at(59))
        assertEquals(NotificationTime(NotificationR.string.inbox_hours_ago, 11), at(719))
        assertEquals(NotificationR.string.inbox_today, at(720).label)
        assertEquals(NotificationR.string.inbox_yesterday, at(1440).label)
        assertEquals(NotificationTime(NotificationR.string.inbox_days_ago, 6), at(6 * 1440).let { it })
        assertEquals("2026-10-17", at(7 * 1440).date)
    }
}
