package app.manyak.studio.domain

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.story.StoryDeletion
import app.manyak.common.entity.story.StorySummary
import app.manyak.studio.entity.StorySubmission

interface StudioRepository : StoryDeletion {
    suspend fun submissions(): DomainResult<List<StorySubmission>>

    suspend fun deleteSubmission(submissionId: String): DomainResult<Unit>

    /**
     * 내가 만든 스토리 목록. 서버가 주는 생성 최신순을 그대로 유지하며, 삭제된 스토리는 서버가
     * 제외한다. 인증이 필요한 조회다.
     */
    suspend fun myStories(): DomainResult<List<StorySummary>>
}
