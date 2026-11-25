package app.manyak.common.domain.story

import app.manyak.common.entity.story.CompletionFailure
import app.manyak.common.entity.story.CompletionRequestSummary
import app.manyak.common.entity.story.CreationProgressSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** 제작 기능이 제작 탭에 여는 최소 계약. 편집 초안 여러 개와 완성 요청 여러 개를 따로 전달한다. */
interface CreationProgressAccess {
    /** 폼을 떠난 뒤 완료된 등록과 수정도 열려 있는 제작 목록에 알린다. */
    val submissionChanges: Flow<Unit> get() = emptyFlow()

    /** 완성에 실패한 요청이 추가 정보 단계 초안으로 되돌아왔다. 보고 있는 제작 탭이 안내한다. */
    val completionFailures: Flow<CompletionFailure> get() = emptyFlow()

    /** 편집 초안. 처음 임시 저장한 시각 최신순이고, 시각이 없는 이전 초안은 맨 뒤다. */
    val drafts: Flow<List<CreationProgressSummary>>

    /** 완성 요청. 처음 임시 저장한 시각 최신순이고, 시각이 없는 이전 요청은 제출 최신순으로 맨 뒤다. */
    val completionRequests: Flow<List<CompletionRequestSummary>>

    /** 서버에 둔 초안 목록을 다시 받는다. 받지 못해도 기기 안 초안은 그대로 보인다. */
    suspend fun refreshDrafts() {}

    /** 편집 초안 하나만 폐기한다. 다른 초안과 완성 요청은 건드리지 않는다. [onServer]는 카드의 [CreationProgressSummary.onServer]다. */
    suspend fun discard(
        draftId: String,
        onServer: Boolean = false,
    ): Boolean

    /** 미확정 요청의 서버 상태를 조회하고 미접수 요청을 재전송한다. 요청별 실패는 서로 격리된다. */
    suspend fun refreshCompletionRequests()

    /** 요청 하나를 지운다. 완료된 요청이 목록의 일반 카드로 바뀐 뒤에 쓴다. */
    suspend fun deleteCompletionRequest(requestId: String): Boolean
}
