package app.manyak.feature.create

import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.CreationProgress
import app.manyak.core.domain.story.PendingStoryCreation
import app.manyak.core.domain.story.StorylineRating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class CreateStorylineViewModelTest {
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
    fun `생성 결과가 도착하면 목록이 채워진다`() =
        runTest(dispatcher) {
            val (_, _, viewModel) = loadedViewModel()

            assertEquals(3, viewModel.uiState.value.storylines.size)
            assertFalse(viewModel.uiState.value.hasGenerationError)
            assertEquals(
                "첫 번째 스토리라인",
                viewModel.uiState.value.activeStoryline
                    ?.storyline,
            )
        }

    @Test
    fun `생성 실패는 빈 목록과 인라인 오류 상태가 된다`() =
        runTest(dispatcher) {
            val repository = FakeStoryCreationRepository()
            repository.queuedGenerationResults += DomainResult.Failure(DomainError.Network)
            val store = StorylineGenerationStore(repository, FakePendingStoryCreationStore(), this)
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            val viewModel = CreateStorylineViewModel(store, repository)
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.storylines
                    .isEmpty(),
            )
            assertTrue(viewModel.uiState.value.hasGenerationError)
        }

    @Test
    fun `같은 평가를 다시 누르면 해제되고 다른 평가를 누르면 바뀐다`() =
        runTest(dispatcher) {
            val (_, _, viewModel) = loadedViewModel()

            viewModel.onIntent(CreateStorylineIntent.ToggleRating(StorylineRating.GOOD))
            advanceUntilIdle()
            assertEquals(StorylineRating.GOOD, viewModel.uiState.value.activeRating)

            viewModel.onIntent(CreateStorylineIntent.ToggleRating(StorylineRating.BAD))
            advanceUntilIdle()
            assertEquals(StorylineRating.BAD, viewModel.uiState.value.activeRating)

            viewModel.onIntent(CreateStorylineIntent.ToggleRating(StorylineRating.BAD))
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.activeRating)
        }

    @Test
    fun `평가는 스토리라인별로 따로 보관된다`() =
        runTest(dispatcher) {
            val (_, _, viewModel) = loadedViewModel()

            viewModel.onIntent(CreateStorylineIntent.ToggleRating(StorylineRating.GOOD))
            advanceUntilIdle()
            viewModel.onIntent(CreateStorylineIntent.SelectStoryline(1))
            advanceUntilIdle()
            viewModel.onIntent(CreateStorylineIntent.ToggleRating(StorylineRating.BAD))
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.activeIndex)
            assertEquals(
                mapOf(1L to StorylineRating.GOOD, 2L to StorylineRating.BAD),
                viewModel.uiState.value.ratings,
            )
        }

    @Test
    fun `평가 연타는 디바운스 후 마지막 상태만 서버에 반영한다`() =
        runTest(dispatcher) {
            val (repository, _, viewModel) = loadedViewModel()

            viewModel.onIntent(CreateStorylineIntent.ToggleRating(StorylineRating.GOOD))
            runCurrent()
            viewModel.onIntent(CreateStorylineIntent.ToggleRating(StorylineRating.BAD))
            advanceUntilIdle()

            assertEquals(listOf(1L to StorylineRating.BAD), repository.ratingCalls)
            // 성공은 버튼 상태로 충분해 따로 안내하지 않는다.
            assertNull(withTimeoutOrNull(1_000) { viewModel.uiEffect.first() })
        }

    @Test
    fun `평가 해제는 취소 요청으로 동기화된다`() =
        runTest(dispatcher) {
            val (repository, _, viewModel) = loadedViewModel()

            viewModel.onIntent(CreateStorylineIntent.ToggleRating(StorylineRating.GOOD))
            advanceUntilIdle()
            viewModel.onIntent(CreateStorylineIntent.ToggleRating(StorylineRating.GOOD))
            advanceUntilIdle()

            assertEquals(
                listOf(1L to StorylineRating.GOOD, 1L to null),
                repository.ratingCalls,
            )
            assertNull(viewModel.uiState.value.activeRating)
        }

    @Test
    fun `평가 동기화 실패는 마지막 반영 값으로 되돌리고 실패를 알린다`() =
        runTest(dispatcher) {
            val (repository, _, viewModel) = loadedViewModel()
            repository.queuedRatingResults += DomainResult.Failure(DomainError.Network)

            viewModel.onIntent(CreateStorylineIntent.ToggleRating(StorylineRating.GOOD))
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.activeRating)
            assertEquals(
                CreateStorylineEffect.ShowRatingSyncFailed,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
            // 되돌린 값과 서버 값이 같으므로 추가 요청을 보내지 않는다.
            assertEquals(1, repository.ratingCalls.size)
        }

    @Test
    fun `디바운스 대기 중 재생성하면 평가 동기화를 보내지 않는다`() =
        runTest(dispatcher) {
            val (repository, _, viewModel) = loadedViewModel()

            viewModel.onIntent(CreateStorylineIntent.ToggleRating(StorylineRating.GOOD))
            runCurrent()
            viewModel.onIntent(CreateStorylineIntent.Regenerate)
            advanceUntilIdle()

            assertTrue(repository.ratingCalls.isEmpty())
        }

    @Test
    fun `선택하기는 활성 스토리라인 순번을 실은 추가 정보 전환 효과를 낸다`() =
        runTest(dispatcher) {
            val (_, _, viewModel) = loadedViewModel()

            viewModel.onIntent(CreateStorylineIntent.SelectStoryline(2))
            advanceUntilIdle()
            viewModel.onIntent(CreateStorylineIntent.ConfirmSelection)
            advanceUntilIdle()

            val effect = withTimeoutOrNull(1_000) { viewModel.uiEffect.first() }
            assertEquals(CreateStorylineEffect.NavigateToAdditionalInfo(storylineIndex = 2), effect)
        }

    @Test
    fun `다시 만들기는 재생성을 요청하고 새 결과에서 선택과 평가를 초기화한다`() =
        runTest(dispatcher) {
            val (repository, _, viewModel) = loadedViewModel()

            viewModel.onIntent(CreateStorylineIntent.SelectStoryline(2))
            advanceUntilIdle()
            viewModel.onIntent(CreateStorylineIntent.ToggleRating(StorylineRating.GOOD))
            advanceUntilIdle()
            viewModel.onIntent(CreateStorylineIntent.Regenerate)
            advanceUntilIdle()

            assertEquals(2, repository.generationCommands.size)
            assertTrue(repository.generationCommands[1].isRegenerated)
            assertEquals(0, viewModel.uiState.value.activeIndex)
            assertTrue(
                viewModel.uiState.value.ratings
                    .isEmpty(),
            )
        }

    @Test
    fun `재생성 실패는 직전 결과를 유지한 채 인라인 오류를 켠다`() =
        runTest(dispatcher) {
            val (repository, _, viewModel) = loadedViewModel()

            repository.queuedGenerationResults += DomainResult.Failure(DomainError.Network)
            viewModel.onIntent(CreateStorylineIntent.Regenerate)
            advanceUntilIdle()

            assertEquals(3, viewModel.uiState.value.storylines.size)
            assertTrue(viewModel.uiState.value.hasGenerationError)
        }

    @Test
    fun `저장한 결과만 남은 이탈은 경고 없이 나간다`() =
        runTest(dispatcher) {
            val repository = FakeStoryCreationRepository()
            val pendingStore = FakePendingStoryCreationStore()
            val store = StorylineGenerationStore(repository, pendingStore, this)
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            val viewModel = CreateStorylineViewModel(store, repository)
            advanceUntilIdle()

            viewModel.onIntent(CreateStorylineIntent.LeaveFunnel)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.exitWarning)
            assertTrue(pendingStore.current is PendingStoryCreation.Draft)
            assertEquals(
                CreateStorylineEffect.ExitFunnel,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `탭만 옮긴 이탈은 잃을 내용이 없어 경고 없이 나간다`() =
        runTest(dispatcher) {
            val repository = FakeStoryCreationRepository()
            val pendingStore = FakePendingStoryCreationStore()
            val store = StorylineGenerationStore(repository, pendingStore, this)
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            val viewModel = CreateStorylineViewModel(store, repository)
            advanceUntilIdle()
            viewModel.onIntent(CreateStorylineIntent.SelectStoryline(1))
            advanceUntilIdle()

            viewModel.onIntent(CreateStorylineIntent.LeaveFunnel)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.exitWarning)
            assertFalse(viewModel.draftSave.value.hasUnsavedChanges)
            assertEquals(
                CreateStorylineEffect.ExitFunnel,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `임시 저장은 지금 탭 선택을 레코드에 반영한다`() =
        runTest(dispatcher) {
            val repository = FakeStoryCreationRepository()
            val pendingStore = FakePendingStoryCreationStore()
            val store = StorylineGenerationStore(repository, pendingStore, this)
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            val viewModel = CreateStorylineViewModel(store, repository)
            advanceUntilIdle()
            viewModel.onIntent(CreateStorylineIntent.SelectStoryline(1))
            advanceUntilIdle()

            viewModel.onIntent(CreateStorylineIntent.SaveDraft)
            advanceUntilIdle()

            assertEquals(1, (pendingStore.current as PendingStoryCreation.Draft).progress.activeStorylineIndex)
            assertFalse(viewModel.draftSave.value.hasUnsavedChanges)
        }

    @Test
    fun `보존할 결과가 없는 이탈은 소실 경고 다이얼로그를 거쳐 이탈한다`() =
        runTest(dispatcher) {
            val repository = FakeStoryCreationRepository()
            // HTTP 오류는 레코드도 지워져 보존할 것이 남지 않는다.
            repository.queuedGenerationResults +=
                DomainResult.Failure(DomainError.Server(status = 502, code = null, requestId = null))
            val pendingStore = FakePendingStoryCreationStore()
            val store = StorylineGenerationStore(repository, pendingStore, this)
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            val viewModel = CreateStorylineViewModel(store, repository)
            advanceUntilIdle()

            viewModel.onIntent(CreateStorylineIntent.LeaveFunnel)
            advanceUntilIdle()
            assertEquals(FunnelExitWarning.NOTHING_TO_PRESERVE, viewModel.uiState.value.exitWarning)

            viewModel.onIntent(CreateStorylineIntent.ConfirmLeaveFunnel)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.exitWarning)
            assertNull(pendingStore.current)
            assertEquals(
                CreateStorylineEffect.ExitFunnel,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `생성 대기 중 이탈은 진행 레코드를 유지한 채 이탈한다`() =
        runTest(dispatcher) {
            val repository = FakeStoryCreationRepository()
            repository.holdGeneration = true
            val pendingStore = FakePendingStoryCreationStore()
            val store = StorylineGenerationStore(repository, pendingStore, this)
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            val viewModel = CreateStorylineViewModel(store, repository)
            advanceUntilIdle()

            viewModel.onIntent(CreateStorylineIntent.LeaveFunnel)
            advanceUntilIdle()

            assertTrue(pendingStore.current is PendingStoryCreation.GeneratingStorylines)
            assertEquals(
                CreateStorylineEffect.ExitFunnel,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `이탈 처리 중에는 스토어 초기화가 화면 상태를 되그리지 않는다`() =
        runTest(dispatcher) {
            val repository = FakeStoryCreationRepository()
            val pendingStore = FakePendingStoryCreationStore()
            val store = StorylineGenerationStore(repository, pendingStore, this)
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            val viewModel = CreateStorylineViewModel(store, repository)
            advanceUntilIdle()

            viewModel.onIntent(CreateStorylineIntent.LeaveFunnel)
            advanceUntilIdle()

            // 이탈로 스토어는 Idle 이 됐지만, pop 애니메이션 동안 나가는 화면이 빈 실패 상태로
            // 번쩍이지 않도록 마지막 콘텐츠를 유지한다.
            assertEquals(3, viewModel.uiState.value.storylines.size)
            assertFalse(viewModel.uiState.value.hasGenerationError)
        }

    @Test
    fun `재개 진입은 복원이 끝나기 전에 생성 중 화면을 그리지 않는다`() =
        runTest(dispatcher) {
            val repository = FakeStoryCreationRepository()
            val pendingStore =
                FakePendingStoryCreationStore(
                    initial =
                        PendingStoryCreation.Draft(
                            generationCommand = null,
                            generation = sampleStorylineGeneration(),
                            progress = CreationProgress(),
                        ),
                )
            val store = StorylineGenerationStore(repository, pendingStore, this)

            val viewModel = CreateStorylineViewModel(store, repository)

            // 복원 결과가 오기 전 첫 프레임. 여기서 로딩을 확정해 그리면 재개 진입 때마다
            // "스토리라인을 만들고 있어요"가 스쳐 지나간다.
            assertEquals(StorylineContent.Restoring, viewModel.uiState.value.content)

            advanceUntilIdle()
            assertEquals(3, viewModel.uiState.value.storylines.size)
        }

    @Test
    fun `키워드 단계에서 넘어온 진입은 첫 프레임부터 생성 중이다`() =
        runTest(dispatcher) {
            val repository = FakeStoryCreationRepository()
            val store = StorylineGenerationStore(repository, FakePendingStoryCreationStore(), this)
            // 키워드 화면이 생성을 시작한 직후 — 아직 응답 전이라 스토어는 생성 중이다.
            store.generate(sampleGenerationInput())

            val viewModel = CreateStorylineViewModel(store, repository)

            assertEquals(StorylineContent.Generating, viewModel.uiState.value.content)
        }

    /** 생성 성공까지 끝낸 스토어를 관찰하는 ViewModel 을 만든다. */
    private suspend fun TestScope.loadedViewModel(): Triple<
        FakeStoryCreationRepository,
        StorylineGenerationStore,
        CreateStorylineViewModel,
    > {
        val repository = FakeStoryCreationRepository()
        val store = StorylineGenerationStore(repository, FakePendingStoryCreationStore(), this)
        store.generate(sampleGenerationInput())
        advanceUntilIdle()
        val viewModel = CreateStorylineViewModel(store, repository)
        advanceUntilIdle()
        return Triple(repository, store, viewModel)
    }
}
