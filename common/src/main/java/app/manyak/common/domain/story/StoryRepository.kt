package app.manyak.common.domain.story

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.story.StoryDetail
import app.manyak.common.entity.story.StoryReportReason
import app.manyak.common.entity.story.StorySummary

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
     * 스토리 상세. 회원 세션으로 조회한다 — 내가 만든 스토리는 기본 비공개라 익명 요청에는
     * 404 가 오고, 본 엔딩도 회원 집계라 토큰이 없으면 비어서 온다.
     *
     * 없는 스토리와 읽을 수 없는 스토리는 서버가 모두 404 로 돌려준다(존재 여부 비노출).
     */
    suspend fun storyDetail(storyId: String): DomainResult<StoryDetail>

    /**
     * 내가 만든 스토리 삭제(소프트). 이미 삭제된 스토리도 성공으로 본다 — 목록에서 사라지는
     * 목표가 이미 달성된 상태라서다.
     */
    suspend fun deleteStory(storyId: String): DomainResult<Unit>

    /**
     * 스토리 신고. 인증이 필요하며, 같은 회원이 같은 스토리를 다시 신고해도 서버가 멱등으로 흡수한다.
     * 접수만 하는 경로라 결과로 돌려줄 상태가 없다.
     *
     * @param detail 자유 서술(500자 이내). 사유만으로 맥락이 부족할 때 채운다.
     */
    suspend fun reportStory(
        storyId: String,
        reason: StoryReportReason,
        detail: String?,
    ): DomainResult<Unit>
}
