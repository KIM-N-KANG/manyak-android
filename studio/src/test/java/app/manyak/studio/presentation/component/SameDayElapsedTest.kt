package app.manyak.studio.presentation.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

class SameDayElapsedTest {
    private val kst = TimeZone.getTimeZone("Asia/Seoul")

    private fun at(
        hour: Int,
        minute: Int,
        day: Int = 5,
    ) = GregorianCalendar(kst)
        .apply {
            set(2026, 9, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun today_reads_as_elapsed_time_and_earlier_days_as_date() {
        val now = at(15, 30)

        assertEquals(SameDayElapsed.JustNow, sameDayElapsedOf(now - 59_000, now, kst))
        assertEquals(SameDayElapsed.JustNow, sameDayElapsedOf(now + 60_000, now, kst))
        assertEquals(SameDayElapsed.Minutes(59), sameDayElapsedOf(at(14, 31), now, kst))
        assertEquals(SameDayElapsed.Hours(15), sameDayElapsedOf(at(0, 10), now, kst))
        assertNull(sameDayElapsedOf(at(23, 50, day = 4), at(0, 5), kst))
    }
}
