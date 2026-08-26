package app.manyak.core.domain.story

import app.manyak.core.domain.error.DomainResult

interface StoryRepository {
    /**
     * 마냑 공식 계정의 오리지널 스토리 목록. 서버 등록순을 그대로 유지하며, 공식 계정이 설정되지
     * 않은 환경은 빈 목록이다.
     */
    suspend fun originalStories(): DomainResult<List<StorySummary>>

    /**
     * 내가 만든 스토리 목록. 서버가 주는 생성 최신순을 그대로 유지하며, 삭제된 스토리는 서버가
     * 제외한다. 인증이 필요한 조회다.
     */
    suspend fun myStories(): DomainResult<List<StorySummary>>

    /**
     * 내가 만든 스토리 삭제(소프트). 이미 삭제된 스토리도 성공으로 본다 — 목록에서 사라지는
     * 목표가 이미 달성된 상태라서다.
     */
    suspend fun deleteStory(storyId: String): DomainResult<Unit>
}
