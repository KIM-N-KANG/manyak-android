package app.manyak.core.data.api.dto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class DisplayDateTest {
    @Test
    fun `KST 자정을 넘긴 UTC 시각은 다음 날짜로 옮긴다`() {
        // 자르기만 하면 방금 받은 출석 보상이 어제 받은 것으로 보인다.
        assertEquals("2026-09-02", "2026-09-01T15:10:00Z".toDisplayDate())
        assertEquals("2026-10-02", "2026-10-01T15:10:00Z".toDisplayDate())
    }

    @Test
    fun `KST 자정 직전 시각은 그날 날짜를 유지한다`() {
        assertEquals("2026-09-01", "2026-09-01T14:59:59Z".toDisplayDate())
    }

    @Test
    fun `기기 시간대와 무관하게 KST 로 읽는다`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals("2026-09-02", "2026-09-01T15:10:00Z".toDisplayDate())
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `오프셋과 소수점 이하 초가 붙어 와도 같은 시점으로 읽는다`() {
        assertEquals("2026-09-02", "2026-09-02T00:10:00+09:00".toDisplayDate())
        assertEquals("2026-09-02", "2026-09-01T15:10:00.123456Z".toDisplayDate())
    }

    @Test
    fun `시각 없이 날짜만 오면 옮길 시점이 없어 그대로 쓴다`() {
        assertEquals("2026-09-01", "2026-09-01".toDisplayDate())
    }

    @Test
    fun `읽을 수 없는 값은 null 이고 화면이 그 줄을 그리지 않는다`() {
        listOf("", "어제", "2026-13-28T09:12:33Z", "2026-02-30T09:12:33Z").forEach { value ->
            assertNull(value, value.toDisplayDate())
        }
    }
}
