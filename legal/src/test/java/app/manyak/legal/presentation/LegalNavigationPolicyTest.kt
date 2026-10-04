package app.manyak.legal.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class LegalNavigationPolicyTest {
    @Test
    fun `문서 경로와 앵커만 내부에서 연다`() {
        for (path in listOf("/terms", "/privacy/", "/about?source=my#guest")) {
            assertEquals(LegalNavigation.DOCUMENT, legalNavigation("https://manyak.app$path", "manyak.app"))
        }
    }

    @Test
    fun `외부 웹은 브라우저로 넘기고 내부 기능과 실행 가능한 스킴은 차단한다`() {
        assertEquals(LegalNavigation.EXTERNAL, legalNavigation("https://example.com/help", "manyak.app"))
        for (url in listOf(
            "https://manyak.app/",
            "https://manyak.app/onboarding",
            "https://manyak.app/my/feedback",
            "javascript:alert(1)",
            "intent://test",
            "file:///terms",
            "http://manyak.app/terms",
            "https://user@manyak.app/terms",
            "not a url",
        )) {
            assertEquals(url, LegalNavigation.BLOCKED, legalNavigation(url, "manyak.app"))
        }
    }
}
