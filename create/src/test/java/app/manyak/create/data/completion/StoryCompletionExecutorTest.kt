package app.manyak.create.data.completion

import app.manyak.auth.domain.SessionGate
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.create.entity.CompletedStory
import app.manyak.create.entity.CompletionOutcome
import app.manyak.create.entity.CreationProgress
import app.manyak.create.entity.CreationRequestSnapshot
import app.manyak.create.entity.StoryCompletionCommand
import app.manyak.create.entity.StoryCompletionRequest
import app.manyak.create.testing.FakePendingStoryCreationStore
import app.manyak.create.testing.FakeStoryCompletionRequestStore
import app.manyak.create.testing.FakeStoryCreationRepository
import app.manyak.create.testing.sampleStorylineGeneration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoryCompletionExecutorTest {
    private class Fixture(
        val repository: FakeStoryCreationRepository,
        val requestStore: FakeStoryCompletionRequestStore,
        val gate: SessionGate,
        val executor: StoryCompletionExecutor,
    )

    private fun TestScope.fixture(initial: List<StoryCompletionRequest> = emptyList()): Fixture {
        val repository = FakeStoryCreationRepository()
        val draftStore = FakePendingStoryCreationStore()
        val requestStore = FakeStoryCompletionRequestStore(initial, draftStore)
        val gate = SessionGate()
        return Fixture(
            repository = repository,
            requestStore = requestStore,
            gate = gate,
            executor =
                StoryCompletionExecutor(
                    requestStore,
                    draftStore,
                    repository,
                    gate,
                    this,
                ),
        )
    }

    @Test
    fun `제출은 영속 뒤 전송하고 성공 결과를 그 요청에만 반영한다`() =
        runTest {
            val fixture = fixture()
            fixture.repository.queuedCompletionResults += DomainResult.Success(CompletedStory("story-a", "A"))

            assertTrue(fixture.executor.submit(request("a")))
            assertTrue(fixture.executor.submit(request("b")))
            advanceUntilIdle()

            assertEquals(listOf("a", "b"), fixture.repository.completionCommands.map { it.requestId })
            assertEquals(
                CompletionOutcome.Completed(CompletedStory("story-a", "A")),
                fixture.requestStore.current
                    .single { it.requestId == "a" }
                    .outcome,
            )
            assertEquals(
                CompletionOutcome.Completed(CompletedStory("story-1", "완성 스토리")),
                fixture.requestStore.current
                    .single { it.requestId == "b" }
                    .outcome,
            )
        }

    @Test
    fun `영속 실패는 전송하지 않는다`() =
        runTest {
            val fixture = fixture()
            fixture.requestStore.submitSucceeds = false

            assertFalse(fixture.executor.submit(request("a")))
            advanceUntilIdle()

            assertTrue(fixture.repository.completionCommands.isEmpty())
        }

    @Test
    fun `네트워크 오류는 미확정으로 남고 새로고침이 복구 조회로 완료를 되찾는다`() =
        runTest {
            val fixture = fixture()
            fixture.repository.queuedCompletionResults += DomainResult.Failure(DomainError.Network)
            fixture.executor.submit(request("a"))
            advanceUntilIdle()
            assertEquals(
                CompletionOutcome.Pending,
                fixture.requestStore.current
                    .single()
                    .outcome,
            )

            fixture.repository.queuedCreationRequestResults +=
                DomainResult.Success(CreationRequestSnapshot.StoryReady(CompletedStory("story-a", "A")))
            fixture.executor.refreshCompletionRequests()
            advanceUntilIdle()

            assertEquals(
                CompletionOutcome.Completed(CompletedStory("story-a", "A")),
                fixture.requestStore.current
                    .single()
                    .outcome,
            )
        }

    @Test
    fun `새로고침의 404 는 미접수라 같은 requestId 로 다시 보낸다`() =
        runTest {
            val fixture = fixture(initial = listOf(request("a")))
            fixture.repository.queuedCreationRequestResults += DomainResult.Failure(notFound())

            fixture.executor.refreshCompletionRequests()
            advanceUntilIdle()

            assertEquals(listOf("a"), fixture.repository.completionCommands.map { it.requestId })
            assertTrue(
                fixture.requestStore.current
                    .single()
                    .outcome is CompletionOutcome.Completed,
            )
        }

    @Test
    fun `거절 응답은 복구 조회로 판정하고 서버가 모르는 요청만 실패로 확정한다`() =
        runTest {
            val fixture = fixture()
            fixture.repository.queuedCompletionResults +=
                DomainResult.Failure(DomainError.Server(status = 402, code = null, requestId = null))
            fixture.repository.queuedCreationRequestResults += DomainResult.Failure(notFound())
            fixture.executor.submit(request("a"))
            advanceUntilIdle()
            assertEquals(
                CompletionOutcome.Failed,
                fixture.requestStore.current
                    .single()
                    .outcome,
            )

            // 409 인데 서버가 진행 중이면 실패가 아니라 미확정이다.
            fixture.repository.queuedCompletionResults +=
                DomainResult.Failure(DomainError.Server(status = 409, code = null, requestId = null))
            fixture.repository.queuedCreationRequestResults += DomainResult.Success(CreationRequestSnapshot.Pending)
            fixture.executor.submit(request("b"))
            advanceUntilIdle()
            assertEquals(
                CompletionOutcome.Pending,
                fixture.requestStore.current
                    .single { it.requestId == "b" }
                    .outcome,
            )
        }

    @Test
    fun `실패 재시도는 같은 requestId 로 다시 보내고 조회 실패는 다른 요청 판정을 막지 않는다`() =
        runTest {
            val fixture =
                fixture(
                    initial =
                        listOf(
                            request("a").copy(outcome = CompletionOutcome.Failed),
                            request("b"),
                            request("c"),
                        ),
                )
            fixture.repository.queuedCreationRequestResults += DomainResult.Failure(DomainError.Network)
            fixture.repository.queuedCreationRequestResults += DomainResult.Success(CreationRequestSnapshot.Failed)

            fixture.executor.refreshCompletionRequests()
            advanceUntilIdle()
            val outcomes = fixture.requestStore.current.associate { it.requestId to it.outcome }
            assertEquals(CompletionOutcome.Failed, outcomes["a"])
            assertEquals(2, outcomes.values.count { it == CompletionOutcome.Failed })
            assertEquals(1, outcomes.values.count { it == CompletionOutcome.Pending })

            fixture.executor.retryCompletionRequest("a")
            advanceUntilIdle()
            assertEquals(listOf("a"), fixture.repository.completionCommands.map { it.requestId })
            assertTrue(
                fixture.requestStore.current
                    .single { it.requestId == "a" }
                    .outcome is CompletionOutcome.Completed,
            )
        }

    @Test
    fun `로그아웃 장벽 뒤에 도착한 결과는 반영하지 않는다`() =
        runTest {
            val fixture = fixture()
            val release = CompletableDeferred<Unit>()
            fixture.repository.completionGate = release
            fixture.executor.submit(request("a"))
            advanceUntilIdle()

            fixture.gate.raiseBarrier()
            release.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                CompletionOutcome.Pending,
                fixture.requestStore.current
                    .single()
                    .outcome,
            )
        }

    private fun request(id: String): StoryCompletionRequest =
        StoryCompletionRequest(
            command =
                StoryCompletionCommand(
                    requestId = id,
                    simpleCreationId = 10,
                    storylineId = 1,
                    additionalInfos = emptyList(),
                ),
            generationCommand = null,
            generation = sampleStorylineGeneration(),
            progress = CreationProgress(selectedStorylineIndex = 0),
            submittedAt = 1L,
        )

    private fun notFound() = DomainError.Server(status = 404, code = null, requestId = null)
}
