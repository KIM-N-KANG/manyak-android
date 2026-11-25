package app.manyak.notice.domain

import app.manyak.notice.entity.NoticeSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeUnreadTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun notice(
        id: String,
        agoDays: Long,
    ) = NoticeSummary(id, "제목 $id", now - agoDays * day)

    @Test
    fun `게시 30일 안이고 읽은 적이 없어야 안 읽음이다`() {
        assertTrue(notice("a", 0).isUnread(emptyList(), now))
        assertTrue(notice("a", 30).isUnread(emptyList(), now))
        assertFalse(notice("a", 31).isUnread(emptyList(), now))
        assertFalse(notice("a", 1).isUnread(listOf("a"), now))
    }

    @Test
    fun `게시 시각을 모르는 공지는 안 읽음으로 세지 않는다`() {
        assertFalse(NoticeSummary("a", "제목", null).isUnread(emptyList(), now))
    }

    @Test
    fun `목록에 안 읽은 공지가 하나라도 있으면 참이다`() {
        val notices = listOf(notice("a", 40), notice("b", 3), notice("c", 1))
        assertTrue(notices.hasUnread(listOf("b"), now))
        assertFalse(notices.hasUnread(listOf("b", "c"), now))
        assertFalse(emptyList<NoticeSummary>().hasUnread(emptyList(), now))
    }

    @Test
    fun `읽은 ID 는 맨 앞에 두고 중복은 옮기며 100개를 넘기지 않는다`() {
        assertEquals(listOf("b", "a"), listOf("a").withRead("b"))
        assertEquals(listOf("a", "b"), listOf("b", "a").withRead("a"))

        val full = (1..NOTICE_READ_IDS_LIMIT).map { "n$it" }
        val next = full.withRead("new")
        assertEquals(NOTICE_READ_IDS_LIMIT, next.size)
        assertEquals("new", next.first())
        assertFalse("n${NOTICE_READ_IDS_LIMIT}" in next)
    }
}
