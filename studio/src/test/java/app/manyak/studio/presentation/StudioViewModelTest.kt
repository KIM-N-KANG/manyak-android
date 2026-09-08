package app.manyak.studio.presentation

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.story.CreationProgressAccess
import app.manyak.common.entity.story.CompletionRequestStatus
import app.manyak.common.entity.story.CompletionRequestSummary
import app.manyak.common.entity.story.CreationProgressSummary
import app.manyak.common.entity.story.CreationResumePoint
import app.manyak.common.entity.story.CreationStage
import app.manyak.report.entity.StoryReportReason
import app.manyak.report.presentation.StoryReportAction
import app.manyak.studio.testing.FakeCreationProgressAccess
import app.manyak.studio.testing.FakeStoryRepository
import app.manyak.studio.testing.sampleStories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `초안이 없으면 카드 없이 바로 새 생성으로 진입한다`() =
        runTest(dispatcher) {
            val viewModel = studioViewModel(FakeCreationProgressAccess(), FakeStoryRepository(), NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.draft)

            viewModel.onIntent(StudioIntent.CreateStory)
            advanceUntilIdle()

            assertEquals(
                StudioEffect.NavigateToCreate,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `추가 정보 단계 초안은 그 단계의 재개 지점이 된다`() =
        runTest(dispatcher) {
            val store = FakeCreationProgressAccess(draft = draftRecord(selectedIndex = 2))
            val viewModel = studioViewModel(store, FakeStoryRepository(), NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()

            assertEquals(
                CreationResumePoint.AdditionalInfoStep(storylineIndex = 2),
                viewModel.uiState.value.draft
                    ?.resumePoint,
            )
        }

    @Test
    fun `초안이 있는 FAB 진입은 다이얼로그로 묻고 새로 만들기는 초안만 폐기 후 진입한다`() =
        runTest(dispatcher) {
            val store = FakeCreationProgressAccess(draft = generatingRecord(), requests = listOf(pendingRequest("a")))
            val viewModel = studioViewModel(store, FakeStoryRepository(), NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()

            viewModel.onIntent(StudioIntent.CreateStory)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.showResumeChoiceDialog)

            viewModel.onIntent(StudioIntent.StartNewCreation)
            advanceUntilIdle()

            assertNull(store.currentDraft)
            assertEquals(listOf(pendingRequest("a")), store.currentRequests)
            assertFalse(viewModel.uiState.value.showResumeChoiceDialog)
            assertEquals(
                StudioEffect.NavigateToCreate,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `이어서 만들기는 레코드 단계의 재개 지점으로 진입한다`() =
        runTest(dispatcher) {
            val store = FakeCreationProgressAccess(draft = generatingRecord())
            val viewModel = studioViewModel(store, FakeStoryRepository(), NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()

            viewModel.onIntent(StudioIntent.ResumeCreation)
            advanceUntilIdle()

            assertEquals(
                StudioEffect.NavigateToResume(CreationResumePoint.StorylineStep),
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `화면이 보이면 내 스토리를 조회해 목록 상태가 된다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val viewModel = studioViewModel(FakeCreationProgressAccess(), repository, NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.loadFailed)
            assertEquals(sampleStories(), state.stories)
            assertEquals(1, repository.myStoriesCallCount)
        }

    @Test
    fun `화면에 다시 보이면 목록을 다시 읽어 새로 만든 스토리를 반영한다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            repository.queuedResults.add(DomainResult.Success(sampleStories().take(1)))
            val viewModel = studioViewModel(FakeCreationProgressAccess(), repository, NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()
            assertEquals(sampleStories().take(1), viewModel.uiState.value.stories)

            // 퍼널·채팅방을 거쳐 돌아온 자리.
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(sampleStories(), state.stories)
            assertFalse(state.isLoading)
            assertEquals(2, repository.myStoriesCallCount)
        }

    @Test
    fun `갱신이 실패해도 보고 있던 목록을 그대로 둔다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val viewModel = studioViewModel(FakeCreationProgressAccess(), repository, NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()

            repository.queuedResults.add(DomainResult.Failure(DomainError.Network))
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(sampleStories(), state.stories)
            assertFalse(state.loadFailed)
            assertFalse(state.isLoading)
        }

    @Test
    fun `조회 실패는 실패 상태가 되고 재시도가 다시 조회한다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            repository.queuedResults.add(DomainResult.Failure(DomainError.Network))
            val viewModel = studioViewModel(FakeCreationProgressAccess(), repository, NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()

            val failedState = viewModel.uiState.value
            assertTrue(failedState.loadFailed)
            assertTrue(failedState.stories.isEmpty())

            viewModel.onIntent(StudioIntent.Retry)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.loadFailed)
            assertEquals(sampleStories(), state.stories)
            assertEquals(2, repository.myStoriesCallCount)
        }

    @Test
    fun `당겨서 새로고침은 골격 없이 목록을 다시 읽는다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val viewModel = studioViewModel(FakeCreationProgressAccess(), repository, NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()

            viewModel.onIntent(StudioIntent.Refresh)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, repository.myStoriesCallCount)
            assertFalse(state.isRefreshing)
            assertFalse(state.isLoading)
            assertEquals(sampleStories(), state.stories)
        }

    @Test
    fun `새로고침 실패는 목록을 그대로 두고 실패 안내를 보낸다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val viewModel = studioViewModel(FakeCreationProgressAccess(), repository, NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()

            repository.queuedResults.add(DomainResult.Failure(DomainError.Network))
            viewModel.onIntent(StudioIntent.Refresh)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isRefreshing)
            assertFalse(state.loadFailed)
            assertEquals(sampleStories(), state.stories)
            assertEquals(
                StudioEffect.ShowRefreshFailed,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `삭제는 확인을 거쳐 목록에서 대상만 제거하고 완료 안내를 보낸다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val viewModel = studioViewModel(FakeCreationProgressAccess(), repository, NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()
            val loadedState = viewModel.uiState.value
            val target = loadedState.stories.first()

            viewModel.onIntent(StudioIntent.OpenCardOptions(StudioCard.Story(target)))
            advanceUntilIdle()
            viewModel.onIntent(StudioIntent.RequestDelete)
            advanceUntilIdle()
            assertEquals(StudioCard.Story(target), viewModel.uiState.value.deleteTarget)

            viewModel.onIntent(StudioIntent.ConfirmDelete)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.deleteTarget)
            assertFalse(state.isDeleting)
            assertEquals(listOf(target.id), repository.deletedStoryIds)
            assertFalse(state.stories.any { story -> story.id == target.id })
            assertEquals(
                StudioEffect.ShowStoryDeleted,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `삭제 실패는 목록을 그대로 두고 실패 안내를 보낸다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            repository.queuedDeleteResults.add(DomainResult.Failure(DomainError.Network))
            val viewModel = studioViewModel(FakeCreationProgressAccess(), repository, NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()
            val loadedState = viewModel.uiState.value
            val target = loadedState.stories.first()

            // 실제 화면처럼 다이얼로그가 뜬 뒤에야 확인을 누를 수 있다.
            viewModel.onIntent(StudioIntent.OpenCardOptions(StudioCard.Story(target)))
            advanceUntilIdle()
            viewModel.onIntent(StudioIntent.RequestDelete)
            advanceUntilIdle()
            viewModel.onIntent(StudioIntent.ConfirmDelete)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.deleteTarget)
            assertEquals(sampleStories(), state.stories)
            assertEquals(
                StudioEffect.ShowStoryDeleteFailed,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `삭제 다이얼로그는 닫기 요청으로 대상 없이 닫힌다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val viewModel = studioViewModel(FakeCreationProgressAccess(), repository, NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()
            val loadedState = viewModel.uiState.value
            val target = loadedState.stories.first()

            viewModel.onIntent(StudioIntent.OpenCardOptions(StudioCard.Story(target)))
            advanceUntilIdle()
            viewModel.onIntent(StudioIntent.RequestDelete)
            viewModel.onIntent(StudioIntent.DismissDeleteDialog)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.deleteTarget)
            assertTrue(repository.deletedStoryIds.isEmpty())
        }

    @Test
    fun `옵션 시트에서 연 신고는 그 카드의 스토리로 나가고 시트를 닫는다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val viewModel = studioViewModel(FakeCreationProgressAccess(), repository, NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()
            val target =
                viewModel.uiState.value.stories
                    .first()

            viewModel.onIntent(StudioIntent.OpenCardOptions(StudioCard.Story(target)))
            advanceUntilIdle()
            viewModel.onIntent(StudioIntent.Report(StoryReportAction.Open))
            advanceUntilIdle()
            // 신고하기는 옵션 시트를 닫고 신고 시트를 연다 — 두 시트가 겹치지 않는다.
            assertNull(viewModel.uiState.value.optionsTarget)
            assertTrue(viewModel.uiState.value.report.isSheetOpen)

            viewModel.onIntent(StudioIntent.Report(StoryReportAction.SelectReason(StoryReportReason.SPAM)))
            advanceUntilIdle()
            viewModel.onIntent(StudioIntent.Report(StoryReportAction.Submit))
            advanceUntilIdle()

            assertEquals(listOf(target.id), repository.reportedStoryIds)
            assertFalse(viewModel.uiState.value.report.isSheetOpen)
            assertNull(viewModel.uiState.value.reportStoryId)
        }

    @Test
    fun `완성 중 요청만 있으면 묻지 않고 새 생성으로 진입하고 새로고침이 요청 상태를 조회한다`() =
        runTest(dispatcher) {
            val store = FakeCreationProgressAccess(requests = listOf(pendingRequest("a"), pendingRequest("b")))
            val repository = FakeStoryRepository()
            repository.queuedResults.add(DomainResult.Success(emptyList()))
            val viewModel = studioViewModel(store, repository, NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()

            // 완성 스토리가 0개여도 로컬 카드가 있어 목록으로 그린다.
            assertTrue(viewModel.uiState.value.hasLocalCards)
            assertEquals(2, viewModel.uiState.value.completionRequests.size)
            assertEquals(1, store.refreshCount)

            viewModel.onIntent(StudioIntent.Refresh)
            advanceUntilIdle()
            assertEquals(2, store.refreshCount)

            viewModel.onIntent(StudioIntent.CreateStory)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.showResumeChoiceDialog)
            assertEquals(
                StudioEffect.NavigateToCreate,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `완성 중 카드가 있는 동안만 5초마다 요청 상태를 조회한다`() =
        runTest(dispatcher) {
            val store = FakeCreationProgressAccess(requests = listOf(pendingRequest("a")))
            val viewModel = studioViewModel(store, FakeStoryRepository(), NoOpAnalytics)
            val polling = launch { viewModel.drivePendingCompletionPolling() }
            // 주기 루프는 끝이 없어 advanceUntilIdle 을 쓰면 가상 시간을 무한히 소비한다.
            runCurrent()
            assertEquals(0, store.refreshCount)

            advanceTimeBy(5_001)
            assertEquals(1, store.refreshCount)
            advanceTimeBy(5_000)
            assertEquals(2, store.refreshCount)

            // 미확정 요청이 사라지면 더 조회하지 않는다.
            store.emitRequests(emptyList())
            advanceTimeBy(20_000)
            assertEquals(2, store.refreshCount)
            polling.cancel()
        }

    @Test
    fun `완료된 요청은 목록에 같은 스토리가 실리면 지워지고 아직 없으면 목록을 다시 읽는다`() =
        runTest(dispatcher) {
            val store = FakeCreationProgressAccess()
            val repository = FakeStoryRepository()
            val viewModel = studioViewModel(store, repository, NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()

            // 목록에 없는 완료 — 카드는 남고 목록을 한 번만 다시 읽는다.
            store.emitRequests(listOf(completedRequest("x", storyId = "story-new")))
            advanceUntilIdle()
            assertEquals(2, repository.myStoriesCallCount)
            assertTrue(store.deletedRequestIds.isEmpty())
            assertEquals(1, viewModel.uiState.value.completionRequests.size)

            // 목록에 실린 완료 — 요청 행을 지워 중복 카드를 막는다.
            store.emitRequests(listOf(completedRequest("y", storyId = "story-1")))
            advanceUntilIdle()
            assertEquals(listOf("y"), store.deletedRequestIds)
            assertTrue(
                viewModel.uiState.value.completionRequests
                    .isEmpty(),
            )
        }

    @Test
    fun `초안 카드 삭제는 확인 뒤 초안만 지우고 실패하면 카드를 남긴다`() =
        runTest(dispatcher) {
            val store = FakeCreationProgressAccess(draft = generatingRecord(), requests = listOf(pendingRequest("a")))
            val viewModel = studioViewModel(store, FakeStoryRepository(), NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()

            store.discardSucceeds = false
            viewModel.onIntent(StudioIntent.OpenCardOptions(StudioCard.Draft))
            advanceUntilIdle()
            viewModel.onIntent(StudioIntent.RequestDelete)
            advanceUntilIdle()
            assertEquals(StudioCard.Draft, viewModel.uiState.value.deleteTarget)
            viewModel.onIntent(StudioIntent.ConfirmDelete)
            advanceUntilIdle()
            assertEquals(generatingRecord(), viewModel.uiState.value.draft)
            assertEquals(
                StudioEffect.ShowStoryDeleteFailed,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )

            store.discardSucceeds = true
            viewModel.onIntent(StudioIntent.OpenCardOptions(StudioCard.Draft))
            advanceUntilIdle()
            viewModel.onIntent(StudioIntent.RequestDelete)
            advanceUntilIdle()
            viewModel.onIntent(StudioIntent.ConfirmDelete)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.draft)
            assertNull(viewModel.uiState.value.deleteTarget)
            assertEquals(listOf(pendingRequest("a")), store.currentRequests)
        }

    @Test
    fun `실패 카드는 같은 요청으로 재시도하고 삭제는 그 요청만 지운다`() =
        runTest(dispatcher) {
            val failed = pendingRequest("f").copy(status = CompletionRequestStatus.FAILED)
            val store = FakeCreationProgressAccess(requests = listOf(failed, pendingRequest("a")))
            val viewModel = studioViewModel(store, FakeStoryRepository(), NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()

            viewModel.onIntent(StudioIntent.RetryCompletion("f"))
            advanceUntilIdle()
            assertEquals(listOf("f"), store.retriedRequestIds)

            viewModel.onIntent(StudioIntent.OpenCardOptions(StudioCard.FailedRequest("f")))
            advanceUntilIdle()
            viewModel.onIntent(StudioIntent.RequestDelete)
            advanceUntilIdle()
            viewModel.onIntent(StudioIntent.ConfirmDelete)
            advanceUntilIdle()
            assertEquals(listOf("f"), store.deletedRequestIds)
            assertEquals(listOf(pendingRequest("a")), store.currentRequests)
        }
}

private fun generatingRecord(): CreationProgressSummary =
    CreationProgressSummary(CreationStage.STORYLINE_GENERATION, CreationResumePoint.StorylineStep)

private fun draftRecord(selectedIndex: Int): CreationProgressSummary =
    CreationProgressSummary(CreationStage.STORY_DRAFT, CreationResumePoint.AdditionalInfoStep(selectedIndex))

private fun pendingRequest(id: String): CompletionRequestSummary =
    CompletionRequestSummary(requestId = id, submittedAt = 1L, status = CompletionRequestStatus.PENDING)

private fun completedRequest(
    id: String,
    storyId: String,
): CompletionRequestSummary =
    CompletionRequestSummary(
        requestId = id,
        submittedAt = 1L,
        status = CompletionRequestStatus.COMPLETED,
        storyId = storyId,
        storyTitle = "완성",
    )

private fun studioViewModel(
    store: CreationProgressAccess,
    repository: FakeStoryRepository,
    analytics: app.manyak.analytics.domain.Analytics,
): StudioViewModel = StudioViewModel(store, repository, analytics, repository)
