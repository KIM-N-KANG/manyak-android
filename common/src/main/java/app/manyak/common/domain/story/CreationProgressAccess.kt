package app.manyak.common.domain.story

import app.manyak.common.entity.story.CompletionRequestSummary
import app.manyak.common.entity.story.CreationProgressSummary
import kotlinx.coroutines.flow.Flow

/** 제작 기능이 제작 탭에 여는 최소 계약. 편집 초안 하나와 완성 요청 여러 개를 따로 전달한다. */
interface CreationProgressAccess {
    /** 편집 슬롯의 초안. 없으면 null. */
    val progress: Flow<CreationProgressSummary?>

    /** 제출 최신순 완성 요청. */
    val completionRequests: Flow<List<CompletionRequestSummary>>

    /** 편집 초안만 폐기한다. 완성 요청은 건드리지 않는다. */
    suspend fun discard(): Boolean

    /** 미확정 요청의 서버 상태를 조회하고 미접수 요청을 재전송한다. 요청별 실패는 서로 격리된다. */
    suspend fun refreshCompletionRequests()

    /** 실패한 요청을 같은 requestId 로 다시 보낸다. */
    suspend fun retryCompletionRequest(requestId: String)

    /** 요청 하나를 지운다. 완료된 요청이 목록의 일반 카드로 바뀐 뒤와 실패 요청 삭제에 쓴다. */
    suspend fun deleteCompletionRequest(requestId: String): Boolean
}
