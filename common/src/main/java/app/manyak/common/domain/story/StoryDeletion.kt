package app.manyak.common.domain.story

import app.manyak.common.domain.error.DomainResult

interface StoryDeletion {
    /**
     * 내가 만든 스토리 삭제(소프트). 이미 삭제된 스토리도 성공으로 본다 — 목록에서 사라지는
     * 목표가 이미 달성된 상태라서다.
     */
    suspend fun deleteStory(storyId: String): DomainResult<Unit>
}
