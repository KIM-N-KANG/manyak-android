package app.manyak.common.presentation.story

import app.manyak.common.domain.story.CreateTutorialProgress
import app.manyak.common.entity.story.CreateTutorialStage
import app.manyak.common.entity.story.CreateTutorialStage.ADDITIONAL_INFO
import app.manyak.common.entity.story.CreateTutorialStage.COMPLETING
import app.manyak.common.entity.story.CreateTutorialStage.INTRO
import app.manyak.common.entity.story.CreateTutorialStage.KEYWORD
import app.manyak.common.entity.story.CreateTutorialStage.STORYLINE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateTutorialControllerTest {
    @Test
    fun `stored value decides the next stage`() {
        assertEquals(INTRO, CreateTutorialStage.nextAfter(null))
        assertEquals(STORYLINE, CreateTutorialStage.nextAfter("keyword"))
        assertEquals(COMPLETING, CreateTutorialStage.nextAfter("additional-info"))
        assertNull(CreateTutorialStage.nextAfter("completing"))
        assertNull(CreateTutorialStage.nextAfter(CreateTutorialStage.DONE))
        assertNull(CreateTutorialStage.nextAfter("unknown"))
    }

    @Test
    fun `keyword screen opens the greeting after the delay and starting continues to the keyword guide`() =
        runTest {
            val fixture = fixture(next = INTRO, screen = KEYWORD)

            advanceTimeBy(CreateTutorialController.OPEN_DELAY_MS - 1)
            assertNull(fixture.controller.stage.value)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(INTRO, fixture.controller.stage.value)

            fixture.controller.primary()
            runCurrent()
            assertEquals(KEYWORD, fixture.controller.stage.value)
            assertEquals("intro", fixture.progress.stored)

            fixture.controller.primary()
            runCurrent()
            assertNull(fixture.controller.stage.value)
            assertEquals("keyword", fixture.progress.stored)
            assertEquals(
                listOf(CreateTutorialReport.StepViewed(INTRO), CreateTutorialReport.StepViewed(KEYWORD)),
                fixture.reports,
            )
            fixture.stop()
        }

    @Test
    fun `skipping the greeting finishes every stage`() =
        runTest {
            val fixture = fixture(next = INTRO, screen = KEYWORD)
            advanceTimeBy(CreateTutorialController.OPEN_DELAY_MS + 1)

            fixture.controller.secondary()
            runCurrent()

            assertNull(fixture.controller.stage.value)
            assertEquals(CreateTutorialStage.DONE, fixture.progress.stored)
            assertEquals(CreateTutorialReport.Skipped(INTRO), fixture.reports.last())
            fixture.stop()
        }

    @Test
    fun `a later screen does not open an earlier stage`() =
        runTest {
            val fixture = fixture(next = INTRO, screen = STORYLINE)
            advanceTimeBy(CreateTutorialController.OPEN_DELAY_MS * 2)

            assertNull(fixture.controller.stage.value)
            assertEquals(emptyList<CreateTutorialReport>(), fixture.reports)
            fixture.stop()
        }

    @Test
    fun `a screen that stops being ready within the delay does not open`() =
        runTest {
            val fixture = fixture(next = ADDITIONAL_INFO, screen = ADDITIONAL_INFO)
            advanceTimeBy(CreateTutorialController.OPEN_DELAY_MS / 2)
            fixture.screen.value = null
            advanceTimeBy(CreateTutorialController.OPEN_DELAY_MS * 2)

            assertNull(fixture.controller.stage.value)
            fixture.stop()
        }

    @Test
    fun `the last stage reports how it was closed`() =
        runTest {
            val fixture = fixture(next = COMPLETING, screen = COMPLETING)
            advanceTimeBy(CreateTutorialController.OPEN_DELAY_MS + 1)

            fixture.controller.primary()
            runCurrent()

            assertNull(fixture.controller.stage.value)
            assertEquals(CreateTutorialStage.DONE, fixture.progress.stored)
            assertEquals(CreateTutorialReport.Completed(toOriginals = true), fixture.reports.last())
            fixture.stop()
        }

    private class Fixture(
        val controller: CreateTutorialController,
        val progress: FakeProgress,
        val screen: MutableStateFlow<CreateTutorialStage?>,
        val reports: List<CreateTutorialReport>,
        val stop: () -> Unit,
    )

    private fun TestScope.fixture(
        next: CreateTutorialStage?,
        screen: CreateTutorialStage?,
    ): Fixture {
        val progress = FakeProgress(next)
        val reports = mutableListOf<CreateTutorialReport>()
        val screenFlow = MutableStateFlow(screen)
        val controller = CreateTutorialController(this, progress, screenFlow, report = { reports += it })
        val job = launch { controller.drive() }
        runCurrent()
        return Fixture(controller, progress, screenFlow, reports) { job.cancel() }
    }

    private class FakeProgress(
        private var next: CreateTutorialStage?,
    ) : CreateTutorialProgress {
        var stored: String? = null

        override suspend fun next() = next

        override suspend fun finish(stage: CreateTutorialStage) {
            stored = stage.wire
            next = stage.following
        }

        override suspend fun finishAll() {
            stored = CreateTutorialStage.DONE
            next = null
        }
    }
}
