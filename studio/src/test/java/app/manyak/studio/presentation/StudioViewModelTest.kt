package app.manyak.studio.presentation

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.story.CreationProgressAccess
import app.manyak.common.entity.story.CreationProgressSummary
import app.manyak.common.entity.story.CreationResumePoint
import app.manyak.common.entity.story.CreationStage
import app.manyak.report.entity.StoryReportReason
import app.manyak.report.presentation.StoryReportAction
import app.manyak.studio.testing.FakeStoryRepository
import app.manyak.studio.testing.sampleStories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
    fun `진행 레코드가 없으면 배너 없이 바로 새 생성으로 진입한다`() =
        runTest(dispatcher) {
            val viewModel = studioViewModel(FakePendingStoryCreationStore(), FakeStoryRepository(), NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.pendingBanner)

            viewModel.onIntent(StudioIntent.CreateStory)
            advanceUntilIdle()

            assertEquals(
                StudioEffect.NavigateToCreate,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `완성 진행 레코드는 완성 중 배너와 추가 정보 재개 지점이 된다`() =
        runTest(dispatcher) {
            val store = FakePendingStoryCreationStore(initial = completingRecord(selectedIndex = 2))
            val viewModel = studioViewModel(store, FakeStoryRepository(), NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()

            val banner = viewModel.uiState.value.pendingBanner
            assertTrue(banner?.isCompleting == true)
            assertEquals(CreationResumePoint.AdditionalInfoStep(storylineIndex = 2), banner?.resumePoint)
        }

    @Test
    fun `레코드가 있는 FAB 진입은 다이얼로그로 묻고 새로 만들기는 폐기 후 진입한다`() =
        runTest(dispatcher) {
            val store = FakePendingStoryCreationStore(initial = generatingRecord())
            val viewModel = studioViewModel(store, FakeStoryRepository(), NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()

            viewModel.onIntent(StudioIntent.CreateStory)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.showResumeChoiceDialog)

            viewModel.onIntent(StudioIntent.StartNewCreation)
            advanceUntilIdle()

            assertNull(store.current)
            assertFalse(viewModel.uiState.value.showResumeChoiceDialog)
            assertEquals(
                StudioEffect.NavigateToCreate,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `이어서 만들기는 레코드 단계의 재개 지점으로 진입한다`() =
        runTest(dispatcher) {
            val store = FakePendingStoryCreationStore(initial = generatingRecord())
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
            val viewModel = studioViewModel(FakePendingStoryCreationStore(), repository, NoOpAnalytics)
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
            val viewModel = studioViewModel(FakePendingStoryCreationStore(), repository, NoOpAnalytics)
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
            val viewModel = studioViewModel(FakePendingStoryCreationStore(), repository, NoOpAnalytics)
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
            val viewModel = studioViewModel(FakePendingStoryCreationStore(), repository, NoOpAnalytics)
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
            val viewModel = studioViewModel(FakePendingStoryCreationStore(), repository, NoOpAnalytics)
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
            val viewModel = studioViewModel(FakePendingStoryCreationStore(), repository, NoOpAnalytics)
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
            val viewModel = studioViewModel(FakePendingStoryCreationStore(), repository, NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()
            val loadedState = viewModel.uiState.value
            val target = loadedState.stories.first()

            viewModel.onIntent(StudioIntent.OpenStoryOptions(target))
            advanceUntilIdle()
            viewModel.onIntent(StudioIntent.RequestDeleteStory)
            advanceUntilIdle()
            assertEquals(target, viewModel.uiState.value.deleteTarget)

            viewModel.onIntent(StudioIntent.ConfirmDeleteStory)
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
            val viewModel = studioViewModel(FakePendingStoryCreationStore(), repository, NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()
            val loadedState = viewModel.uiState.value
            val target = loadedState.stories.first()

            // 실제 화면처럼 다이얼로그가 뜬 뒤에야 확인을 누를 수 있다.
            viewModel.onIntent(StudioIntent.OpenStoryOptions(target))
            advanceUntilIdle()
            viewModel.onIntent(StudioIntent.RequestDeleteStory)
            advanceUntilIdle()
            viewModel.onIntent(StudioIntent.ConfirmDeleteStory)
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
            val viewModel = studioViewModel(FakePendingStoryCreationStore(), repository, NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()
            val loadedState = viewModel.uiState.value
            val target = loadedState.stories.first()

            viewModel.onIntent(StudioIntent.OpenStoryOptions(target))
            advanceUntilIdle()
            viewModel.onIntent(StudioIntent.RequestDeleteStory)
            viewModel.onIntent(StudioIntent.DismissDeleteDialog)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.deleteTarget)
            assertTrue(repository.deletedStoryIds.isEmpty())
        }

    @Test
    fun `옵션 시트에서 연 신고는 그 카드의 스토리로 나가고 시트를 닫는다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val viewModel = studioViewModel(FakePendingStoryCreationStore(), repository, NoOpAnalytics)
            viewModel.onIntent(StudioIntent.ScreenShown)
            advanceUntilIdle()
            val target =
                viewModel.uiState.value.stories
                    .first()

            viewModel.onIntent(StudioIntent.OpenStoryOptions(target))
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
}

private class FakePendingStoryCreationStore(
    initial: CreationProgressSummary? = null,
) : CreationProgressAccess {
    private val state = MutableStateFlow(initial)

    override val progress: Flow<CreationProgressSummary?> = state

    val current: CreationProgressSummary? get() = state.value

    override suspend fun discard(): Boolean {
        state.value = null
        return true
    }
}

private fun generatingRecord(): CreationProgressSummary =
    CreationProgressSummary(CreationStage.STORYLINE_GENERATION, CreationResumePoint.StorylineStep)

private fun completingRecord(selectedIndex: Int): CreationProgressSummary =
    CreationProgressSummary(CreationStage.STORY_COMPLETION, CreationResumePoint.AdditionalInfoStep(selectedIndex))

private fun studioViewModel(
    store: CreationProgressAccess,
    repository: FakeStoryRepository,
    analytics: app.manyak.analytics.domain.Analytics,
): StudioViewModel = StudioViewModel(store, repository, analytics, repository)
