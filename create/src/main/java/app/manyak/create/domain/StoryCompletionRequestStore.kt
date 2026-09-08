package app.manyak.create.domain

import app.manyak.create.entity.CompletedStory
import app.manyak.create.entity.StoryCompletionRequest
import kotlinx.coroutines.flow.Flow

/**
 * 제출된 완성 요청의 영속 저장소. 결과 반영과 삭제는 requestId 단위이며, 전량 삭제는
 * 세션 종료 정리만 부른다.
 */
interface StoryCompletionRequestStore {
    val requests: Flow<List<StoryCompletionRequest>>

    suspend fun readAll(): List<StoryCompletionRequest>

    /** 요청 행 삽입과 해당 초안 해제를 원자적으로 처리한다. 영속에 성공했을 때만 true. */
    suspend fun submit(request: StoryCompletionRequest): Boolean

    suspend fun markCompleted(
        requestId: String,
        story: CompletedStory,
    ): Boolean

    suspend fun markFailed(requestId: String): Boolean

    suspend fun markPending(requestId: String): Boolean

    suspend fun delete(requestId: String): Boolean
}
