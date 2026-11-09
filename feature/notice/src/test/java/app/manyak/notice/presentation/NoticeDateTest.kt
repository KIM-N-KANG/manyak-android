package app.manyak.notice.presentation

import app.manyak.common.data.time.toEpochMillisOrNull
import app.manyak.notice.entity.NoticeDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoticeDateTest {
    private fun millis(iso: String) = checkNotNull(iso.toEpochMillisOrNull())

    @Test
    fun `KST 기준 날짜로 그린다`() {
        // UTC 23시는 KST 다음 날 08시다.
        assertEquals("2026-10-25", formatNoticeDate(millis("2026-10-24T23:30:00Z")))
        assertEquals("2026-10-24", formatNoticeDate(millis("2026-10-24T04:52:44.122522Z")))
    }

    @Test
    fun `게시한 뒤 날짜가 바뀔 만큼 고쳤을 때만 수정일을 보인다`() {
        val detail =
            NoticeDetail(
                id = "1",
                title = "t",
                body = "b",
                publishedAtMillis = millis("2026-10-24T04:00:00Z"),
                updatedAtMillis = millis("2026-10-24T05:00:00Z"),
            )
        assertNull(detail.updatedDateOrNull())
        assertEquals("2026-10-26", detail.copy(updatedAtMillis = millis("2026-10-26T01:00:00Z")).updatedDateOrNull())
        assertNull(detail.copy(updatedAtMillis = millis("2026-10-20T01:00:00Z")).updatedDateOrNull())
        assertNull(detail.copy(updatedAtMillis = null).updatedDateOrNull())
    }
}
