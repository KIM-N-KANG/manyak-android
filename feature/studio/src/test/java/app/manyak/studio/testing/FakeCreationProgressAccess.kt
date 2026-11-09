package app.manyak.studio.testing

import app.manyak.common.domain.story.CreationProgressAccess
import app.manyak.common.entity.story.CompletionFailure
import app.manyak.common.entity.story.CompletionRequestSummary
import app.manyak.common.entity.story.CreationProgressSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** 초안·요청 흐름의 인메모리 구현. 호출 기록으로 제작 탭의 갱신·삭제 시점을 검증한다. */
internal class FakeCreationProgressAccess(
    drafts: List<CreationProgressSummary> = emptyList(),
    requests: List<CompletionRequestSummary> = emptyList(),
) : CreationProgressAccess {
    private val draftState = MutableStateFlow(drafts)
    private val requestState = MutableStateFlow(requests)

    override val drafts: Flow<List<CreationProgressSummary>> = draftState
    override val completionRequests: Flow<List<CompletionRequestSummary>> = requestState
    override val submissionChanges = MutableSharedFlow<Unit>()
    override val completionFailures = MutableSharedFlow<CompletionFailure>()

    val currentDrafts: List<CreationProgressSummary> get() = draftState.value
    val currentRequests: List<CompletionRequestSummary> get() = requestState.value
    var refreshCount = 0
    var draftRefreshCount = 0
    val deletedRequestIds = mutableListOf<String>()
    var discardSucceeds = true
    var draftRefreshGate: CompletableDeferred<Unit>? = null
    val discarded = mutableListOf<Pair<String, Boolean>>()
    var deleteSucceeds = true

    fun emitRequests(requests: List<CompletionRequestSummary>) {
        requestState.value = requests
    }

    override suspend fun discard(
        draftId: String,
        onServer: Boolean,
    ): Boolean {
        if (!discardSucceeds) return false
        discarded += draftId to onServer
        draftState.value = draftState.value.filterNot { it.draftId == draftId && it.onServer == onServer }
        return true
    }

    override suspend fun refreshDrafts() {
        draftRefreshCount++
        draftRefreshGate?.await()
    }

    override suspend fun refreshCompletionRequests() {
        refreshCount++
    }

    override suspend fun deleteCompletionRequest(requestId: String): Boolean {
        if (!deleteSucceeds) return false
        deletedRequestIds += requestId
        requestState.value = requestState.value.filterNot { it.requestId == requestId }
        return true
    }
}
