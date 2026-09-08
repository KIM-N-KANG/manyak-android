package app.manyak.create.domain

import app.manyak.create.entity.CompletedStory
import app.manyak.create.entity.StoryCompletionRequest
import kotlinx.coroutines.flow.Flow

/**
 * 제출된 완성 요청의 영속 저장소. 결과 반영과 삭제는 requestId 단위이고, 행은 회원별로 격리되어
 * 로그아웃해도 남는다 — 같은 회원이 돌아오면 완성 중이던 요청이 이어진다.
 */
interface StoryCompletionRequestStore {
    /** 소유자를 모르는 이전 버전 행을 지금 회원의 것으로 넘긴다. */
    suspend fun claimUnowned()

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
