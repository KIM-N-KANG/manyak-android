package app.manyak.report.domain

import app.manyak.common.domain.error.DomainResult
import app.manyak.report.entity.StoryReportReason

interface ReportRepository {
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
