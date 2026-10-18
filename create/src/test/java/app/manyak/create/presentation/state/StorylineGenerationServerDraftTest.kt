package app.manyak.create.presentation.state

import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.create.entity.CharacterGender
import app.manyak.create.entity.CreationProgress
import app.manyak.create.entity.KeywordCharacterSnapshot
import app.manyak.create.entity.KeywordDraftSnapshot
import app.manyak.create.entity.PendingStoryCreation
import app.manyak.create.entity.ServerDraftLink
import app.manyak.create.entity.SimpleDraftOpen
import app.manyak.create.entity.StoryCompletionCommand
import app.manyak.create.entity.StoryDraftSave
import app.manyak.create.testing.FakePendingStoryCreationStore
import app.manyak.create.testing.FakeSimpleDraftStore
import app.manyak.create.testing.FakeStoryCompletionSubmitter
import app.manyak.create.testing.FakeStoryCreationRepository
import app.manyak.create.testing.TEST_DRAFT_ID
import app.manyak.create.testing.sampleGenerationCommand
import app.manyak.create.testing.sampleGenerationInput
import app.manyak.create.testing.sampleStorylineGeneration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorylineGenerationServerDraftTest {
    private val repository = FakeStoryCreationRepository()
    private val local = FakePendingStoryCreationStore()
    private val server = FakeSimpleDraftStore()
    private val submitter = FakeStoryCompletionSubmitter()

    private fun TestScope.store(localStore: FakePendingStoryCreationStore = local): StorylineGenerationStore =
        StorylineGenerationStore(repository, localStore, submitter, this, server).bind(TEST_DRAFT_ID)

    @Test
    fun `새 세션의 키워드 저장은 기기에 남기지 않고 같은 초안을 이어 쓴다`() =
        runTest {
            val store = store()
            assertEquals(FunnelEntryOutcome.READY, store.ensureRestored())

            assertEquals(StoryDraftSave.Saved(1), store.saveKeywordDraft(keyword("가")))
            assertEquals(StoryDraftSave.Saved(2), store.saveKeywordDraft(keyword("나")))

            assertEquals(listOf(null, 1), server.saves.map { it.baseVersion })
            assertTrue(server.saves.all { it.record is PendingStoryCreation.KeywordDraft })
            assertNull(local.current)
            assertTrue(store.hasSavedDraft())
        }

    @Test
    fun `생성 중과 생성 결과는 기기 안에 두고 그때의 서버 버전을 붙인다`() =
        runTest {
            val store = store()
            store.ensureRestored()
            store.saveKeywordDraft(keyword("가"))

            store.generate(sampleGenerationInput())
            runCurrent()
            assertEquals(
                ServerDraftLink(1),
                (local.writes.first() as PendingStoryCreation.GeneratingStorylines).serverDraft,
            )

            advanceUntilIdle()
            val draft = local.current as PendingStoryCreation.Draft
            assertEquals(ServerDraftLink(1), draft.serverDraft)
            // 결과를 보기만 하고 떠난 제작은 서버 자리를 차지하지 않는다.
            assertEquals(1, server.saves.size)
        }

    @Test
    fun `생성 결과는 다음 사용자 저장 때 서버로 옮기고 기기 안 기록을 지운다`() =
        runTest {
            val store = store()
            store.ensureRestored()
            store.generate(sampleGenerationInput())
            advanceUntilIdle()

            store.markStorylineSelected(0)
            store.saveDraft()
            runCurrent()

            val saved = server.saves.single()
            assertNull(saved.baseVersion)
            assertEquals(0, (saved.record as PendingStoryCreation.Draft).progress.selectedStorylineIndex)
            assertNull(local.current)
            assertEquals(DraftSaveStatus.SAVED, store.draftSave.value.status)
        }

    @Test
    fun `다시 만들기가 실패한 뒤 저장해도 화면의 결과를 만든 생성 요청을 가리킨다`() =
        runTest {
            val store = store()
            store.ensureRestored()
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            val shown = repository.generationCommands.single()
            repository.queuedGenerationResults += DomainResult.Failure(DomainError.Server(500, null, null))

            store.regenerate()
            advanceUntilIdle()
            store.updateActiveStoryline(1)
            store.saveDraft()
            advanceUntilIdle()

            val saved = server.saves.single().record as PendingStoryCreation.Draft
            assertEquals(shown.requestId, saved.generationCommand?.requestId)
        }

    @Test
    fun `충돌 확인에서 저장하면 서버 버전으로 덮어쓰고 기기 안 생성 결과 기록도 지운다`() =
        runTest {
            val store = store()
            store.ensureRestored()
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            server.queuedResults += StoryDraftSave.Conflict(5)
            val messages = mutableListOf<DraftSaveMessage>()
            backgroundScope.launch { store.draftMessages.collect { messages += it } }

            store.markStorylineSelected(0)
            store.saveDraft()
            advanceUntilIdle()
            assertEquals(5, store.draftSave.value.conflictVersion)
            assertTrue(local.current is PendingStoryCreation.Draft)

            server.stored[TEST_DRAFT_ID] = PendingStoryCreation.KeywordDraft(keyword("다른 기기")) to 5
            store.resolveConflict(overwrite = true)
            advanceUntilIdle()

            assertEquals(5, server.saves.last().baseVersion)
            assertNull(store.draftSave.value.conflictVersion)
            assertNull(local.current)
            assertEquals(listOf(DraftSaveMessage.SAVED), messages)
        }

    @Test
    fun `백그라운드 저장은 실패와 충돌을 알리지 않는다`() =
        runTest {
            val store = store()
            store.ensureRestored()
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            server.queuedResults += StoryDraftSave.Conflict(5)
            val messages = mutableListOf<DraftSaveMessage>()
            backgroundScope.launch { store.draftMessages.collect { messages += it } }

            store.markStorylineSelected(0)
            store.saveDraft(notify = false)
            advanceUntilIdle()

            assertNull(store.draftSave.value.conflictVersion)
            assertEquals(emptyList<DraftSaveMessage>(), messages)
        }

    @Test
    fun `버튼 저장의 상한 초과는 안내한다`() =
        runTest {
            val store = store()
            store.ensureRestored()
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            server.queuedResults += StoryDraftSave.LimitExceeded

            store.markStorylineSelected(0)
            store.saveDraft()
            runCurrent()

            assertEquals(DraftSaveMessage.LIMIT, store.draftMessages.first())
        }

    @Test
    fun `저장 중에 다시 저장하면 건너뛰지 않고 앞 저장의 버전으로 뒤이어 보낸다`() =
        runTest {
            val store = store()
            store.ensureRestored()
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            val gate = CompletableDeferred<Unit>()
            server.gate = gate

            store.markStorylineSelected(0)
            store.saveDraft()
            runCurrent()
            store.updateAdditionalInfoProgress(listOf("배경"), emptyList(), artStyle = null)
            store.saveDraft(notify = false)
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(null, 1), server.saves.map { it.baseVersion })
            assertEquals(
                listOf("배경"),
                (server.saves.last().record as PendingStoryCreation.Draft).progress.additionalInfoInputs,
            )
        }

    @Test
    fun `카드로 연 서버 초안은 그 버전을 기준으로 저장한다`() =
        runTest {
            server.stored[TEST_DRAFT_ID] = storyDraft(selectedIndex = null) to 3
            val store = store()

            assertEquals(FunnelEntryOutcome.READY, store.ensureRestored(resume = true))
            assertTrue(store.state.value is StorylineGenerationState.Generated)

            store.updateActiveStoryline(1)
            store.saveDraft()
            advanceUntilIdle()

            assertEquals(3, server.saves.single().baseVersion)
        }

    @Test
    fun `추가 정보 단계로 저장된 서버 초안을 카드로 열면 추가 정보 단계로 넘길 순번을 한 번 준다`() =
        runTest {
            server.stored[TEST_DRAFT_ID] = storyDraft(selectedIndex = 1) to 3
            val store = store()

            store.ensureRestored(resume = true)

            assertEquals(1, store.takeResumeStorylineIndex())
            assertNull(store.takeResumeStorylineIndex())
        }

    @Test
    fun `카드로 연 서버 초안의 처음 저장 시각을 진행 기록과 완성 요청이 이어받는다`() =
        runTest {
            server.openResult =
                DomainResult.Success(
                    SimpleDraftOpen.Found(
                        PendingStoryCreation.KeywordDraft(keyword("가")),
                        version = 2,
                        createdAt = 1_000,
                    ),
                )
            val store = store()
            store.ensureRestored(resume = true)

            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            assertEquals(1_000L, local.all.single().createdAt)

            store.submitCompletion(StoryCompletionCommand("c1", 10, 1, emptyList(), "ANIME"))
            assertEquals(1_000L, submitter.submitted.single().createdAt)
        }

    @Test
    fun `카드로 연 초안을 찾지 못하면 알리고 같은 ID 의 새 세션으로 시작한다`() =
        runTest {
            server.openResult = DomainResult.Success(SimpleDraftOpen.NotFound(version = 4))
            val store = store()

            assertEquals(FunnelEntryOutcome.NOT_FOUND, store.ensureRestored(resume = true))
            store.saveKeywordDraft(keyword("가"))

            // 생성 결과를 잃은 초안은 같은 ID 로 덮어써 새로 시작한다.
            assertEquals(4, server.saves.single().baseVersion)
        }

    @Test
    fun `새로 시작한 화면은 서버 초안이 없어도 알리지 않는다`() =
        runTest {
            assertEquals(FunnelEntryOutcome.READY, store().ensureRestored(resume = false))
        }

    @Test
    fun `서버를 읽지 못하면 진입이 실패하고 다시 시도할 수 있다`() =
        runTest {
            server.openResult = DomainResult.Failure(DomainError.Network)
            val store = store()

            assertEquals(FunnelEntryOutcome.FAILED, store.ensureRestored())
            server.openResult = null
            assertEquals(FunnelEntryOutcome.READY, store.ensureRestored())
        }

    @Test
    fun `진입 판정이 끝나면 다시 묻지 않는다`() =
        runTest {
            val store = store()
            store.ensureRestored()
            store.ensureRestored()

            assertEquals(1, server.openCount)
        }

    @Test
    fun `서버 초안을 가리키는 기기 안 기록으로 열면 그 기준 버전으로 서버에 저장한다`() =
        runTest {
            val linked = FakePendingStoryCreationStore(storyDraft(null).copy(serverDraft = ServerDraftLink(2)))
            server.stored[TEST_DRAFT_ID] = PendingStoryCreation.KeywordDraft(keyword("가")) to 2
            val store = store(linked)

            store.ensureRestored(resume = true)
            store.updateActiveStoryline(1)
            store.saveDraft()
            advanceUntilIdle()

            assertEquals(0, server.openCount)
            assertEquals(2, server.saves.single().baseVersion)
            assertNull(linked.current)
        }

    @Test
    fun `따로 보이던 서버 초안 카드로 열면 이 기기의 기록을 새 ID 로 떼어 내고 서버 초안을 연다`() =
        runTest {
            val fork = storyDraft(null).copy(serverDraft = ServerDraftLink(1))
            val forked = FakePendingStoryCreationStore(fork)
            server.stored[TEST_DRAFT_ID] = PendingStoryCreation.KeywordDraft(keyword("다른 기기")) to 3
            val store = store(forked)

            assertEquals(FunnelEntryOutcome.READY, store.ensureRestored(resume = true, server = true))

            assertEquals("다른 기기", store.takeRestoredKeyword()?.protagonist?.name)
            assertNull(forked.current)
            val detached = forked.all.single()
            assertTrue(detached.draftId != TEST_DRAFT_ID)
            assertNull((detached.record as PendingStoryCreation.Draft).serverDraft)
        }

    @Test
    fun `연결 없는 기존 기기 안 초안은 기기 안에 저장한다`() =
        runTest {
            val legacy = FakePendingStoryCreationStore(storyDraft(null))
            val store = store(legacy)

            store.ensureRestored(resume = true)
            store.updateActiveStoryline(1)
            store.saveDraft()
            advanceUntilIdle()

            assertEquals(emptyList<FakeSimpleDraftStore.Save>(), server.saves)
            assertEquals(1, (legacy.current as PendingStoryCreation.Draft).progress.activeStorylineIndex)
            assertNull((legacy.current as PendingStoryCreation.Draft).serverDraft)
        }

    @Test
    fun `완성 요청은 세션 초안 ID 를 싣고 서버 초안 연결을 남긴다`() =
        runTest {
            val store = store()
            store.ensureRestored()
            store.saveKeywordDraft(keyword("가"))
            store.generate(sampleGenerationInput())
            advanceUntilIdle()

            store.submitCompletion(StoryCompletionCommand("c1", 10, 1, emptyList(), "ANIME", draftId = TEST_DRAFT_ID))

            val request = submitter.submitted.single()
            assertEquals(TEST_DRAFT_ID, request.command.draftId)
            assertEquals(ServerDraftLink(1), request.serverDraft)
        }

    @Test
    fun `입력을 모두 지우고 저장하면 서버 키워드 초안을 지운다`() =
        runTest {
            val store = store()
            store.ensureRestored()
            store.saveKeywordDraft(keyword("가"))

            assertEquals(StoryDraftSave.Saved(0), store.saveKeywordDraft(keyword("")))

            assertEquals(listOf(TEST_DRAFT_ID), server.deleted)
            assertTrue(!store.hasSavedDraft())
        }

    private fun keyword(name: String): KeywordDraftSnapshot =
        KeywordDraftSnapshot(
            selectedGenreTagIds = emptyList(),
            customGenreTags = emptyList(),
            protagonist =
                KeywordCharacterSnapshot(
                    name,
                    if (name.isEmpty()) null else CharacterGender.MALE,
                    emptyList(),
                    emptyList(),
                ),
            supportingCharacters = emptyList(),
        )

    private fun storyDraft(selectedIndex: Int?): PendingStoryCreation.Draft =
        PendingStoryCreation.Draft(
            generationCommand = sampleGenerationCommand(),
            generation = sampleStorylineGeneration(),
            progress = CreationProgress(selectedStorylineIndex = selectedIndex),
        )
}
