package app.manyak.feature.studio

import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.CreationProgress
import app.manyak.core.domain.story.CreationResumePoint
import app.manyak.core.domain.story.PendingStoryCreation
import app.manyak.core.domain.story.PendingStoryCreationStore
import app.manyak.core.domain.story.StoryCharacterInput
import app.manyak.core.domain.story.StoryCompletionCommand
import app.manyak.core.domain.story.Storyline
import app.manyak.core.domain.story.StorylineGeneration
import app.manyak.core.domain.story.StorylineGenerationCommand
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
            val viewModel = StudioViewModel(FakePendingStoryCreationStore(), FakeStoryRepository())
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
            val viewModel = StudioViewModel(store, FakeStoryRepository())
            advanceUntilIdle()

            val banner = viewModel.uiState.value.pendingBanner
            assertTrue(banner?.isCompleting == true)
            assertEquals(CreationResumePoint.AdditionalInfoStep(storylineIndex = 2), banner?.resumePoint)
        }

    @Test
    fun `레코드가 있는 FAB 진입은 다이얼로그로 묻고 새로 만들기는 폐기 후 진입한다`() =
        runTest(dispatcher) {
            val store = FakePendingStoryCreationStore(initial = generatingRecord())
            val viewModel = StudioViewModel(store, FakeStoryRepository())
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
            val viewModel = StudioViewModel(store, FakeStoryRepository())
            advanceUntilIdle()

            viewModel.onIntent(StudioIntent.ResumeCreation)
            advanceUntilIdle()

            assertEquals(
                StudioEffect.NavigateToResume(CreationResumePoint.StorylineStep),
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `진입 시 내 스토리를 조회해 목록 상태가 된다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val viewModel = StudioViewModel(FakePendingStoryCreationStore(), repository)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.loadFailed)
            assertEquals(sampleStories(), state.stories)
            assertEquals(1, repository.myStoriesCallCount)
        }

    @Test
    fun `조회 실패는 실패 상태가 되고 재시도가 다시 조회한다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            repository.queuedResults.add(DomainResult.Failure(DomainError.Network))
            val viewModel = StudioViewModel(FakePendingStoryCreationStore(), repository)
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
}

private class FakePendingStoryCreationStore(
    initial: PendingStoryCreation? = null,
) : PendingStoryCreationStore {
    private val state = MutableStateFlow(initial)

    override val record: Flow<PendingStoryCreation?> = state

    val current: PendingStoryCreation? get() = state.value

    override suspend fun read(): PendingStoryCreation? = state.value

    override suspend fun write(record: PendingStoryCreation): Boolean {
        state.value = record
        return true
    }

    override suspend fun clear(): Boolean {
        state.value = null
        return true
    }
}

private fun generatingRecord(): PendingStoryCreation =
    PendingStoryCreation.GeneratingStorylines(
        command =
            StorylineGenerationCommand(
                requestId = "request-1",
                genreTagIds = listOf(1),
                customGenreTags = emptyList(),
                protagonist =
                    StoryCharacterInput(
                        name = null,
                        gender = null,
                        featureTagIds = emptyList(),
                        customTags = emptyList(),
                    ),
                supportingCharacters = emptyList(),
                parentCreationId = null,
                isRegenerated = false,
            ),
    )

private fun completingRecord(selectedIndex: Int): PendingStoryCreation =
    PendingStoryCreation.CompletingStory(
        generationCommand = null,
        generation =
            StorylineGeneration(
                simpleCreationId = 10,
                storylines = listOf(Storyline(id = 1, storyline = "스토리라인", recommendedInfos = emptyList())),
            ),
        command =
            StoryCompletionCommand(
                requestId = "request-2",
                simpleCreationId = 10,
                storylineId = 1,
                additionalInfos = emptyList(),
            ),
        progress = CreationProgress(selectedStorylineIndex = selectedIndex),
    )
