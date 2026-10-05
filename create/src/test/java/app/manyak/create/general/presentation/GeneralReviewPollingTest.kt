package app.manyak.create.general.presentation

import app.manyak.common.domain.error.DomainResult
import app.manyak.create.general.entity.GeneralStoryContent
import app.manyak.create.general.entity.GeneralStoryEditor
import app.manyak.create.general.entity.GeneralSubmission
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeneralReviewPollingTest {
    @Test
    fun polling_waits_one_second_and_stops_at_a_terminal_result() =
        runTest {
            val times = mutableListOf<Long>()
            var completed = false
            pollGeneralReview(
                startedAt = 0,
                now = { testScheduler.currentTime },
                fetch = {
                    times += testScheduler.currentTime
                    DomainResult.Success(editor(if (times.size == 3) "REJECTED" else "PENDING"))
                },
                onResult = { completed = true },
                onTimeout = { error("unexpected timeout") },
            )
            assertEquals(listOf(1000L, 2000L, 3000L), times)
            assertTrue(completed)
        }

    @Test
    fun a_hanging_fetch_is_cancelled_at_the_overall_sixty_second_deadline() =
        runTest {
            var cancelled = false
            var timedOut = false
            pollGeneralReview(
                startedAt = 0,
                now = { testScheduler.currentTime },
                fetch = {
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled = true
                    }
                },
                onResult = { error("unexpected result") },
                onTimeout = { timedOut = true },
            )
            assertEquals(60_000L, testScheduler.currentTime)
            assertTrue(cancelled)
            assertTrue(timedOut)
        }

    @Test
    fun leaving_the_screen_cancels_polling_without_a_timeout_effect() =
        runTest {
            var fetches = 0
            var timedOut = false
            val job =
                launch {
                    pollGeneralReview(
                        startedAt = 0,
                        now = { testScheduler.currentTime },
                        fetch = {
                            fetches++
                            DomainResult.Success(editor("PENDING"))
                        },
                        onResult = { error("unexpected result") },
                        onTimeout = { timedOut = true },
                    )
                }
            advanceTimeBy(2500)
            runCurrent()
            job.cancel()
            advanceTimeBy(60_000)
            assertEquals(2, fetches)
            assertFalse(timedOut)
        }

    @Test
    fun approval_followup_is_not_cancelled_by_the_review_deadline() =
        runTest {
            var completed = false
            pollGeneralReview(
                startedAt = -58_000,
                now = { testScheduler.currentTime },
                fetch = { DomainResult.Success(editor("APPROVED")) },
                onResult = {
                    delay(10_000)
                    completed = true
                },
                onTimeout = { error("unexpected timeout") },
            )
            assertTrue(completed)
        }

    private fun editor(status: String) =
        GeneralStoryEditor(
            content = GeneralStoryContent("제목", "소개"),
            submission = GeneralSubmission("submission", status),
        )
}
