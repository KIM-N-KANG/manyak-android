package app.manyak.feature.create

import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorylineGenerationStoreTest {
    @Test
    fun `생성 성공은 결과를 발행하고 최초 생성 필드를 명시한다`() =
        runTest {
            val repository = FakeStoryCreationRepository()
            val store = StorylineGenerationStore(repository)

            store.generate(sampleGenerationInput())

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
            val store = StorylineGenerationStore(repository)

            store.generate(sampleGenerationInput())
            store.regenerate()

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
            val store = StorylineGenerationStore(repository)

            store.generate(sampleGenerationInput())
            assertEquals(StorylineGenerationState.Failed(previousResult = null), store.state.value)

            store.regenerate()

            val (first, second) = repository.generationCommands
            assertEquals(first.requestId, second.requestId)
            assertFalse(second.isRegenerated)
            assertTrue(store.state.value is StorylineGenerationState.Generated)
        }

    @Test
    fun `재생성 실패는 직전 성공 결과를 유지한다`() =
        runTest {
            val repository = FakeStoryCreationRepository()
            val store = StorylineGenerationStore(repository)

            store.generate(sampleGenerationInput())
            repository.queuedGenerationResults += DomainResult.Failure(DomainError.Network)
            store.regenerate()

            assertEquals(
                StorylineGenerationState.Failed(previousResult = sampleStorylineGeneration()),
                store.state.value,
            )
        }

    @Test
    fun `직전 명령이 없으면 재생성 요청을 보내지 않는다`() =
        runTest {
            val repository = FakeStoryCreationRepository()
            val store = StorylineGenerationStore(repository)

            store.regenerate()

            assertTrue(repository.generationCommands.isEmpty())
            assertEquals(StorylineGenerationState.Idle, store.state.value)
        }

    @Test
    fun `새 생성은 이전 퍼널의 결과를 버린다`() =
        runTest {
            val repository = FakeStoryCreationRepository()
            val store = StorylineGenerationStore(repository)

            store.generate(sampleGenerationInput())
            repository.queuedGenerationResults += DomainResult.Failure(DomainError.Network)
            store.generate(sampleGenerationInput())

            assertEquals(StorylineGenerationState.Failed(previousResult = null), store.state.value)
        }
}
