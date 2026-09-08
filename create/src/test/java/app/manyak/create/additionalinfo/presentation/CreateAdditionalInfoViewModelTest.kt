package app.manyak.create.additionalinfo.presentation

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.create.entity.CreationProgress
import app.manyak.create.entity.PendingStoryCreation
import app.manyak.create.presentation.state.FunnelExitWarning
import app.manyak.create.presentation.state.StorylineGenerationState
import app.manyak.create.presentation.state.StorylineGenerationStore
import app.manyak.create.testing.FakePendingStoryCreationStore
import app.manyak.create.testing.FakeStoryCompletionSubmitter
import app.manyak.create.testing.FakeStoryCreationRepository
import app.manyak.create.testing.sampleGenerationInput
import app.manyak.create.testing.sampleStorylineGeneration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class CreateAdditionalInfoViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): CreateAdditionalInfoViewModel {
        val repository = FakeStoryCreationRepository()
        val pendingStore = FakePendingStoryCreationStore()
        return CreateAdditionalInfoViewModel(
            StorylineGenerationStore(repository, pendingStore, FakeStoryCompletionSubmitter(), this),
            NoOpAnalytics,
        )
    }

    private class LoadedFixture(
        val submitter: FakeStoryCompletionSubmitter,
        val pendingStore: FakePendingStoryCreationStore,
        val store: StorylineGenerationStore,
        val viewModel: CreateAdditionalInfoViewModel,
    )

    /** 스토리라인 생성 성공 결과를 스냅숏한 ViewModel 을 만든다. */
    private fun TestScope.loadedViewModel(
        selectedStorylineIndex: Int? = null,
        submitter: FakeStoryCompletionSubmitter = FakeStoryCompletionSubmitter(),
    ): LoadedFixture {
        val repository = FakeStoryCreationRepository()
        val pendingStore = FakePendingStoryCreationStore()
        val store = StorylineGenerationStore(repository, pendingStore, submitter, this)
        store.generate(sampleGenerationInput())
        advanceUntilIdle()
        // "선택하기"로 추가 정보 단계에 들어온 상태를 만든다. 재개 지점이 이 값으로 갈린다.
        selectedStorylineIndex?.let(store::markStorylineSelected)
        return LoadedFixture(
            submitter = submitter,
            pendingStore = pendingStore,
            store = store,
            viewModel = CreateAdditionalInfoViewModel(store, NoOpAnalytics),
        )
    }

    @Test
    fun `생성 결과의 본문과 추천 추가 정보가 화면 상태로 스냅숏된다`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel().viewModel

            assertEquals(10L, viewModel.uiState.value.simpleCreationId)
            assertEquals(
                listOf("첫 번째 스토리라인", "두 번째 스토리라인", "세 번째 스토리라인"),
                viewModel.uiState.value.storylines
                    .map { it.text },
            )
            assertEquals(
                listOf("폐허를 자세히 그려줘"),
                viewModel.uiState.value.storylines
                    .first()
                    .recommendedInfos,
            )
        }

    @Test
    fun `스토리 완성 요청은 추천 채택분을 앞세우고 빈 자유 입력을 뺀다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel()
            val viewModel = fixture.viewModel

            viewModel.onIntent(CreateAdditionalInfoIntent.ToggleRecommendation("폐허를 자세히 그려줘"))
            advanceUntilIdle()
            val inputId =
                viewModel.uiState.value.additionalInfos
                    .first()
                    .id
            viewModel.onIntent(CreateAdditionalInfoIntent.ChangeInput(inputId, " 배경은 현대의 서울로 해줘 "))
            advanceUntilIdle()
            viewModel.onIntent(CreateAdditionalInfoIntent.CompleteStory(storylineIndex = 0))
            advanceUntilIdle()

            val command =
                fixture.submitter.submitted
                    .single()
                    .command
            assertEquals(10L, command.simpleCreationId)
            assertEquals(1L, command.storylineId)
            assertEquals(listOf("폐허를 자세히 그려줘", "배경은 현대의 서울로 해줘"), command.additionalInfos)
            // 영속에 성공하면 서버 응답을 기다리지 않고 제작 탭으로 돌아간다.
            assertEquals(
                CreateAdditionalInfoEffect.ReturnToStudioAfterSubmission,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
            assertEquals(StorylineGenerationState.Idle, fixture.store.state.value)
        }

    @Test
    fun `제출 연타는 요청 하나로 처리한다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel()

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.CompleteStory(storylineIndex = 0))
            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.CompleteStory(storylineIndex = 0))
            advanceUntilIdle()

            assertEquals(1, fixture.submitter.submitted.size)
        }

    @Test
    fun `영속 실패는 전송하지 않고 입력을 유지한 채 실패를 알린다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel(submitter = FakeStoryCompletionSubmitter(submitSucceeds = false))
            val viewModel = fixture.viewModel
            val inputId =
                viewModel.uiState.value.additionalInfos
                    .first()
                    .id
            viewModel.onIntent(CreateAdditionalInfoIntent.ChangeInput(inputId, "배경은 서울"))
            advanceUntilIdle()

            viewModel.onIntent(CreateAdditionalInfoIntent.CompleteStory(storylineIndex = 0))
            advanceUntilIdle()

            assertTrue(fixture.submitter.submitted.isEmpty())
            assertFalse(viewModel.uiState.value.isSubmitting)
            assertEquals(
                "배경은 서울",
                viewModel.uiState.value.additionalInfos
                    .first()
                    .value,
            )
            assertEquals(
                CreateAdditionalInfoEffect.ShowSubmissionFailure,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
            assertTrue(fixture.store.state.value is StorylineGenerationState.Generated)

            // 같은 페이로드 재시도는 요청 ID 를 재사용한다.
            val firstRequestId = fixture.store.lastCompletionCommand?.requestId
            fixture.submitter.submitSucceeds = true
            viewModel.onIntent(CreateAdditionalInfoIntent.CompleteStory(storylineIndex = 0))
            advanceUntilIdle()
            assertEquals(
                firstRequestId,
                fixture.submitter.submitted
                    .single()
                    .requestId,
            )
        }

    @Test
    fun `추천 추가 정보는 다시 누르면 해제된다`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.onIntent(CreateAdditionalInfoIntent.ToggleRecommendation("배경을 자세히 그려줘"))
            advanceUntilIdle()
            assertEquals(setOf("배경을 자세히 그려줘"), viewModel.uiState.value.selectedRecommendations)

            viewModel.onIntent(CreateAdditionalInfoIntent.ToggleRecommendation("배경을 자세히 그려줘"))
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.selectedRecommendations
                    .isEmpty(),
            )
        }

    @Test
    fun `추가 정보 입력은 모아 두었다가 임시 저장에서 한 번에 나간다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel(selectedStorylineIndex = 0)
            advanceUntilIdle()
            val inputId =
                fixture.viewModel.uiState.value.additionalInfos
                    .first()
                    .id

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ChangeInput(inputId, "배경은 서울"))
            advanceUntilIdle()

            assertTrue(fixture.store.draftSave.value.hasUnsavedChanges)
            assertTrue(
                (fixture.pendingStore.current as PendingStoryCreation.Draft)
                    .progress.additionalInfoInputs
                    .none { it == "배경은 서울" },
            )

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.SaveDraft)
            advanceUntilIdle()

            val draft = fixture.pendingStore.current as PendingStoryCreation.Draft
            assertEquals("배경은 서울", draft.progress.additionalInfoInputs.first())
            assertFalse(fixture.store.draftSave.value.hasUnsavedChanges)
        }

    @Test
    fun `저장하지 않은 추가 정보가 있으면 이탈 전에 경고한다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel(selectedStorylineIndex = 0)
            advanceUntilIdle()
            val inputId =
                fixture.viewModel.uiState.value.additionalInfos
                    .first()
                    .id
            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ChangeInput(inputId, "배경은 서울"))
            advanceUntilIdle()

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.LeaveFunnel)
            advanceUntilIdle()

            assertEquals(FunnelExitWarning.UNSAVED_CHANGES, fixture.viewModel.uiState.value.exitWarning)

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ConfirmLeaveFunnel)
            advanceUntilIdle()

            val draft = fixture.pendingStore.current as PendingStoryCreation.Draft
            assertTrue(draft.progress.additionalInfoInputs.none { it == "배경은 서울" })
        }

    @Test
    fun `입력은 상한까지만 추가되고 삭제한 입력은 목록에서 빠진다`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            repeat(CreateAdditionalInfoUiState.INPUT_MAX_COUNT) {
                viewModel.onIntent(CreateAdditionalInfoIntent.AddInput)
                advanceUntilIdle()
            }
            assertEquals(
                CreateAdditionalInfoUiState.INPUT_MAX_COUNT,
                viewModel.uiState.value.additionalInfos.size,
            )
            assertFalse(viewModel.uiState.value.canAddInput)

            val firstId =
                viewModel.uiState.value.additionalInfos
                    .first()
                    .id
            viewModel.onIntent(CreateAdditionalInfoIntent.RemoveInput(firstId))
            advanceUntilIdle()

            assertEquals(
                CreateAdditionalInfoUiState.INPUT_MAX_COUNT - 1,
                viewModel.uiState.value.additionalInfos.size,
            )
            assertTrue(
                viewModel.uiState.value.additionalInfos
                    .none { it.id == firstId },
            )
        }

    @Test
    fun `임시 저장본 재개는 스토어 복원 스냅숏으로 입력과 추천 선택을 되살린다`() =
        runTest(dispatcher) {
            val repository = FakeStoryCreationRepository()
            val pendingStore =
                FakePendingStoryCreationStore(
                    initial =
                        PendingStoryCreation.Draft(
                            generationCommand = null,
                            generation = sampleStorylineGeneration(),
                            progress =
                                CreationProgress(
                                    selectedStorylineIndex = 0,
                                    activeStorylineIndex = 0,
                                    additionalInfoInputs = listOf("배경은 서울", ""),
                                    selectedRecommendations = listOf("폐허를 자세히 그려줘", "사라진 추천"),
                                ),
                        ),
                )
            val store = StorylineGenerationStore(repository, pendingStore, FakeStoryCompletionSubmitter(), this)
            val viewModel =
                CreateAdditionalInfoViewModel(store, NoOpAnalytics)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(10L, state.simpleCreationId)
            assertEquals(listOf("배경은 서울", ""), state.additionalInfos.map { it.value })
            // 현재 생성 결과에 없는 추천 선택은 복원하지 않는다 — 완성 요청에 섞여 실리지 않게 한다.
            assertEquals(setOf("폐허를 자세히 그려줘"), state.selectedRecommendations)
        }

    @Test
    fun `재개 진입은 복원이 끝나기 전에 입력 화면을 그리지 않는다`() =
        runTest(dispatcher) {
            val repository = FakeStoryCreationRepository()
            val pendingStore =
                FakePendingStoryCreationStore(
                    initial =
                        PendingStoryCreation.Draft(
                            generationCommand = null,
                            generation = sampleStorylineGeneration(),
                            progress = CreationProgress(selectedStorylineIndex = 0),
                        ),
                )
            val store = StorylineGenerationStore(repository, pendingStore, FakeStoryCompletionSubmitter(), this)

            val viewModel =
                CreateAdditionalInfoViewModel(store, NoOpAnalytics)

            // 복원 결과가 오기 전 첫 프레임. 여기서 입력 화면을 그리면 본문 없는 화면이 스쳐 지나간다.
            assertTrue(viewModel.uiState.value.isRestoring)

            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isRestoring)
            assertEquals(3, viewModel.uiState.value.storylines.size)
        }

    @Test
    fun `스토리라인 단계에서 넘어온 진입은 첫 프레임부터 입력 화면이다`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel().viewModel

            assertFalse(viewModel.uiState.value.isRestoring)
        }

    @Test
    fun `복원을 기다리는 동안에는 빈 입력이 임시 저장 재료로 미러링되지 않는다`() =
        runTest(dispatcher) {
            val repository = FakeStoryCreationRepository()
            val inputs = listOf("배경은 서울", "")
            val pendingStore =
                FakePendingStoryCreationStore(
                    initial =
                        PendingStoryCreation.Draft(
                            generationCommand = null,
                            generation = sampleStorylineGeneration(),
                            progress =
                                CreationProgress(
                                    selectedStorylineIndex = 0,
                                    additionalInfoInputs = inputs,
                                ),
                        ),
                )
            val store = StorylineGenerationStore(repository, pendingStore, FakeStoryCompletionSubmitter(), this)

            CreateAdditionalInfoViewModel(store, NoOpAnalytics)
            advanceUntilIdle()

            assertEquals(inputs, store.progress.additionalInfoInputs)
        }

    @Test
    fun `입력값은 최대 길이까지만 반영된다`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val inputId =
                viewModel.uiState.value.additionalInfos
                    .first()
                    .id
            val overflow = "가".repeat(CreateAdditionalInfoUiState.INPUT_MAX_LENGTH + 10)

            viewModel.onIntent(CreateAdditionalInfoIntent.ChangeInput(inputId, overflow))
            advanceUntilIdle()

            assertEquals(
                CreateAdditionalInfoUiState.INPUT_MAX_LENGTH,
                viewModel.uiState.value.additionalInfos
                    .first()
                    .value.length,
            )
        }

    @Test
    fun `임시 저장하면 선택 순번과 입력이 담긴 저장본이 남는다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel(selectedStorylineIndex = 1)
            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ChangeInput(inputId = 0, value = "배경은 서울"))
            advanceUntilIdle()

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.SaveDraft)
            advanceUntilIdle()

            val record = fixture.pendingStore.read() as PendingStoryCreation.Draft
            assertEquals(1, record.progress.selectedStorylineIndex)
            assertEquals(listOf("배경은 서울", "", ""), record.progress.additionalInfoInputs)
        }

    @Test
    fun `저장한 뒤 이탈하면 닫기 확인만 거쳐 이탈 효과를 낸다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel(selectedStorylineIndex = 0)
            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.SaveDraft)
            advanceUntilIdle()

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.LeaveFunnel)
            advanceUntilIdle()
            assertEquals(FunnelExitWarning.SAVED_DRAFT, fixture.viewModel.uiState.value.exitWarning)
            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ConfirmLeaveFunnel)
            advanceUntilIdle()

            assertNull(fixture.viewModel.uiState.value.exitWarning)
            assertEquals(
                CreateAdditionalInfoEffect.ExitFunnel,
                fixture.viewModel.uiEffect.first(),
            )
        }

    @Test
    fun `추가 정보가 없으면 다시 선택하기는 곧바로 돌아간다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel(selectedStorylineIndex = 0)

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ReselectStoryline)
            advanceUntilIdle()

            assertFalse(fixture.viewModel.uiState.value.showReselectWarningDialog)
            assertEquals(
                CreateAdditionalInfoEffect.NavigateBackToStoryline,
                fixture.viewModel.uiEffect.first(),
            )
        }

    @Test
    fun `입력이 있으면 다시 선택하기는 초기화 경고를 띄운다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel(selectedStorylineIndex = 0)
            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ChangeInput(inputId = 0, value = "배경은 서울"))
            advanceUntilIdle()

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ReselectStoryline)
            advanceUntilIdle()

            assertTrue(fixture.viewModel.uiState.value.showReselectWarningDialog)
            assertNull(withTimeoutOrNull(100) { fixture.viewModel.uiEffect.first() })
        }

    @Test
    fun `추천만 골라도 다시 선택하기는 초기화 경고를 띄운다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel(selectedStorylineIndex = 0)
            val recommendation =
                fixture.viewModel.uiState.value.storylines
                    .first()
                    .recommendedInfos
                    .first()
            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ToggleRecommendation(recommendation))
            advanceUntilIdle()

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ReselectStoryline)
            advanceUntilIdle()

            assertTrue(fixture.viewModel.uiState.value.showReselectWarningDialog)
        }

    @Test
    fun `초기화를 확정하면 선택 순번이 사라져 다음 이탈은 스토리라인 단계로 재개한다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel(selectedStorylineIndex = 0)
            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ChangeInput(inputId = 0, value = "배경은 서울"))
            advanceUntilIdle()

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ReselectStoryline)
            advanceUntilIdle()
            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ConfirmReselect)
            advanceUntilIdle()

            assertNull(fixture.store.progress.selectedStorylineIndex)
            assertEquals(emptyList<String>(), fixture.store.progress.additionalInfoInputs)
            assertEquals(emptyList<String>(), fixture.store.progress.selectedRecommendations)
            assertEquals(
                CreateAdditionalInfoEffect.NavigateBackToStoryline,
                fixture.viewModel.uiEffect.first(),
            )
        }

    @Test
    fun `초기화를 취소하면 입력이 남는다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel(selectedStorylineIndex = 0)
            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ChangeInput(inputId = 0, value = "배경은 서울"))
            advanceUntilIdle()

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ReselectStoryline)
            advanceUntilIdle()
            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.DismissReselectWarning)
            advanceUntilIdle()

            assertFalse(fixture.viewModel.uiState.value.showReselectWarningDialog)
            assertEquals(
                "배경은 서울",
                fixture.viewModel.uiState.value.additionalInfos
                    .first()
                    .value,
            )
            assertEquals(0, fixture.store.progress.selectedStorylineIndex)
        }
}
