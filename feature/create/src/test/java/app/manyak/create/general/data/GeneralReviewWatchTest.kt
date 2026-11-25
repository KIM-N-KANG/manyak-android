package app.manyak.create.general.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeneralReviewWatchTest {
    private var now = 0L
    private val watch = GeneralReviewWatch { now }

    @Test
    fun watching_suppresses_any_result_only_until_the_screen_stops_waiting() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val waiting = async { watch.watching("s1") { release.await() } }
            runCurrent()
            assertTrue(watch.suppresses("s1", "REJECTED"))
            assertFalse(watch.suppresses("s2", "REJECTED"))
            release.complete(Unit)
            waiting.await()
            assertFalse(watch.suppresses("s1", "REJECTED"))
        }

    @Test
    fun a_shown_result_suppresses_the_same_late_alert_within_the_window() {
        watch.shown("s1", "APPROVED")
        assertTrue(watch.suppresses("s1", "APPROVED"))
        assertFalse(watch.suppresses("s1", "REJECTED"))
        now += 2 * 60 * 1000L + 1
        assertFalse(watch.suppresses("s1", "APPROVED"))
    }

    @Test
    fun waiting_again_after_resubmission_forgets_the_previous_shown_result() =
        runTest {
            watch.shown("s1", "REJECTED")
            watch.watching("s1") { }
            assertFalse(watch.suppresses("s1", "REJECTED"))
        }
}
