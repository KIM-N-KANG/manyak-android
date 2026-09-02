package app.manyak.feature.studio

import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.StoryDetail
import app.manyak.core.domain.story.StoryReportReason
import app.manyak.core.domain.story.StoryRepository
import app.manyak.core.domain.story.StorySummary
import kotlinx.coroutines.yield

internal fun sampleStories(): List<StorySummary> =
    listOf(
        StorySummary(
            id = "story-1",
            title = "두 번째 시계공",
            authorNickname = null,
            thumbnailUrl = "https://cdn.manyak.app/thumbnails/1_sm.png",
            oneLineIntro = "멈춘 시계탑을 고치는 견습공의 하루",
            genres = listOf("판타지", "미스터리"),
            turnCount = 128,
            createdDate = "2026-08-03",
        ),
        StorySummary(
            id = "story-2",
            title = "달빛 아래의 계약",
            authorNickname = null,
            thumbnailUrl = null,
            oneLineIntro = "",
            genres = emptyList(),
            turnCount = 0,
            createdDate = null,
        ),
    )

/** 조회 결과는 큐에서 꺼내고 비면 성공 샘플을 돌려준다. */
internal class FakeStoryRepository : StoryRepository {
    var myStoriesCallCount = 0
    val queuedResults = ArrayDeque<DomainResult<List<StorySummary>>>()

    override suspend fun originalStories(): DomainResult<List<StorySummary>> = DomainResult.Success(emptyList())

    override suspend fun storyDetail(storyId: String): DomainResult<StoryDetail> =
        DomainResult.Failure(DomainError.Unknown)

    override suspend fun myStories(): DomainResult<List<StorySummary>> {
        // 실제 네트워크 호출처럼 반드시 한 번 양보한다.
        yield()
        myStoriesCallCount++
        return queuedResults.removeFirstOrNull() ?: DomainResult.Success(sampleStories())
    }

    val deletedStoryIds = mutableListOf<String>()
    val queuedDeleteResults = ArrayDeque<DomainResult<Unit>>()

    override suspend fun deleteStory(storyId: String): DomainResult<Unit> {
        yield()
        deletedStoryIds += storyId
        return queuedDeleteResults.removeFirstOrNull() ?: DomainResult.Success(Unit)
    }

    override suspend fun reportStory(
        storyId: String,
        reason: StoryReportReason,
        detail: String?,
    ): DomainResult<Unit> = DomainResult.Success(Unit)
}
