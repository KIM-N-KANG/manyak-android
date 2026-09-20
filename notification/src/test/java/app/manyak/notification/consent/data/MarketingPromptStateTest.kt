package app.manyak.notification.consent.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 거절 뒤 세 번째 재진입에만 다시 묻고, 두 번 거절하거나 허용하면 더 묻지 않는다. */
class MarketingPromptStateTest {
    @Test
    fun `묻지 않은 회원에게는 바로 묻는다`() {
        val (next, prompt) = MarketingPromptState(declines = 0, entriesSinceDecline = 0).onEntry()

        assertTrue(prompt)
        assertEquals(MarketingPromptState(0, 0), next)
    }

    @Test
    fun `한 번 거절하면 세 번째 재진입에 다시 묻는다`() {
        var state = MarketingPromptState(declines = 0, entriesSinceDecline = 0).onDeclined()
        val prompts =
            List(4) {
                val (next, prompt) = state.onEntry()
                state = next
                prompt
            }

        assertEquals(listOf(false, false, true, true), prompts)
    }

    @Test
    fun `두 번 거절하거나 허용하면 더 묻지 않는다`() {
        val declinedTwice = MarketingPromptState(declines = 1, entriesSinceDecline = 3).onDeclined()
        val settled = MarketingPromptState(declines = 0, entriesSinceDecline = 0).onSettled()

        assertFalse(declinedTwice.onEntry().second)
        assertFalse(settled.onEntry().second)
        assertFalse(declinedTwice.onDeclined().onEntry().second)
    }
}
