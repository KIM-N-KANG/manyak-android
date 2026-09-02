package app.manyak.feature.chat

import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.StoryDetail
import app.manyak.core.domain.story.StoryReportReason
import app.manyak.core.domain.story.StoryRepository
import app.manyak.core.domain.story.StorySummary
import kotlinx.coroutines.yield

/** 채팅방이 쓰는 것은 신고 하나뿐이라 나머지는 계약만 채운다. */
internal class FakeStoryRepository : StoryRepository {
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

    override suspend fun originalStories(): DomainResult<List<StorySummary>> = DomainResult.Success(emptyList())

    override suspend fun myStories(): DomainResult<List<StorySummary>> = DomainResult.Success(emptyList())

    override suspend fun storyDetail(storyId: String): DomainResult<StoryDetail> =
        DomainResult.Failure(DomainError.Unknown)

    override suspend fun deleteStory(storyId: String): DomainResult<Unit> = DomainResult.Success(Unit)
}
