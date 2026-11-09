package app.manyak.studio.presentation.component

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class SavedAtTextTest {
    @Test
    fun `처음 임시 저장 시각은 기기 시간대와 무관하게 KST 분 단위로 보인다`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            // 2026-09-01T15:10:00Z — KST 로는 다음 날 00:10 이다.
            assertEquals("2026-09-02 00:10", 1_788_275_400_000L.toSavedAtText())
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
