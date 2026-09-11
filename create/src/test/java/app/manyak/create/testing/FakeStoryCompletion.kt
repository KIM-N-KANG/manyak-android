package app.manyak.create.testing

import app.manyak.create.domain.StoryCompletionRequestStore
import app.manyak.create.domain.StoryCompletionSubmitter
import app.manyak.create.entity.CompletedStory
import app.manyak.create.entity.CompletionOutcome
import app.manyak.create.entity.StoryCompletionRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** 제출 기록만 남기는 실행자. 영속 실패 시나리오는 [submitSucceeds] 로 만든다. */
internal class FakeStoryCompletionSubmitter(
    var submitSucceeds: Boolean = true,
) : StoryCompletionSubmitter {
    val submitted = mutableListOf<StoryCompletionRequest>()

    override suspend fun submit(request: StoryCompletionRequest): Boolean {
        if (!submitSucceeds) return false
        submitted += request
        return true
    }
}

/** 요청 테이블의 인메모리 구현. 제출은 [draftStore] 의 초안을 함께 내려 트랜잭션을 흉내 낸다. */
internal class FakeStoryCompletionRequestStore(
    initial: List<StoryCompletionRequest> = emptyList(),
    private val draftStore: FakePendingStoryCreationStore? = null,
    var submitSucceeds: Boolean = true,
) : StoryCompletionRequestStore {
    private val state = MutableStateFlow(initial)

    override val requests: Flow<List<StoryCompletionRequest>> = state

    val current: List<StoryCompletionRequest> get() = state.value

    override suspend fun claimUnowned() = Unit

    override suspend fun readAll(): List<StoryCompletionRequest> = state.value

    override suspend fun submit(request: StoryCompletionRequest): Boolean {
        if (!submitSucceeds) return false
        draftStore?.clear()
        state.value = state.value.filterNot { it.requestId == request.requestId } + request
        return true
    }

    override suspend fun markCompleted(
        requestId: String,
        story: CompletedStory,
    ): Boolean = update(requestId) { it.copy(outcome = CompletionOutcome.Completed(story)) }

    override suspend fun markFailed(requestId: String): Boolean =
        update(requestId) { it.copy(outcome = CompletionOutcome.Failed) }

    override suspend fun markPending(requestId: String): Boolean =
        update(requestId) { it.copy(outcome = CompletionOutcome.Pending) }

    override suspend fun delete(requestId: String): Boolean {
        state.value = state.value.filterNot { it.requestId == requestId }
        return true
    }

    private fun update(
        requestId: String,
        transform: (StoryCompletionRequest) -> StoryCompletionRequest,
    ): Boolean {
        if (state.value.none { it.requestId == requestId }) return false
        state.value = state.value.map { if (it.requestId == requestId) transform(it) else it }
        return true
    }
}
