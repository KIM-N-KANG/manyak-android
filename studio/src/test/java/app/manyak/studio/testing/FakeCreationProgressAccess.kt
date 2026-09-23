package app.manyak.studio.testing

import app.manyak.common.domain.story.CreationProgressAccess
import app.manyak.common.entity.story.CompletionRequestSummary
import app.manyak.common.entity.story.CreationProgressSummary
import kotlinx.coroutines.flow.Flow
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

    val currentDrafts: List<CreationProgressSummary> get() = draftState.value
    val currentRequests: List<CompletionRequestSummary> get() = requestState.value
    var refreshCount = 0
    val retriedRequestIds = mutableListOf<String>()
    val deletedRequestIds = mutableListOf<String>()
    var discardSucceeds = true
    var deleteSucceeds = true

    fun emitRequests(requests: List<CompletionRequestSummary>) {
        requestState.value = requests
    }

    override suspend fun discard(draftId: String): Boolean {
        if (!discardSucceeds) return false
        draftState.value = draftState.value.filterNot { it.draftId == draftId }
        return true
    }

    override suspend fun refreshCompletionRequests() {
        refreshCount++
    }

    override suspend fun retryCompletionRequest(requestId: String) {
        retriedRequestIds += requestId
    }

    override suspend fun deleteCompletionRequest(requestId: String): Boolean {
        if (!deleteSucceeds) return false
        deletedRequestIds += requestId
        requestState.value = requestState.value.filterNot { it.requestId == requestId }
        return true
    }
}
