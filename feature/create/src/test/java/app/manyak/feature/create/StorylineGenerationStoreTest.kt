package app.manyak.feature.create

import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.CreationProgress
import app.manyak.core.domain.story.CreationRequestSnapshot
import app.manyak.core.domain.story.PendingStoryCreation
import app.manyak.core.domain.story.StoryCharacterInput
import app.manyak.core.domain.story.StoryCompletionCommand
import app.manyak.core.domain.story.StorylineGenerationCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorylineGenerationStoreTest {
    @Test
    fun `생성 성공은 결과를 발행하고 최초 생성 필드를 명시한다`() =
        runTest {
            val repository = FakeStoryCreationRepository()
            val store = StorylineGenerationStore(repository, FakePendingStoryCreationStore(), this)

            store.generate(sampleGenerationInput())

            advanceUntilIdle()

            val command = repository.generationCommands.single()
            assertNull(command.parentCreationId)
            assertFalse(command.isRegenerated)
            assertEquals(
                sampleStorylineGeneration(),
                (store.state.value as StorylineGenerationState.Generated).result,
            )
        }

    @Test
    fun `성공 결과의 재생성은 새 요청 ID 에 직전 요청 ID 를 부모로 싣는다`() =
        runTest {
            val repository = FakeStoryCreationRepository()
            val store = StorylineGenerationStore(repository, FakePendingStoryCreationStore(), this)

            store.generate(sampleGenerationInput())

            advanceUntilIdle()
            store.regenerate()
            advanceUntilIdle()

            val (first, second) = repository.generationCommands
            assertNotEquals(first.requestId, second.requestId)
            assertEquals(first.requestId, second.parentCreationId)
            assertTrue(second.isRegenerated)
            assertEquals(first.genreTagIds, second.genreTagIds)
        }

    @Test
    fun `실패 후 다시 만들기는 같은 요청 ID 로 재시도한다`() =
        runTest {
            val repository = FakeStoryCreationRepository()
            repository.queuedGenerationResults += DomainResult.Failure(DomainError.Network)
            val store = StorylineGenerationStore(repository, FakePendingStoryCreationStore(), this)

            store.generate(sampleGenerationInput())

            advanceUntilIdle()
            assertEquals(StorylineGenerationState.Failed(previousResult = null), store.state.value)

            store.regenerate()

            advanceUntilIdle()

            val (first, second) = repository.generationCommands
            assertEquals(first.requestId, second.requestId)
            assertFalse(second.isRegenerated)
            assertTrue(store.state.value is StorylineGenerationState.Generated)
        }

    @Test
    fun `재생성 실패는 직전 성공 결과를 유지한다`() =
        runTest {
            val repository = FakeStoryCreationRepository()
            val store = StorylineGenerationStore(repository, FakePendingStoryCreationStore(), this)

            store.generate(sampleGenerationInput())

            advanceUntilIdle()
            repository.queuedGenerationResults += DomainResult.Failure(DomainError.Network)
            store.regenerate()
            advanceUntilIdle()

            assertEquals(
                StorylineGenerationState.Failed(previousResult = sampleStorylineGeneration()),
                store.state.value,
            )
        }

    @Test
    fun `직전 명령이 없으면 재생성 요청을 보내지 않는다`() =
        runTest {
            val repository = FakeStoryCreationRepository()
            val store = StorylineGenerationStore(repository, FakePendingStoryCreationStore(), this)

            store.regenerate()

            advanceUntilIdle()

            assertTrue(repository.generationCommands.isEmpty())
            assertEquals(StorylineGenerationState.Idle, store.state.value)
        }

    @Test
    fun `새 생성은 이전 퍼널의 결과를 버린다`() =
        runTest {
            val repository = FakeStoryCreationRepository()
            val store = StorylineGenerationStore(repository, FakePendingStoryCreationStore(), this)

            store.generate(sampleGenerationInput())

            advanceUntilIdle()
            repository.queuedGenerationResults += DomainResult.Failure(DomainError.Network)
            store.generate(sampleGenerationInput())
            advanceUntilIdle()

            assertEquals(StorylineGenerationState.Failed(previousResult = null), store.state.value)
        }

    @Test
    fun `생성 요청은 시작 전에 영속되고 성공 결과는 즉시 임시 저장된다`() =
        runTest {
            val repository = FakeStoryCreationRepository()
            val pendingStore = FakePendingStoryCreationStore()
            val store = StorylineGenerationStore(repository, pendingStore, this)

            store.generate(sampleGenerationInput())

            advanceUntilIdle()

            val written = pendingStore.writes.first() as PendingStoryCreation.GeneratingStorylines
            assertEquals(repository.generationCommands.single().requestId, written.command.requestId)
            val draft = pendingStore.current as PendingStoryCreation.Draft
            assertEquals(sampleStorylineGeneration(), draft.generation)
            assertFalse(store.draftSave.value.hasUnsavedChanges)
        }

    @Test
    fun `네트워크 오류는 레코드를 보존하고 HTTP 오류는 즉시 지운다`() =
        runTest {
            val repository = FakeStoryCreationRepository()
            val pendingStore = FakePendingStoryCreationStore()
            val store = StorylineGenerationStore(repository, pendingStore, this)

            repository.queuedGenerationResults += DomainResult.Failure(DomainError.Network)
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            assertTrue(pendingStore.current is PendingStoryCreation.GeneratingStorylines)

            repository.queuedGenerationResults +=
                DomainResult.Failure(DomainError.Server(status = 502, code = null, requestId = null))
            store.regenerate()
            advanceUntilIdle()
            assertNull(pendingStore.current)
        }

    @Test
    fun `재시도 409 는 실패가 아니라 복구 폴링으로 결과를 되찾는다`() =
        runTest {
            val repository = FakeStoryCreationRepository()
            val pendingStore = FakePendingStoryCreationStore()
            val store = StorylineGenerationStore(repository, pendingStore, this)
            repository.queuedGenerationResults += DomainResult.Failure(DomainError.Network)
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            repository.queuedGenerationResults +=
                DomainResult.Failure(DomainError.Server(status = 409, code = null, requestId = null))

            store.regenerate()

            advanceUntilIdle()
            assertEquals(StorylineGenerationState.Generating, store.state.value)

            repository.queuedCreationRequestResults += DomainResult.Success(CreationRequestSnapshot.Pending)
            repository.queuedCreationRequestResults +=
                DomainResult.Success(CreationRequestSnapshot.StorylinesReady(sampleStorylineGeneration()))
            val recovery = launch { store.runStorylineRecovery() }
            advanceUntilIdle()

            assertEquals(
                sampleStorylineGeneration(),
                (store.state.value as StorylineGenerationState.Generated).result,
            )
            val retriedRequestId = repository.generationCommands.first().requestId
            assertEquals(listOf(retriedRequestId, retriedRequestId), repository.creationRequestIds)
            assertTrue(pendingStore.current is PendingStoryCreation.Draft)
            recovery.cancel()
        }

    @Test
    fun `진행 레코드 복원은 생성 중 상태로 폴링에 합류하고 실패 조회는 실패 화면으로 합류한다`() =
        runTest {
            val repository = FakeStoryCreationRepository()
            val command = sampleGenerationCommand()
            val pendingStore =
                FakePendingStoryCreationStore(initial = PendingStoryCreation.GeneratingStorylines(command))
            val store = StorylineGenerationStore(repository, pendingStore, this)

            store.ensureRestored()
            assertEquals(StorylineGenerationState.Generating, store.state.value)

            repository.queuedCreationRequestResults += DomainResult.Success(CreationRequestSnapshot.Failed)
            val recovery = launch { store.runStorylineRecovery() }
            advanceUntilIdle()

            assertEquals(StorylineGenerationState.Failed(previousResult = null), store.state.value)
            assertNull(pendingStore.current)
            recovery.cancel()
        }

    @Test
    fun `임시 저장본 복원은 결과·진행 스냅숏을 되살리고 레코드를 유지한다`() =
        runTest {
            val repository = FakeStoryCreationRepository()
            val progress =
                CreationProgress(
                    selectedStorylineIndex = 1,
                    activeStorylineIndex = 1,
                    additionalInfoInputs = listOf("배경은 서울"),
                    selectedRecommendations = listOf("폐허를 자세히 그려줘"),
                )
            val pendingStore =
                FakePendingStoryCreationStore(
                    initial =
                        PendingStoryCreation.Draft(
                            generationCommand = sampleGenerationCommand(),
                            generation = sampleStorylineGeneration(),
                            progress = progress,
                        ),
                )
            val store = StorylineGenerationStore(repository, pendingStore, this)

            store.ensureRestored()

            assertEquals(
                sampleStorylineGeneration(),
                (store.state.value as StorylineGenerationState.Generated).result,
            )
            assertEquals(progress, store.progress)
            assertTrue(pendingStore.current is PendingStoryCreation.Draft)

            // 복원된 명령으로 "다시 만들기" 체인이 이어진다.
            store.regenerate()
            advanceUntilIdle()
            assertEquals(sampleGenerationCommand().requestId, repository.generationCommands.single().parentCreationId)
        }

    @Test
    fun `스토리라인 진행 변경은 모아 두었다가 임시 저장에서 한 번에 나간다`() =
        runTest {
            val pendingStore = FakePendingStoryCreationStore()
            val store = StorylineGenerationStore(FakeStoryCreationRepository(), pendingStore, this)
            store.generate(sampleGenerationInput())
            advanceUntilIdle()

            store.updateActiveStoryline(2)
            store.markStorylineSelected(2)
            advanceUntilIdle()

            assertTrue(store.draftSave.value.hasUnsavedChanges)
            assertEquals(0, (pendingStore.current as PendingStoryCreation.Draft).progress.activeStorylineIndex)

            store.saveDraft()
            advanceUntilIdle()

            val progress = (pendingStore.current as PendingStoryCreation.Draft).progress
            assertEquals(2, progress.activeStorylineIndex)
            assertEquals(2, progress.selectedStorylineIndex)
            assertFalse(store.draftSave.value.hasUnsavedChanges)
        }

    @Test
    fun `임시 저장을 연달아 눌러도 바뀐 것이 없으면 한 번만 쓴다`() =
        runTest {
            val pendingStore = FakePendingStoryCreationStore()
            val store = StorylineGenerationStore(FakeStoryCreationRepository(), pendingStore, this)
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            val writesAfterGeneration = pendingStore.writes.size

            repeat(5) { store.saveDraft() }
            advanceTimeBy(DRAFT_SAVED_DISPLAY_MS - 1)
            runCurrent()

            assertEquals(writesAfterGeneration, pendingStore.writes.size)
            assertEquals(DraftSaveStatus.SAVED, store.draftSave.value.status)
            // 생성 성공이 이미 저장해 둬 누를 것이 없다.
            assertFalse(store.draftSave.value.canSave)

            // 내용이 바뀌면 다시 쓴다.
            store.markStorylineSelected(1)
            assertTrue(store.draftSave.value.canSave)
            store.saveDraft()
            advanceUntilIdle()

            assertEquals(writesAfterGeneration + 1, pendingStore.writes.size)
        }

    @Test
    fun `저장 완료 표시는 3초 뒤 기본 상태로 돌아간다`() =
        runTest {
            val store =
                StorylineGenerationStore(FakeStoryCreationRepository(), FakePendingStoryCreationStore(), this)
            store.generate(sampleGenerationInput())
            advanceTimeBy(DRAFT_SAVED_DISPLAY_MS - 1)
            runCurrent()

            assertEquals(DraftSaveStatus.SAVED, store.draftSave.value.status)

            advanceTimeBy(1)
            runCurrent()

            assertEquals(DraftSaveStatus.IDLE, store.draftSave.value.status)
        }

    @Test
    fun `생성 중에는 진행 레코드를 덮지 않도록 임시 저장을 잠근다`() =
        runTest {
            val repository = FakeStoryCreationRepository()
            repository.holdGeneration = true
            val pendingStore = FakePendingStoryCreationStore()
            val store = StorylineGenerationStore(repository, pendingStore, this)

            store.generate(sampleGenerationInput())
            advanceUntilIdle()

            assertFalse(store.draftSave.value.canSave)
            store.saveDraft()
            advanceUntilIdle()

            assertTrue(pendingStore.current is PendingStoryCreation.GeneratingStorylines)
            // 응답을 기다리는 요청을 끊어 테스트 스코프가 끝나게 한다.
            store.leaveFunnel()
        }

    @Test
    fun `임시 저장 쓰기가 실패하면 저장 완료로 표시하지 않는다`() =
        runTest {
            val pendingStore = FakePendingStoryCreationStore(writeSucceeds = false)
            val store = StorylineGenerationStore(FakeStoryCreationRepository(), pendingStore, this)

            store.generate(sampleGenerationInput())
            advanceUntilIdle()

            assertTrue(store.state.value is StorylineGenerationState.Generated)
            assertEquals(DraftSaveStatus.IDLE, store.draftSave.value.status)
            assertNull(pendingStore.current)
        }

    @Test
    fun `대기 중 Draft 저장은 완성 요청 레코드를 덮어쓰지 않는다`() =
        runTest {
            val pendingStore = FakePendingStoryCreationStore()
            val store = StorylineGenerationStore(FakeStoryCreationRepository(), pendingStore, this)
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            store.updateAdditionalInfoProgress(listOf("배경은 서울"), emptyList())

            val generation = sampleStorylineGeneration()
            val command =
                StoryCompletionCommand(
                    requestId = "completion-request",
                    simpleCreationId = generation.simpleCreationId,
                    storylineId = generation.storylines.first().id,
                    additionalInfos = listOf("배경은 서울"),
                )
            store.beginCompletion(command)
            advanceUntilIdle()

            val record = pendingStore.current as PendingStoryCreation.CompletingStory
            assertEquals(command, record.command)
            assertEquals(listOf("배경은 서울"), record.progress.additionalInfoInputs)
        }

    @Test
    fun `이탈은 저장하지 않은 변경을 버리고 진행 중 레코드는 유지하며 스토어를 초기화한다`() =
        runTest {
            val repository = FakeStoryCreationRepository()
            val pendingStore = FakePendingStoryCreationStore()
            val store = StorylineGenerationStore(repository, pendingStore, this)

            // 저장하지 않은 탭 변경은 마지막 저장 스냅숏을 덮지 않는다.
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            store.updateActiveStoryline(2)
            store.leaveFunnel()
            val draft = pendingStore.current as PendingStoryCreation.Draft
            assertEquals(sampleStorylineGeneration(), draft.generation)
            assertEquals(0, draft.progress.activeStorylineIndex)
            assertEquals(StorylineGenerationState.Idle, store.state.value)

            // 진행 중 레코드가 있는 이탈 — 그대로 유지한다.
            store.ensureRestored()
            repository.queuedGenerationResults += DomainResult.Failure(DomainError.Network)
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            store.leaveFunnel()
            assertTrue(pendingStore.current is PendingStoryCreation.GeneratingStorylines)

            // 남은 것이 없는 이탈 — 조용히 나간다.
            pendingStore.clear()
            store.leaveFunnel()
            assertNull(pendingStore.current)
        }
}

private fun sampleGenerationCommand(): StorylineGenerationCommand =
    StorylineGenerationCommand(
        requestId = "restored-request",
        genreTagIds = listOf(1),
        customGenreTags = emptyList(),
        protagonist =
            StoryCharacterInput(name = null, gender = null, featureTagIds = listOf(2), customTags = emptyList()),
        supportingCharacters = emptyList(),
        parentCreationId = null,
        isRegenerated = false,
    )
