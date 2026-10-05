package app.manyak.studio.presentation

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.studio.entity.StorySubmission
import app.manyak.studio.entity.SubmissionStatus
import app.manyak.studio.testing.FakeCreationProgressAccess
import app.manyak.studio.testing.FakeStoryRepository
import app.manyak.studio.testing.sampleStories
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudioSubmissionTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `검토 중일 때만 5초 조회하며 최종 상태가 되면 멈춘다`() =
        runTest(dispatcher) {
            val repository = repository(SubmissionStatus.PENDING)
            val vm = viewModel(repository)
            vm.onIntent(StudioIntent.ScreenShown)
            runCurrent()
            val polling = backgroundScope.launch { vm.drivePendingSubmissionPolling() }
            runCurrent()
            advanceTimeBy(4_999)
            runCurrent()
            assertEquals(1, repository.submissionsCallCount)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, repository.submissionsCallCount)
            repository.currentSubmissions = listOf(submission(SubmissionStatus.REJECTED))
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(3, repository.submissionsCallCount)
            advanceTimeBy(15_000)
            runCurrent()
            assertEquals(3, repository.submissionsCallCount)
            polling.cancel()
        }

    @Test
    fun `화면을 떠나면 폴링 요청을 취소하고 복귀와 새로고침은 즉시 조회한다`() =
        runTest(dispatcher) {
            val repository = repository(SubmissionStatus.PENDING)
            val vm = viewModel(repository)
            vm.onIntent(StudioIntent.ScreenShown)
            runCurrent()
            var canceled = false
            repository.beforeSubmissions = {
                try {
                    awaitCancellation()
                } finally {
                    canceled = true
                }
            }
            val polling = backgroundScope.launch { vm.drivePendingSubmissionPolling() }
            runCurrent()
            advanceTimeBy(5_000)
            runCurrent()
            polling.cancel()
            runCurrent()
            assertTrue(canceled)
            advanceTimeBy(15_000)
            runCurrent()
            assertEquals(2, repository.submissionsCallCount)
            repository.beforeSubmissions = {}
            vm.onIntent(StudioIntent.ScreenShown)
            runCurrent()
            vm.onIntent(StudioIntent.Refresh)
            runCurrent()
            assertEquals(4, repository.submissionsCallCount)
        }

    @Test
    fun `제출본 조회 실패는 내 스토리를 남기고 다음 주기에 복구한다`() =
        runTest(dispatcher) {
            val repository = repository(SubmissionStatus.PENDING)
            val vm = viewModel(repository)
            vm.onIntent(StudioIntent.ScreenShown)
            runCurrent()
            repository.queuedSubmissionResults.add(DomainResult.Failure(DomainError.Network))
            val polling = backgroundScope.launch { vm.drivePendingSubmissionPolling() }
            runCurrent()
            advanceTimeBy(5_000)
            runCurrent()
            assertTrue(
                vm.uiState.value.submissions
                    .isEmpty(),
            )
            assertEquals(sampleStories(), vm.uiState.value.stories)
            assertEquals(1, repository.myStoriesCallCount)
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(repository.currentSubmissions, vm.uiState.value.submissions)
            polling.cancel()
        }

    @Test
    fun `조회 실패로 카드가 숨겨진 동안 하나를 삭제해도 남은 검토 중 제출본을 다시 조회한다`() =
        runTest(dispatcher) {
            val repository = repository(SubmissionStatus.PENDING)
            repository.currentSubmissions += submission(SubmissionStatus.PENDING).copy(id = "remaining")
            val vm = viewModel(repository)
            vm.onIntent(StudioIntent.ScreenShown)
            runCurrent()
            vm.onIntent(StudioIntent.OpenCardOptions(StudioCard.Submission(repository.currentSubmissions.first())))
            runCurrent()
            vm.onIntent(StudioIntent.RequestDelete)
            runCurrent()
            repository.queuedSubmissionResults.add(DomainResult.Failure(DomainError.Network))
            vm.onIntent(StudioIntent.Refresh)
            runCurrent()
            repository.queuedSubmissionResults.add(DomainResult.Failure(DomainError.Network))
            vm.onIntent(StudioIntent.ConfirmDelete)
            runCurrent()
            assertTrue(vm.uiState.value.hasPendingSubmissions)
            assertTrue(
                vm.uiState.value.submissions
                    .isEmpty(),
            )
            val polling = backgroundScope.launch { vm.drivePendingSubmissionPolling() }
            runCurrent()
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(
                listOf("remaining"),
                vm.uiState.value.submissions
                    .map { it.id },
            )
            polling.cancel()
        }

    @Test
    fun `승인되어 사라진 제출본은 진행 중인 스토리 조회 뒤에도 다시 조회한다`() =
        runTest(dispatcher) {
            val repository = repository(SubmissionStatus.PENDING)
            val vm = viewModel(repository)
            vm.onIntent(StudioIntent.ScreenShown)
            runCurrent()
            val waiting = CompletableDeferred<Unit>()
            repository.beforeStories = { waiting.await() }
            vm.onIntent(StudioIntent.ScreenShown)
            runCurrent()
            assertEquals(2, repository.myStoriesCallCount)
            repository.currentSubmissions = emptyList()
            val polling = backgroundScope.launch { vm.drivePendingSubmissionPolling() }
            runCurrent()
            advanceTimeBy(5_000)
            runCurrent()
            assertTrue(
                vm.uiState.value.submissions
                    .isEmpty(),
            )
            waiting.complete(Unit)
            runCurrent()
            assertEquals(3, repository.myStoriesCallCount)
            assertFalse(vm.uiState.value.hasPendingSubmissions)
            polling.cancel()
        }

    @Test
    fun `등록 취소는 확인 후 한 번만 삭제하고 성공 토스트를 보낸다`() =
        runTest(dispatcher) {
            val repository = repository(SubmissionStatus.PENDING)
            val vm = viewModel(repository)
            vm.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()
            vm.onIntent(StudioIntent.OpenCardOptions(StudioCard.Submission(repository.currentSubmissions.single())))
            advanceUntilIdle()
            vm.onIntent(StudioIntent.RequestDelete)
            advanceUntilIdle()
            assertTrue(repository.deletedSubmissionIds.isEmpty())
            assertNull(vm.uiState.value.optionsTarget)
            vm.onIntent(StudioIntent.ConfirmDelete)
            vm.onIntent(StudioIntent.ConfirmDelete)
            advanceUntilIdle()
            assertEquals(listOf("submission"), repository.deletedSubmissionIds)
            assertTrue(
                vm.uiState.value.submissions
                    .isEmpty(),
            )
            assertNull(vm.uiState.value.deleteTarget)
            assertEquals(StudioEffect.ShowSubmissionCanceled, vm.uiEffect.first())
        }

    @Test
    fun `반려와 실패 삭제는 제출본만 제거한다`() =
        runTest(dispatcher) {
            for (status in listOf(SubmissionStatus.REJECTED, SubmissionStatus.FAILED)) {
                val repository = repository(status)
                val vm = viewModel(repository)
                vm.onIntent(StudioIntent.ScreenShown)
                advanceUntilIdle()
                vm.onIntent(StudioIntent.OpenCardOptions(StudioCard.Submission(repository.currentSubmissions.single())))
                advanceUntilIdle()
                vm.onIntent(StudioIntent.RequestDelete)
                advanceUntilIdle()
                vm.onIntent(StudioIntent.ConfirmDelete)
                advanceUntilIdle()
                assertEquals(listOf("submission"), repository.deletedSubmissionIds)
                assertTrue(repository.deletedStoryIds.isEmpty())
                assertEquals(sampleStories(), vm.uiState.value.stories)
                assertTrue(
                    vm.uiState.value.submissions
                        .isEmpty(),
                )
            }
        }

    @Test
    fun `삭제 실패는 카드를 유지하고 취소 실패를 알린다`() =
        runTest(dispatcher) {
            val repository = repository(SubmissionStatus.PENDING)
            repository.submissionDeleteResult = DomainResult.Failure(DomainError.Network)
            val vm = viewModel(repository)
            vm.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()
            vm.onIntent(StudioIntent.OpenCardOptions(StudioCard.Submission(repository.currentSubmissions.single())))
            advanceUntilIdle()
            vm.onIntent(StudioIntent.RequestDelete)
            advanceUntilIdle()
            vm.onIntent(StudioIntent.ConfirmDelete)
            advanceUntilIdle()
            assertEquals(repository.currentSubmissions, vm.uiState.value.submissions)
            assertFalse(vm.uiState.value.isDeleting)
            assertEquals(StudioEffect.ShowSubmissionCancelFailed, vm.uiEffect.first())
        }

    @Test
    fun `옵션을 연 동안 상태가 바뀌면 최신 상태를 쓰고 사라지면 닫는다`() =
        runTest(dispatcher) {
            val repository = repository(SubmissionStatus.PENDING)
            val vm = viewModel(repository)
            vm.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()
            vm.onIntent(StudioIntent.OpenCardOptions(StudioCard.Submission(repository.currentSubmissions.single())))
            advanceUntilIdle()
            repository.currentSubmissions = listOf(submission(SubmissionStatus.REJECTED))
            vm.onIntent(StudioIntent.Refresh)
            advanceUntilIdle()
            assertEquals(StudioCard.Submission(repository.currentSubmissions.single()), vm.uiState.value.optionsTarget)
            vm.onIntent(StudioIntent.RequestDelete)
            advanceUntilIdle()
            repository.currentSubmissions = emptyList()
            vm.onIntent(StudioIntent.Refresh)
            advanceUntilIdle()
            assertNull(vm.uiState.value.deleteTarget)
        }

    @Test
    fun `조회 중 삭제는 순서를 지켜 늦은 응답이 삭제 카드를 되살리지 않는다`() =
        runTest(dispatcher) {
            val repository = repository(SubmissionStatus.REJECTED)
            val vm = viewModel(repository)
            vm.onIntent(StudioIntent.ScreenShown)
            runCurrent()
            vm.onIntent(StudioIntent.OpenCardOptions(StudioCard.Submission(repository.currentSubmissions.single())))
            runCurrent()
            vm.onIntent(StudioIntent.RequestDelete)
            runCurrent()
            val waiting = CompletableDeferred<Unit>()
            repository.beforeSubmissions = { waiting.await() }
            vm.onIntent(StudioIntent.Refresh)
            runCurrent()
            vm.onIntent(StudioIntent.ConfirmDelete)
            runCurrent()
            assertTrue(repository.deletedSubmissionIds.isEmpty())
            waiting.complete(Unit)
            runCurrent()
            assertEquals(listOf("submission"), repository.deletedSubmissionIds)
            assertTrue(
                vm.uiState.value.submissions
                    .isEmpty(),
            )
            assertFalse(vm.uiState.value.isDeleting)
        }

    @Test
    fun `제출본 동작 이벤트는 식별자 상태 동작만 담고 취소하면 삭제하지 않는다`() =
        runTest(dispatcher) {
            val events = mutableListOf<app.manyak.analytics.entity.AnalyticsEvent>()
            val analytics =
                object : app.manyak.analytics.domain.Analytics {
                    override fun track(event: app.manyak.analytics.entity.AnalyticsEvent) {
                        events += event
                    }
                }
            for (status in SubmissionStatus.entries) {
                val repository = repository(status)
                val vm = StudioViewModel(FakeCreationProgressAccess(), repository, analytics, repository)
                vm.onIntent(StudioIntent.ScreenShown)
                runCurrent()
                vm.onIntent(StudioIntent.OpenCardOptions(StudioCard.Submission(repository.currentSubmissions.single())))
                runCurrent()
                vm.onIntent(StudioIntent.RequestDelete)
                runCurrent()
                val event = events.last()
                assertEquals("client_storyList_submissionCard_clicked", event.name)
                assertEquals(
                    mapOf(
                        "submission_id" to "submission",
                        "status" to status.name.lowercase(),
                        "action" to if (status == SubmissionStatus.PENDING) "cancel" else "delete",
                    ),
                    event.properties,
                )
                vm.onIntent(StudioIntent.DismissDeleteDialog)
                runCurrent()
                assertTrue(repository.deletedSubmissionIds.isEmpty())
            }
        }

    private fun viewModel(repository: FakeStoryRepository) =
        StudioViewModel(FakeCreationProgressAccess(), repository, NoOpAnalytics, repository)

    private fun repository(status: SubmissionStatus) =
        FakeStoryRepository().apply {
            currentSubmissions = listOf(submission(status))
        }

    private fun submission(status: SubmissionStatus) =
        StorySubmission("submission", status, "검수 스토리", null, null, 0, false)
}
