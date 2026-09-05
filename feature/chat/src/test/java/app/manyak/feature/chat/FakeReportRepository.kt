package app.manyak.feature.chat

import app.manyak.common.domain.error.DomainResult
import app.manyak.report.domain.ReportRepository
import app.manyak.report.entity.StoryReportReason
import kotlinx.coroutines.yield

/** 채팅 목록·채팅방이 제출한 신고를 기록한다. */
internal class FakeReportRepository : ReportRepository {
    /** 접수된 신고 — 채팅방이 어느 스토리를 무엇으로 신고했는지 확인할 때 쓴다. */
    val reportedStories = mutableListOf<Triple<String, StoryReportReason, String?>>()
    val queuedReportResults = ArrayDeque<DomainResult<Unit>>()

    override suspend fun reportStory(
        storyId: String,
        reason: StoryReportReason,
        detail: String?,
    ): DomainResult<Unit> {
        yield()
        reportedStories += Triple(storyId, reason, detail)
        return queuedReportResults.removeFirstOrNull() ?: DomainResult.Success(Unit)
    }
}
