package app.manyak.story.detail.presentation

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.story.StoryLikeUpdates
import app.manyak.story.testing.FakeChatRepository
import app.manyak.story.testing.FakePersonaAccess
import app.manyak.story.testing.FakeStoryRepository
import app.manyak.story.testing.STORY_ID
import app.manyak.story.testing.sampleStoryDetail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoryDetailLikeTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        storyRepository: FakeStoryRepository = FakeStoryRepository(),
        chatRepository: FakeChatRepository = FakeChatRepository(),
        likeUpdates: StoryLikeUpdates = StoryLikeUpdates { _, _ -> },
    ) = StoryDetailViewModel(
        STORY_ID,
        storyRepository,
        chatRepository,
        NoOpAnalytics,
        storyRepository,
        storyRepository,
        likeUpdates,
        FakePersonaAccess(),
    )

    @Test
    fun `좋아요는 응답 전에 반영하고 실패하면 정확한 이전 값으로 복원한다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            repository.queuedDetailResults +=
                DomainResult.Success(sampleStoryDetail(likeCount = 0, isLiked = true, isOwner = false))
            repository.queuedDetailResults +=
                DomainResult.Success(sampleStoryDetail(likeCount = 0, isLiked = true, isOwner = false))
            repository.queuedLikeResults += DomainResult.Failure(DomainError.Network)
            val gate = CompletableDeferred<Unit>()
            repository.likeGate = gate
            val updates = mutableListOf<Pair<String, Long>>()
            val viewModel =
                viewModel(
                    repository,
                    likeUpdates =
                        StoryLikeUpdates {
                            id,
                            count,
                            ->
                            updates += id to count
                        },
                )
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            viewModel.onIntent(StoryDetailIntent.ToggleLike)
            runCurrent()
            assertFalse(
                viewModel.uiState.value.story!!
                    .isLiked,
            )
            assertEquals(
                0L,
                viewModel.uiState.value.story!!
                    .likeCount,
            )
            assertTrue(viewModel.uiState.value.isTogglingLike)
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            viewModel.onIntent(StoryDetailIntent.ToggleLike)
            runCurrent()
            assertEquals(1, repository.storyDetailCallCount)
            assertEquals(1, repository.likeRequests.size)
            gate.complete(Unit)
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.story!!
                    .isLiked,
            )
            assertEquals(
                0L,
                viewModel.uiState.value.story!!
                    .likeCount,
            )
            assertFalse(viewModel.uiState.value.isTogglingLike)
            assertTrue(updates.isEmpty())
            assertEquals(2, repository.storyDetailCallCount)
            assertEquals(StoryDetailEffect.ShowLikeFailed, viewModel.uiEffect.first())
        }

    @Test
    fun `좋아요 성공만 목록으로 전달하고 진행 중 재조회를 취소한다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            repository.queuedDetailResults += DomainResult.Success(sampleStoryDetail(isOwner = false))
            val updates = mutableListOf<Pair<String, Long>>()
            val viewModel =
                viewModel(
                    repository,
                    likeUpdates =
                        StoryLikeUpdates {
                            id,
                            count,
                            ->
                            updates += id to count
                        },
                )
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            val loadGate = CompletableDeferred<Unit>()
            repository.inFlightGate = loadGate
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            runCurrent()
            val likeGate = CompletableDeferred<Unit>()
            repository.likeGate = likeGate
            viewModel.onIntent(StoryDetailIntent.ToggleLike)
            runCurrent()
            assertTrue(
                viewModel.uiState.value.story!!
                    .isLiked,
            )
            assertEquals(
                13L,
                viewModel.uiState.value.story!!
                    .likeCount,
            )
            assertTrue(updates.isEmpty())
            loadGate.complete(Unit)
            runCurrent()
            assertEquals(
                13L,
                viewModel.uiState.value.story!!
                    .likeCount,
            )
            likeGate.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(STORY_ID to 13L), updates)
        }

    @Test
    fun `상세 조회와 복귀는 서버 좋아요 값을 보존하고 토글을 보내지 않는다`() =
        runTest(dispatcher) {
            val storyRepository = FakeStoryRepository()
            storyRepository.queuedDetailResults +=
                DomainResult.Success(sampleStoryDetail(likeCount = 12, isLiked = true, isOwner = false))
            val viewModel = viewModel(storyRepository = storyRepository)

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            val loaded = viewModel.uiState.value
            assertEquals(12L, loaded.story?.likeCount)
            assertTrue(loaded.story?.isLiked == true)

            storyRepository.queuedDetailResults +=
                DomainResult.Success(sampleStoryDetail(likeCount = 15, isLiked = false, isOwner = false))
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            val refreshed = viewModel.uiState.value
            assertEquals(15L, refreshed.story?.likeCount)
            assertFalse(refreshed.story?.isLiked == true)
            assertTrue(storyRepository.likeRequests.isEmpty())
        }

    @Test
    fun `좋아요를 누르면 등록을 보내고 성공 뒤 상태와 수를 한 칸 옮긴다`() =
        runTest(dispatcher) {
            val storyRepository = FakeStoryRepository()
            storyRepository.queuedDetailResults +=
                DomainResult.Success(sampleStoryDetail(likeCount = 12, isLiked = false, isOwner = false))
            val viewModel = viewModel(storyRepository = storyRepository)
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            viewModel.onIntent(StoryDetailIntent.ToggleLike)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(STORY_ID to true), storyRepository.likeRequests)
            assertTrue(state.story?.isLiked == true)
            assertEquals(13L, state.story?.likeCount)
            assertFalse(state.isTogglingLike)
        }

    @Test
    fun `이미 누른 좋아요는 취소를 보내고 수를 되돌린다`() =
        runTest(dispatcher) {
            val storyRepository = FakeStoryRepository()
            storyRepository.queuedDetailResults +=
                DomainResult.Success(sampleStoryDetail(likeCount = 12, isLiked = true, isOwner = false))
            val viewModel = viewModel(storyRepository = storyRepository)
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            viewModel.onIntent(StoryDetailIntent.ToggleLike)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(STORY_ID to false), storyRepository.likeRequests)
            assertFalse(state.story?.isLiked == true)
            assertEquals(11L, state.story?.likeCount)
        }

    @Test
    fun `좋아요 실패는 상태와 수를 그대로 두고 실패를 알린다`() =
        runTest(dispatcher) {
            val storyRepository = FakeStoryRepository()
            storyRepository.queuedDetailResults +=
                DomainResult.Success(sampleStoryDetail(likeCount = 12, isLiked = false, isOwner = false))
            storyRepository.queuedLikeResults += DomainResult.Failure(DomainError.Network)
            val viewModel = viewModel(storyRepository = storyRepository)
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            viewModel.onIntent(StoryDetailIntent.ToggleLike)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.story?.isLiked == true)
            assertEquals(12L, state.story?.likeCount)
            assertFalse(state.isTogglingLike)
            assertEquals(
                StoryDetailEffect.ShowLikeFailed,
                withTimeoutOrNull(TIMEOUT_MILLIS) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `내가 만든 스토리는 좋아요를 보내지 않는다`() =
        runTest(dispatcher) {
            val storyRepository = FakeStoryRepository()
            storyRepository.queuedDetailResults += DomainResult.Success(sampleStoryDetail(isOwner = true))
            val viewModel = viewModel(storyRepository = storyRepository)
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            viewModel.onIntent(StoryDetailIntent.ToggleLike)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.canLike)
            assertTrue(storyRepository.likeRequests.isEmpty())
        }

    @Test
    fun `응답 직후의 연타는 버리고 잠깐 뒤의 탭만 다시 보낸다`() =
        runTest(dispatcher) {
            val storyRepository = FakeStoryRepository()
            storyRepository.queuedDetailResults +=
                DomainResult.Success(sampleStoryDetail(likeCount = 12, isLiked = false, isOwner = false))
            val viewModel = viewModel(storyRepository = storyRepository)
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            viewModel.onIntent(StoryDetailIntent.ToggleLike)
            runCurrent()
            viewModel.onIntent(StoryDetailIntent.ToggleLike)
            advanceTimeBy(COOLDOWN_HALF_MILLIS)
            viewModel.onIntent(StoryDetailIntent.ToggleLike)
            runCurrent()
            assertEquals(listOf(STORY_ID to true), storyRepository.likeRequests)
            assertTrue(
                viewModel.uiState.value.story
                    ?.isLiked == true,
            )

            advanceUntilIdle()
            viewModel.onIntent(StoryDetailIntent.ToggleLike)
            advanceUntilIdle()
            assertEquals(listOf(STORY_ID to true, STORY_ID to false), storyRepository.likeRequests)
        }
}

private const val TIMEOUT_MILLIS = 1_000L
private const val COOLDOWN_HALF_MILLIS = 250L
