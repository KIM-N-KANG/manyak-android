package app.manyak.feature.create

import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.CharacterGender
import app.manyak.core.domain.story.KeywordCharacterSnapshot
import app.manyak.core.domain.story.KeywordCustomTagSnapshot
import app.manyak.core.domain.story.KeywordDraftSnapshot
import app.manyak.core.domain.story.PendingStoryCreation
import app.manyak.core.domain.story.StoryCharacterInput
import app.manyak.core.domain.story.StoryCreationRepository
import app.manyak.core.domain.story.StoryTag
import app.manyak.core.domain.story.StoryTagCategory
import app.manyak.core.domain.story.StorylineGenerationCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class CreateKeywordViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(
        repository: StoryCreationRepository,
        pending: FakePendingStoryCreationStore = FakePendingStoryCreationStore(),
    ): CreateKeywordViewModel =
        CreateKeywordViewModel(
            storyCreationRepository = repository,
            storylineGenerationStore = StorylineGenerationStore(repository, pending, this),
            pendingCreationStore = pending,
        )

    @Test
    fun `태그 조회 실패 후 다시 불러오면 태그를 재조회한다`() =
        runTest(dispatcher) {
            val loadedTag = StoryTag(id = 1, name = "로맨스", category = StoryTagCategory.GENRE)
            val repository = SequencedStoryCreationRepository(loadedTag)
            val viewModel = viewModel(repository)

            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.providedTags is ProvidedTags.Failed)

            viewModel.onIntent(CreateKeywordIntent.RetryTags)
            advanceUntilIdle()

            assertEquals(2, repository.requestCount)
            assertEquals(
                listOf(loadedTag),
                (viewModel.uiState.value.providedTags as ProvidedTags.Loaded)
                    .byCategory
                    .getValue(StoryTagCategory.GENRE),
            )
        }

    @Test
    fun `검증을 통과한 스토리라인 만들기는 단계 전환 효과를 낸다`() =
        runTest(dispatcher) {
            val viewModel = viewModel(fixedTagsRepository())

            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Genre, tagId = 1))
            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Protagonist, tagId = 2))
            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.GenerateStorylines)
            advanceUntilIdle()

            val effect = withTimeoutOrNull(1_000) { viewModel.uiEffect.first() }
            assertEquals(CreateKeywordEffect.NavigateToStoryline, effect)
        }

    @Test
    fun `검증에 실패한 스토리라인 만들기는 단계 전환 효과를 내지 않는다`() =
        runTest(dispatcher) {
            val viewModel = viewModel(fixedTagsRepository())

            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.GenerateStorylines)
            advanceUntilIdle()

            assertNull(withTimeoutOrNull(1_000) { viewModel.uiEffect.first() })
            assertTrue(viewModel.uiState.value.hasAttemptedGenerate)
        }

    @Test
    fun `스토리라인 만들기는 키워드 입력을 생성 명령으로 조립해 요청한다`() =
        runTest(dispatcher) {
            val repository = fixedTagsRepository()
            val viewModel = viewModel(repository)

            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Genre, tagId = 1))
            viewModel.onIntent(CreateKeywordIntent.AddCustomTag(KeywordTarget.Genre, name = "타임루프"))
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Protagonist, tagId = 2))
            viewModel.onIntent(
                CreateKeywordIntent.ChangeCharacterName(KeywordTarget.Protagonist, name = " 지우 "),
            )
            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.GenerateStorylines)
            advanceUntilIdle()

            val command = repository.generationCommands.single()
            assertEquals(listOf(1L), command.genreTagIds)
            assertEquals(listOf("타임루프"), command.customGenreTags)
            assertEquals("지우", command.protagonist.name)
            assertEquals(listOf(2L), command.protagonist.featureTagIds)
            // 퍼널 진입 시 놓인 빈 주변 인물 섹션은 요청에서 빠진다.
            assertTrue(command.supportingCharacters.isEmpty())
            assertNull(command.parentCreationId)
            assertFalse(command.isRegenerated)
            // 이 화면은 스토리라인 목적지로 대체되어 사라지므로 진행 플래그는 해제되지 않는다.
            assertTrue(viewModel.uiState.value.isGeneratingStorylines)
        }

    @Test
    fun `생성 요청은 한 번만 시작되고 실패 처리는 스토리라인 화면이 담당한다`() =
        runTest(dispatcher) {
            val repository = fixedTagsRepository()
            repository.queuedGenerationResults += DomainResult.Failure(DomainError.Network)
            val viewModel = viewModel(repository)

            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Genre, tagId = 1))
            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Protagonist, tagId = 2))
            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.GenerateStorylines)
            viewModel.onIntent(CreateKeywordIntent.GenerateStorylines)
            advanceUntilIdle()

            // 진행 플래그가 중복 시작을 막고, 실패 상태 표시는 스토리라인 화면 몫이다.
            assertTrue(viewModel.uiState.value.isGeneratingStorylines)
            assertEquals(1, repository.generationCommands.size)
        }

    @Test
    fun `퍼널 이탈은 스토어를 정리하고 이탈 효과를 낸다`() =
        runTest(dispatcher) {
            val repository = fixedTagsRepository()
            val pendingStore = FakePendingStoryCreationStore()
            val store = StorylineGenerationStore(repository, pendingStore, this)
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            val viewModel =
                CreateKeywordViewModel(
                    storyCreationRepository = repository,
                    storylineGenerationStore = store,
                    pendingCreationStore = pendingStore,
                )

            viewModel.onIntent(CreateKeywordIntent.LeaveFunnel)
            advanceUntilIdle()
            assertEquals(FunnelExitWarning.SAVED_DRAFT, viewModel.uiState.value.exitWarning)
            viewModel.onIntent(CreateKeywordIntent.ConfirmLeaveFunnel)
            advanceUntilIdle()

            // 생성 성공 시점에 이미 저장된 초안은 이탈해도 재개 재료로 남는다.
            assertTrue(pendingStore.current is PendingStoryCreation.Draft)
            assertEquals(
                CreateKeywordEffect.ExitFunnel,
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `입력이 없으면 소실 확인만 거쳐 이탈하고 레코드도 남기지 않는다`() =
        runTest(dispatcher) {
            val pending = FakePendingStoryCreationStore()
            val viewModel = viewModel(fixedTagsRepository(), pending)
            advanceUntilIdle()

            viewModel.onIntent(CreateKeywordIntent.LeaveFunnel)
            advanceUntilIdle()
            assertEquals(FunnelExitWarning.NOTHING_TO_PRESERVE, viewModel.uiState.value.exitWarning)
            viewModel.onIntent(CreateKeywordIntent.ConfirmLeaveFunnel)
            advanceUntilIdle()

            assertNull(pending.read())
            assertNull(viewModel.uiState.value.exitWarning)
            assertEquals(CreateKeywordEffect.ExitFunnel, viewModel.uiEffect.first())
        }

    @Test
    fun `임시 저장하지 않은 입력이 있으면 이탈 전에 경고한다`() =
        runTest(dispatcher) {
            val pending = FakePendingStoryCreationStore()
            val viewModel = viewModel(fixedTagsRepository(), pending)
            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Genre, tagId = 1L))
            advanceUntilIdle()

            viewModel.onIntent(CreateKeywordIntent.LeaveFunnel)
            advanceUntilIdle()

            assertEquals(FunnelExitWarning.UNSAVED_CHANGES, viewModel.uiState.value.exitWarning)
            assertNull(pending.read())
            assertNull(withTimeoutOrNull(100) { viewModel.uiEffect.first() })
        }

    @Test
    fun `경고에서 나가기를 고르면 저장하지 않은 입력을 버리고 이탈한다`() =
        runTest(dispatcher) {
            val pending = FakePendingStoryCreationStore()
            val viewModel = viewModel(fixedTagsRepository(), pending)
            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Genre, tagId = 1L))
            viewModel.onIntent(CreateKeywordIntent.LeaveFunnel)
            advanceUntilIdle()

            viewModel.onIntent(CreateKeywordIntent.ConfirmLeaveFunnel)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.exitWarning)
            assertNull(pending.read())
            assertEquals(CreateKeywordEffect.ExitFunnel, viewModel.uiEffect.first())
        }

    @Test
    fun `입력만으로는 저장하지 않고 임시 저장 버튼이 레코드를 남긴다`() =
        runTest(dispatcher) {
            val pending = FakePendingStoryCreationStore()
            val viewModel = viewModel(fixedTagsRepository(), pending)
            advanceUntilIdle()

            // 아무것도 건드리지 않았으면 저장할 것이 없다.
            assertFalse(viewModel.uiState.value.draftSave.canSave)

            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Genre, tagId = 1L))
            advanceUntilIdle()

            assertNull(pending.read())
            assertEquals(DraftSaveStatus.IDLE, viewModel.uiState.value.draftSave.status)
            assertTrue(viewModel.uiState.value.draftSave.canSave)
            assertTrue(viewModel.uiState.value.draftSave.hasUnsavedChanges)

            viewModel.onIntent(CreateKeywordIntent.SaveDraft)
            advanceUntilIdle()

            val record = pending.read() as PendingStoryCreation.KeywordDraft
            assertEquals(listOf(1L), record.snapshot.selectedGenreTagIds)
            assertFalse(viewModel.uiState.value.draftSave.hasUnsavedChanges)
            // 저장하고 나면 다시 편집하기 전까지 저장할 것이 없다.
            assertFalse(viewModel.uiState.value.draftSave.canSave)
        }

    @Test
    fun `임시 저장을 연달아 눌러도 바뀐 것이 없으면 한 번만 쓴다`() =
        runTest(dispatcher) {
            val pending = FakePendingStoryCreationStore()
            val viewModel = viewModel(fixedTagsRepository(), pending)
            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Genre, tagId = 1L))
            advanceUntilIdle()

            repeat(5) { viewModel.onIntent(CreateKeywordIntent.SaveDraft) }
            advanceTimeBy(DRAFT_SAVED_DISPLAY_MS - 1)
            runCurrent()

            assertEquals(1, pending.writes.size)
            assertEquals(DraftSaveStatus.SAVED, viewModel.uiState.value.draftSave.status)

            // 입력이 바뀌면 다시 쓴다.
            viewModel.onIntent(CreateKeywordIntent.ChangeCharacterName(KeywordTarget.Protagonist, "새 이름"))
            viewModel.onIntent(CreateKeywordIntent.SaveDraft)
            advanceUntilIdle()

            assertEquals(2, pending.writes.size)
        }

    @Test
    fun `저장 완료 표시는 3초 뒤 기본 상태로 돌아간다`() =
        runTest(dispatcher) {
            val pending = FakePendingStoryCreationStore()
            val viewModel = viewModel(fixedTagsRepository(), pending)
            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Genre, tagId = 1L))
            viewModel.onIntent(CreateKeywordIntent.SaveDraft)
            advanceTimeBy(DRAFT_SAVED_DISPLAY_MS - 1)
            runCurrent()

            assertEquals(DraftSaveStatus.SAVED, viewModel.uiState.value.draftSave.status)

            advanceTimeBy(1)
            runCurrent()

            assertEquals(DraftSaveStatus.IDLE, viewModel.uiState.value.draftSave.status)
        }

    @Test
    fun `저장 뒤 다시 편집하면 저장 완료 표시를 거둔다`() =
        runTest(dispatcher) {
            val pending = FakePendingStoryCreationStore()
            val viewModel = viewModel(fixedTagsRepository(), pending)
            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Genre, tagId = 1L))
            viewModel.onIntent(CreateKeywordIntent.SaveDraft)
            advanceTimeBy(DRAFT_SAVED_DISPLAY_MS - 1)
            runCurrent()
            assertEquals(DraftSaveStatus.SAVED, viewModel.uiState.value.draftSave.status)

            viewModel.onIntent(CreateKeywordIntent.ChangeCharacterName(KeywordTarget.Protagonist, "새 이름"))
            runCurrent()

            assertEquals(DraftSaveStatus.IDLE, viewModel.uiState.value.draftSave.status)
            assertTrue(viewModel.uiState.value.draftSave.hasUnsavedChanges)
        }

    @Test
    fun `입력을 모두 지우고 저장하면 남아 있던 저장본도 사라진다`() =
        runTest(dispatcher) {
            val pending = FakePendingStoryCreationStore()
            val viewModel = viewModel(fixedTagsRepository(), pending)
            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Genre, tagId = 1L))
            viewModel.onIntent(CreateKeywordIntent.SaveDraft)
            advanceUntilIdle()

            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Genre, tagId = 1L))
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.draftSave.canSave)

            viewModel.onIntent(CreateKeywordIntent.SaveDraft)
            advanceUntilIdle()

            assertNull(pending.read())
            assertFalse(viewModel.uiState.value.draftSave.hasUnsavedChanges)
        }

    @Test
    fun `키워드 임시 저장 쓰기가 실패하면 저장 완료로 표시하지 않는다`() =
        runTest(dispatcher) {
            val pending = FakePendingStoryCreationStore(writeSucceeds = false)
            val viewModel = viewModel(fixedTagsRepository(), pending)
            advanceUntilIdle()

            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Genre, tagId = 1L))
            viewModel.onIntent(CreateKeywordIntent.SaveDraft)
            advanceUntilIdle()

            assertNull(pending.read())
            assertEquals(DraftSaveStatus.IDLE, viewModel.uiState.value.draftSave.status)
            assertTrue(viewModel.uiState.value.draftSave.hasUnsavedChanges)
        }

    @Test
    fun `진행 중 레코드가 있으면 키워드 스냅숏이 덮어쓰지 않는다`() =
        runTest(dispatcher) {
            val pending = FakePendingStoryCreationStore()
            val inFlight = PendingStoryCreation.GeneratingStorylines(command = generationCommand())
            pending.write(inFlight)
            val viewModel = viewModel(fixedTagsRepository(), pending)
            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Genre, tagId = 1L))
            advanceUntilIdle()

            viewModel.onIntent(CreateKeywordIntent.SaveDraft)
            advanceUntilIdle()

            assertEquals(inFlight, pending.read())
        }

    @Test
    fun `키워드 임시 저장본이 있으면 복원하고 레코드를 유지한다`() =
        runTest(dispatcher) {
            val pending = FakePendingStoryCreationStore()
            pending.write(
                PendingStoryCreation.KeywordDraft(
                    snapshot =
                        KeywordDraftSnapshot(
                            selectedGenreTagIds = listOf(2L),
                            customGenreTags = listOf(KeywordCustomTagSnapshot("느와르", selected = false)),
                            protagonist =
                                KeywordCharacterSnapshot(
                                    name = "홍길동",
                                    gender = CharacterGender.MALE,
                                    selectedTagIds = listOf(10L),
                                    customTags = emptyList(),
                                ),
                            supportingCharacters = emptyList(),
                        ),
                ),
            )
            val viewModel = viewModel(fixedTagsRepository(), pending)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isRestoring)
            assertEquals(setOf(2L), state.selectedGenreTagIds)
            assertEquals(listOf(CustomTag("느와르", selected = false)), state.customGenreTags)
            assertEquals("홍길동", state.protagonist.name)
            assertEquals(CharacterGender.MALE, state.protagonist.gender)
            assertEquals(setOf(10L), state.protagonist.selectedTagIds)
            // 복원 직후 화면은 디스크와 같으므로 저장하지 않은 변경으로 세지 않는다.
            assertEquals(DraftSaveStatus.IDLE, state.draftSave.status)
            assertFalse(state.draftSave.hasUnsavedChanges)
            assertTrue(pending.read() is PendingStoryCreation.KeywordDraft)
        }

    @Test
    fun `복원할 레코드가 없어도 복원 대기는 끝난다`() =
        runTest(dispatcher) {
            val viewModel = viewModel(fixedTagsRepository())
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isRestoring)
        }

    @Test
    fun `복원이 끝나기 전에 저장해도 저장해 둔 입력을 잃지 않는다`() =
        runTest(dispatcher) {
            val pending = FakePendingStoryCreationStore()
            pending.write(
                PendingStoryCreation.KeywordDraft(
                    snapshot =
                        KeywordDraftSnapshot(
                            selectedGenreTagIds = listOf(2L),
                            customGenreTags = emptyList(),
                            protagonist =
                                KeywordCharacterSnapshot(
                                    name = "홍길동",
                                    gender = null,
                                    selectedTagIds = emptyList(),
                                    customTags = emptyList(),
                                ),
                            supportingCharacters = emptyList(),
                        ),
                ),
            )
            val viewModel = viewModel(fixedTagsRepository(), pending)

            viewModel.onIntent(CreateKeywordIntent.SaveDraft)
            advanceUntilIdle()

            val record = pending.read() as PendingStoryCreation.KeywordDraft
            assertEquals(listOf(2L), record.snapshot.selectedGenreTagIds)
            assertEquals("홍길동", record.snapshot.protagonist.name)
        }

    private fun generationCommand() =
        StorylineGenerationCommand(
            requestId = "req-1",
            genreTagIds = listOf(1L, 2L),
            customGenreTags = listOf("느와르"),
            protagonist =
                StoryCharacterInput(
                    name = "홍길동",
                    gender = CharacterGender.MALE,
                    featureTagIds = listOf(10L),
                    customTags = listOf("과묵함"),
                ),
            supportingCharacters = emptyList(),
            parentCreationId = null,
            isRegenerated = false,
        )

    private fun fixedTagsRepository(): FakeStoryCreationRepository =
        FakeStoryCreationRepository(
            tagsResult =
                DomainResult.Success(
                    listOf(
                        StoryTag(id = 1, name = "로맨스", category = StoryTagCategory.GENRE),
                        StoryTag(id = 2, name = "다정한", category = StoryTagCategory.PROTAGONIST),
                    ),
                ),
        )

    private class SequencedStoryCreationRepository(
        private val loadedTag: StoryTag,
    ) : FakeStoryCreationRepository() {
        var requestCount: Int = 0
            private set

        override suspend fun tags(): DomainResult<List<StoryTag>> {
            requestCount += 1
            return if (requestCount == 1) {
                DomainResult.Failure(DomainError.Network)
            } else {
                DomainResult.Success(listOf(loadedTag))
            }
        }
    }
}
