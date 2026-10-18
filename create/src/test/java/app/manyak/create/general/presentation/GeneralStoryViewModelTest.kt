package app.manyak.create.general.presentation

import androidx.lifecycle.viewModelScope
import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.auth.domain.SessionGate
import app.manyak.common.domain.chat.ChatStarter
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.chat.CreatedChat
import app.manyak.create.entity.StoryDraftSave
import app.manyak.create.entity.StoryTag
import app.manyak.create.entity.StoryTagCategory
import app.manyak.create.general.data.GeneralReviewWatch
import app.manyak.create.general.entity.GeneralDraftOpen
import app.manyak.create.testing.FakeStoryCreationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeneralStoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<GeneralStoryViewModel>()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    @Test
    fun database_load_failure_does_not_open_a_blank_editable_draft() =
        runTest(dispatcher) {
            val drafts = GeneralDraftStoreFake().apply { openResult = DomainResult.Failure(DomainError.Network) }
            val model = model(drafts = drafts)
            advanceUntilIdle()
            assertTrue(model.uiState.value.loadFailed)
            assertFalse(model.uiState.value.inputsEnabled)
            assertFalse(model.uiState.value.loading)
            model.onIntent(GeneralEditorIntent.SaveDraft())
            advanceUntilIdle()
            assertEquals(0, drafts.saves)
        }

    @Test
    fun closing_an_untouched_new_draft_leaves_without_asking_but_input_asks_first() =
        runTest(dispatcher) {
            val model =
                model(
                    drafts =
                        GeneralDraftStoreFake().apply {
                            openResult =
                                DomainResult.Success(GeneralDraftOpen.NotFound)
                        },
                )
            advanceUntilIdle()
            model.onIntent(GeneralEditorIntent.Close)
            advanceUntilIdle()
            assertFalse(model.uiState.value.showExit)
            assertEquals(GeneralEditorEffect.Close, model.uiEffect.first())

            model.onIntent(
                GeneralEditorIntent.ChangeForm(
                    model.uiState.value.form
                        .copy(title = "제목"),
                ),
            )
            advanceUntilIdle()
            model.onIntent(GeneralEditorIntent.Close)
            advanceUntilIdle()
            assertTrue(model.uiState.value.showExit)
        }

    @Test
    fun a_save_exception_releases_the_saving_indicator() =
        runTest(dispatcher) {
            val drafts = GeneralDraftStoreFake().apply { saveFails = true }
            val model = model(drafts = drafts)
            advanceUntilIdle()
            model.onIntent(GeneralEditorIntent.SaveDraft())
            advanceUntilIdle()
            assertEquals(1, drafts.saves)
            assertFalse(model.uiState.value.saving)
            assertFalse(model.uiState.value.saveLocked)
        }

    @Test
    fun first_post_timeout_stops_spinner_and_blocks_duplicate_submission() =
        runTest(dispatcher) {
            val repository = GeneralEditorRepositoryFake().apply { submitResult = { awaitCancellation() } }
            val model = model(repository)
            advanceUntilIdle()
            model.onIntent(GeneralEditorIntent.Submit)
            runCurrent()
            assertTrue(model.uiState.value.submitting)
            advanceTimeBy(90_000)
            runCurrent()
            assertFalse(model.uiState.value.submitting)
            assertTrue(model.uiState.value.uncertainSubmission)
            model.onIntent(GeneralEditorIntent.Submit)
            runCurrent()
            assertEquals(1, repository.posts)
        }

    @Test
    fun accepted_record_failure_retries_local_record_without_posting_again() =
        runTest(dispatcher) {
            val repository = GeneralEditorRepositoryFake()
            val drafts = GeneralDraftStoreFake().apply { acceptedSucceeds = false }
            val model = model(repository, drafts)
            advanceUntilIdle()
            model.onIntent(GeneralEditorIntent.Submit)
            advanceUntilIdle()
            assertEquals(1, repository.posts)
            assertEquals(3, drafts.acceptedWrites)
            assertTrue(model.uiState.value.acceptanceRecordFailed)
            assertFalse(model.uiState.value.inputsEnabled)
            drafts.acceptedSucceeds = true
            model.onIntent(GeneralEditorIntent.Retry)
            advanceUntilIdle()
            assertEquals(4, drafts.acceptedWrites)
            assertEquals(1, repository.posts)
            assertFalse(model.uiState.value.acceptanceRecordFailed)
        }

    @Test
    fun submit_waits_for_storage_but_not_the_two_second_save_cooldown() =
        runTest(dispatcher) {
            val repository = GeneralEditorRepositoryFake()
            val model = model(repository)
            advanceUntilIdle()
            model.onIntent(GeneralEditorIntent.SaveDraft())
            runCurrent()
            assertTrue(model.uiState.value.saveLocked)
            model.onIntent(GeneralEditorIntent.Submit)
            runCurrent()
            assertEquals(1, repository.posts)
            assertEquals(0L, testScheduler.currentTime)
        }

    @Test
    fun an_old_editor_cannot_save_or_submit_after_account_generation_changes() =
        runTest(dispatcher) {
            val gate = SessionGate()
            val repository = GeneralEditorRepositoryFake()
            val drafts = GeneralDraftStoreFake()
            val model = model(repository, drafts, gate)
            advanceUntilIdle()
            gate.raiseBarrier()
            gate.lowerBarrier()
            model.onIntent(GeneralEditorIntent.SaveDraft())
            model.onIntent(GeneralEditorIntent.Submit)
            advanceUntilIdle()
            assertEquals(0, drafts.saves)
            assertEquals(0, repository.posts)
        }

    @Test
    fun saves_chain_the_server_version_and_submit_names_the_draft() =
        runTest(dispatcher) {
            val repository = GeneralEditorRepositoryFake()
            val drafts = GeneralDraftStoreFake()
            val model = model(repository, drafts)
            advanceUntilIdle()
            model.onIntent(GeneralEditorIntent.SaveDraft())
            advanceUntilIdle()
            advanceTimeBy(2_001)
            model.onIntent(GeneralEditorIntent.SaveDraft(false))
            advanceUntilIdle()
            assertEquals(listOf(3, 4), drafts.baseVersions)
            model.onIntent(GeneralEditorIntent.Submit)
            advanceUntilIdle()
            assertEquals(listOf<String?>("draft"), repository.draftIds)
        }

    @Test
    fun a_conflict_asks_first_and_overwrite_uses_the_server_version() =
        runTest(dispatcher) {
            val drafts = GeneralDraftStoreFake().apply { saveResults += StoryDraftSave.Conflict(7) }
            val model = model(drafts = drafts)
            advanceUntilIdle()
            model.onIntent(GeneralEditorIntent.SaveDraft())
            advanceUntilIdle()
            assertEquals(7, model.uiState.value.draftConflictVersion)
            model.onIntent(GeneralEditorIntent.ResolveDraftConflict(overwrite = true))
            advanceUntilIdle()
            assertEquals(listOf(3, 7), drafts.baseVersions)
            assertEquals(null, model.uiState.value.draftConflictVersion)
            assertEquals(
                GeneralEditorEffect.Message(GeneralEditorMessage.DRAFT_SAVED),
                model.uiEffect.first(),
            )
        }

    @Test
    fun a_background_save_failure_stays_silent() =
        runTest(dispatcher) {
            val drafts =
                GeneralDraftStoreFake().apply {
                    saveResults += StoryDraftSave.Conflict(7)
                    saveResults += StoryDraftSave.LimitExceeded
                }
            val model = model(drafts = drafts)
            advanceUntilIdle()
            model.onIntent(GeneralEditorIntent.SaveDraft(false))
            advanceUntilIdle()
            assertEquals(null, model.uiState.value.draftConflictVersion)
            model.onIntent(GeneralEditorIntent.SaveDraft())
            advanceUntilIdle()
            assertEquals(GeneralEditorEffect.Message(GeneralEditorMessage.DRAFT_LIMIT), model.uiEffect.first())
        }

    @Test
    fun a_missing_draft_is_announced_only_when_resumed_from_a_card() =
        runTest(dispatcher) {
            val missing = DomainResult.Success(GeneralDraftOpen.NotFound)
            val resumed =
                model(
                    drafts = GeneralDraftStoreFake().apply { openResult = missing },
                    entry = GeneralEditorEntry.Draft("draft", resume = true),
                )
            advanceUntilIdle()
            assertEquals(GeneralEditorEffect.Message(GeneralEditorMessage.DRAFT_NOT_FOUND), resumed.uiEffect.first())
            assertFalse(resumed.uiState.value.form.hasInput)
            assertTrue(resumed.uiState.value.inputsEnabled)

            val fresh = model(drafts = GeneralDraftStoreFake().apply { openResult = missing })
            advanceUntilIdle()
            fresh.onIntent(GeneralEditorIntent.Close)
            advanceUntilIdle()
            assertEquals(GeneralEditorEffect.Close, fresh.uiEffect.first())
        }

    private fun TestScope.model(
        repository: GeneralEditorRepositoryFake = GeneralEditorRepositoryFake(),
        drafts: GeneralDraftStoreFake = GeneralDraftStoreFake(),
        gate: SessionGate = SessionGate(),
        entry: GeneralEditorEntry = GeneralEditorEntry.Draft("draft"),
    ): GeneralStoryViewModel =
        GeneralStoryViewModel(
            entry = entry,
            repository = repository,
            drafts = drafts,
            catalog =
                FakeStoryCreationRepository(
                    DomainResult.Success(listOf(StoryTag(1, "판타지", StoryTagCategory.GENRE))),
                ),
            chatStarter =
                object : ChatStarter {
                    override suspend fun createChat(
                        storyId: String,
                        startSettingId: String?,
                        personaId: String?,
                    ): DomainResult<CreatedChat> = DomainResult.Success(CreatedChat("chat"))
                },
            gate = gate,
            analytics = NoOpAnalytics,
            writeScope = this,
            clock = GeneralEditorClock { testScheduler.currentTime },
            reviewWatch = GeneralReviewWatch { testScheduler.currentTime },
        ).also { models += it }
}
