package app.manyak.feature.home

import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.StoryRepository
import app.manyak.core.domain.story.StorySummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.yield

internal fun sampleStories(): List<StorySummary> =
    listOf(
        StorySummary(
            id = "story-1",
            title = "두 번째 시계공",
            authorNickname = "마냑",
            thumbnailUrl = "https://cdn.manyak.app/thumbnails/1_sm.png",
            oneLineIntro = "",
            genres = emptyList(),
            turnCount = 128,
        ),
        StorySummary(
            id = "story-2",
            title = "달빛 아래의 계약",
            authorNickname = "마냑",
            thumbnailUrl = null,
            oneLineIntro = "",
            genres = emptyList(),
            turnCount = 0,
        ),
    )

/** 조회 결과는 큐에서 꺼내고 비면 성공 샘플을 돌려준다. */
internal class FakeStoryRepository : StoryRepository {
    var originalStoriesCallCount = 0
    val queuedResults = ArrayDeque<DomainResult<List<StorySummary>>>()

    /** 채우면 조회가 여기서 멈춘다 — 조회가 진행 중인 동안의 동작을 볼 때 쓴다. */
    var inFlightGate: CompletableDeferred<Unit>? = null

    override suspend fun originalStories(): DomainResult<List<StorySummary>> {
        // 실제 네트워크 호출처럼 반드시 한 번 양보한다.
        yield()
        originalStoriesCallCount++
        inFlightGate?.await()
        return queuedResults.removeFirstOrNull() ?: DomainResult.Success(sampleStories())
    }

    override suspend fun myStories(): DomainResult<List<StorySummary>> = DomainResult.Success(emptyList())

    override suspend fun deleteStory(storyId: String): DomainResult<Unit> = DomainResult.Success(Unit)
}
