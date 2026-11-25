package app.manyak.chat.list.presentation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * 웹 `formatRelativeDate` 와 같은 여섯 경계를 고정한다. 계단이 갈리면 같은 채팅의 마지막 활동이
 * 두 클라이언트에서 다른 시각으로 읽힌다.
 */
class RelativeTimeTest {
    private val seoul: TimeZone = TimeZone.getTimeZone("Asia/Seoul")

    @Test
    fun `1분 미만은 방금 전이다`() {
        val now = at(2026, 8, 28, 12, 0)

        assertEquals(RelativeTime.JustNow, relativeTimeOf(now - 59_000L, now, seoul))
        assertEquals(RelativeTime.Minutes(1), relativeTimeOf(now - 60_000L, now, seoul))
    }

    @Test
    fun `기기 시계가 뒤처져 미래 시각이 와도 방금 전이다`() {
        val now = at(2026, 8, 28, 12, 0)

        assertEquals(RelativeTime.JustNow, relativeTimeOf(now + 600_000L, now, seoul))
    }

    @Test
    fun `1시간 미만은 분, 12시간 미만은 시간으로 센다`() {
        val now = at(2026, 8, 28, 12, 0)

        assertEquals(RelativeTime.Minutes(59), relativeTimeOf(now - 59 * 60_000L, now, seoul))
        assertEquals(RelativeTime.Hours(1), relativeTimeOf(now - 60 * 60_000L, now, seoul))
        assertEquals(RelativeTime.Hours(11), relativeTimeOf(now - (11 * 60 + 59) * 60_000L, now, seoul))
    }

    @Test
    fun `12시간을 넘겨도 같은 날이면 오늘이다`() {
        // 오전에 진행한 채팅을 밤에 보는 자리 — 시간 경계를 지났지만 달력은 그대로다.
        val now = at(2026, 8, 28, 23, 0)

        assertEquals(RelativeTime.Today, relativeTimeOf(at(2026, 8, 28, 11, 0), now, seoul))
    }

    @Test
    fun `전날은 어제이고 그 앞은 며칠 전이다`() {
        val now = at(2026, 8, 28, 12, 0)

        assertEquals(RelativeTime.Yesterday, relativeTimeOf(at(2026, 8, 27, 20, 0), now, seoul))
        assertEquals(RelativeTime.Days(2), relativeTimeOf(at(2026, 8, 26, 20, 0), now, seoul))
        assertEquals(RelativeTime.Days(6), relativeTimeOf(at(2026, 8, 22, 12, 0), now, seoul))
    }

    @Test
    fun `7일부터는 기기 시간대의 날짜를 그대로 쓴다`() {
        val now = at(2026, 8, 28, 12, 0)

        assertEquals(RelativeTime.AbsoluteDate("2026-08-21"), relativeTimeOf(at(2026, 8, 21, 12, 0), now, seoul))
    }

    @Test
    fun `날짜는 UTC 가 아니라 기기 시간대로 자른다`() {
        // UTC 로는 2026-08-20 인 시각이 서울에서는 2026-08-21 이다.
        val utcLateNight =
            GregorianCalendar(TimeZone.getTimeZone("UTC"))
                .apply {
                    clear()
                    set(2026, Calendar.AUGUST, 20, 22, 0)
                }.timeInMillis

        assertEquals(
            RelativeTime.AbsoluteDate("2026-08-21"),
            relativeTimeOf(utcLateNight, at(2026, 8, 28, 12, 0), seoul),
        )
    }

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long =
        GregorianCalendar(seoul)
            .apply {
                clear()
                set(year, month - 1, day, hour, minute)
            }.timeInMillis
}
