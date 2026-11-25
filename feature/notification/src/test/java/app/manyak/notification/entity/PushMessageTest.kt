package app.manyak.notification.entity

import app.manyak.core.navigation.PushEntry
import app.manyak.core.navigation.StudioRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushMessageTest {
    @Test
    fun `검수 완료는 서비스 알림이며 서버 문구를 보존하고 제작 탭을 연다`() {
        for (status in listOf("APPROVED", "REJECTED", "FAILED")) {
            val message = requireNotNull(PushMessage.from(payload("s1") + ("status" to status)))
            assertFalse(message.isMarketing)
            assertEquals("검수를 통과했어요", message.title)
            assertEquals("스토리 등록이 완료됐어요", message.body)
            assertEquals("s1", message.targetId)
            assertEquals(StudioRoute, message.entry.routeFor("me"))
        }
    }

    @Test
    fun `다른 제출본은 따로 남고 같은 제출본 재발송은 기존 알림을 교체한다`() {
        val first = requireNotNull(PushMessage.from(payload("s1")))
        val second = requireNotNull(PushMessage.from(payload("s2")))
        val repeated = requireNotNull(PushMessage.from(payload("s1") + ("storyId" to "new-story")))
        assertNotEquals(first.notificationId, second.notificationId)
        assertEquals(first.notificationId, repeated.notificationId)
    }

    @Test
    fun `제출본 ID 또는 최종 상태가 없는 검수 알림은 버린다`() {
        assertNull(PushMessage.from(payload("")))
        assertNull(PushMessage.from(payload("s1") - "status"))
        assertNull(PushMessage.from(payload("s1") + ("status" to "PENDING")))
    }

    private fun payload(id: String) =
        mapOf(
            "type" to PushEntry.TYPE_STORY_MODERATION_COMPLETED,
            "submissionId" to id,
            "recipientId" to "me",
            "status" to "APPROVED",
            "storyId" to "story",
            "deepLink" to "https://manyak.app/studio",
            "title" to "검수를 통과했어요",
            "body" to "스토리 등록이 완료됐어요",
        )
}
