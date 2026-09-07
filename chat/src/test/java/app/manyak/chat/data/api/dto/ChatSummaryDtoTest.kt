package app.manyak.chat.data.api.dto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

class ChatSummaryDtoTest {
    @Test
    fun `빈 미리보기는 정상 값이라 그대로 두고 항목을 버리지 않는다`() {
        // 완료 턴이 없는 채팅(생성 직후)이다. 걸러 내면 방금 만든 채팅이 목록에서 조용히 사라진다.
        val domain = ChatSummaryDto(id = "chat-1", storyTitle = "두 번째 시계공").toDomain()

        assertEquals("chat-1", domain.id)
        assertEquals("", domain.lastStoryPreview)
        assertEquals(0L, domain.turnCount)
    }

    @Test
    fun `빈 썸네일 URL 은 없는 것으로 본다`() {
        assertNull(ChatSummaryDto(id = "chat-1", thumbnailUrlSm = "  ").toDomain().thumbnailUrl)
        assertEquals(
            "https://cdn.example/thumb_sm.png",
            ChatSummaryDto(id = "chat-1", thumbnailUrlSm = "https://cdn.example/thumb_sm.png").toDomain().thumbnailUrl,
        )
    }

    @Test
    fun `ISO 8601 UTC 시각을 epoch millis 로 읽는다`() {
        val expected = utcMillis(2026, 8, 28, 9, 12, 33)

        assertEquals(
            expected,
            ChatSummaryDto(id = "c", updatedAt = "2026-08-28T09:12:33Z").toDomain().updatedAtEpochMillis,
        )
        assertEquals(
            expected + 123L,
            ChatSummaryDto(id = "c", updatedAt = "2026-08-28T09:12:33.123456Z").toDomain().updatedAtEpochMillis,
        )
        // 오프셋 표기가 없으면 서버 계약대로 UTC 로 읽는다.
        assertEquals(
            expected,
            ChatSummaryDto(id = "c", updatedAt = "2026-08-28T09:12:33").toDomain().updatedAtEpochMillis,
        )
    }

    @Test
    fun `오프셋이 붙어 오면 그만큼 당겨 읽는다`() {
        // 무시하면 시간대가 바뀐 날 방금 진행한 채팅이 9시간 전으로 보인다.
        val expected = utcMillis(2026, 8, 28, 0, 12, 33)

        assertEquals(
            expected,
            ChatSummaryDto(id = "c", updatedAt = "2026-08-28T09:12:33+09:00").toDomain().updatedAtEpochMillis,
        )
        assertEquals(
            expected,
            ChatSummaryDto(id = "c", updatedAt = "2026-08-28T09:12:33+0900").toDomain().updatedAtEpochMillis,
        )
    }

    @Test
    fun `읽을 수 없는 시각은 null 이고 카드가 그 자리를 그리지 않는다`() {
        val unreadable =
            listOf(
                null,
                "",
                "2026-08-28",
                "어제",
                // 관대 모드였다면 조용히 다음 해로 넘어가 그럴듯한 시각이 됐을 값이다.
                "2026-13-28T09:12:33Z",
                "2026-02-30T09:12:33Z",
            )

        unreadable.forEach { value ->
            assertNull(value, ChatSummaryDto(id = "c", updatedAt = value).toDomain().updatedAtEpochMillis)
        }
    }

    private fun utcMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): Long =
        GregorianCalendar(TimeZone.getTimeZone("UTC"))
            .apply {
                clear()
                set(year, month - 1, day, hour, minute, second)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
}
