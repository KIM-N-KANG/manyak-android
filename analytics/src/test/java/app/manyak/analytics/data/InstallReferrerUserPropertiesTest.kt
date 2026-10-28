package app.manyak.analytics.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallReferrerUserPropertiesTest {
    @Test
    fun `자연 설치는 source 와 medium 만 남긴다`() {
        assertEquals(
            mapOf("initial_utm_source" to "google-play", "initial_utm_medium" to "organic"),
            installReferrerUserProperties("utm_source=google-play&utm_medium=organic"),
        )
    }

    @Test
    fun `인코딩된 값은 풀고 빈 값과 UTM 이 아닌 키는 뺀다`() {
        assertEquals(
            mapOf(
                "initial_utm_source" to "apps.facebook.com",
                "initial_utm_campaign" to "fb4a",
                "initial_utm_content" to "{\"app\":1}",
            ),
            installReferrerUserProperties(
                "utm_source=apps.facebook.com&utm_campaign=fb4a&utm_content=%7B%22app%22%3A1%7D&utm_medium=&gclid=x",
            ),
        )
    }

    @Test
    fun `빈 referrer 는 보낼 속성이 없다`() {
        assertEquals(emptyMap<String, String>(), installReferrerUserProperties(""))
    }
}
