package app.manyak.notice.presentation

import app.manyak.notice.data.api.dto.NoticeDetailDto
import app.manyak.notice.data.api.dto.NoticeItemDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import app.manyak.notice.R as NoticeR

class NoticeTagTest {
    @Test
    fun `목록과 상세 응답의 태그를 읽고 모르는 태그는 표시하지 않는다`() {
        val body = """{"noticeId":"n","title":"제목","tag":"MAINTENANCE"}"""
        assertEquals("MAINTENANCE", Json.decodeFromString<NoticeItemDto>(body).tag)
        assertEquals("MAINTENANCE", Json.decodeFromString<NoticeDetailDto>(body).tag)
        assertEquals(NoticeR.string.notice_tag_maintenance, noticeTagLabel("MAINTENANCE"))
        assertNull(noticeTagLabel("UNKNOWN"))
        assertNull(noticeTagLabel(null))
    }
}
