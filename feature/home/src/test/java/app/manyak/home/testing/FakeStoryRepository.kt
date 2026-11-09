package app.manyak.home.testing

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.story.StorySummary
import app.manyak.home.domain.HomeRepository
import app.manyak.home.entity.HomeSection
import app.manyak.home.entity.StoryListQuery
import app.manyak.home.entity.StoryPage
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
            likeCount = 0,
            turnCount = 128,
            createdDate = "2026-08-03",
            isOriginal = true,
        ),
        StorySummary(
            id = "story-2",
            title = "달빛 아래의 계약",
            authorNickname = "마냑",
            thumbnailUrl = null,
            oneLineIntro = "",
            genres = emptyList(),
            likeCount = 0,
            turnCount = 0,
            createdDate = null,
            isOriginal = false,
        ),
    )

/** 조회 결과는 큐에서 꺼내고 비면 마지막 페이지인 성공 샘플을 돌려준다. */
internal class FakeStoryRepository : HomeRepository {
    /** 받은 요청의 조건과 커서. 순서대로 쌓인다. */
    val requests = mutableListOf<Pair<StoryListQuery, String?>>()
    val queuedResults = ArrayDeque<DomainResult<StoryPage>>()

    /** 채우면 조회가 여기서 멈춘다 — 조회가 진행 중인 동안의 동작을 볼 때 쓴다. */
    var inFlightGate: CompletableDeferred<Unit>? = null

    override suspend fun publicStories(
        query: StoryListQuery,
        cursor: String?,
    ): DomainResult<StoryPage> {
        // 실제 네트워크 호출처럼 반드시 한 번 양보한다.
        yield()
        requests += query to cursor
        inFlightGate?.await()
        return queuedResults.removeFirstOrNull() ?: DomainResult.Success(StoryPage(sampleStories(), nextCursor = null))
    }

    var homeRequests = 0
    val queuedHomeResults = ArrayDeque<DomainResult<List<HomeSection>>>()

    override suspend fun homeSections(): DomainResult<List<HomeSection>> {
        yield()
        homeRequests += 1
        inFlightGate?.await()
        return queuedHomeResults.removeFirstOrNull()
            ?: DomainResult.Success(listOf(HomeSection(id = null, name = "인기 스토리", stories = sampleStories())))
    }

    /** 탐색 요청의 커서. 순서대로 쌓인다. */
    val exploreRequests = mutableListOf<String?>()
    val queuedExploreResults = ArrayDeque<DomainResult<StoryPage>>()

    override suspend fun exploreStories(cursor: String?): DomainResult<StoryPage> {
        yield()
        exploreRequests += cursor
        inFlightGate?.await()
        return queuedExploreResults.removeFirstOrNull()
            ?: DomainResult.Success(StoryPage(sampleStories(), nextCursor = null))
    }

    /** 검색 요청의 검색어와 커서. 순서대로 쌓인다. */
    val searchRequests = mutableListOf<Pair<String, String?>>()
    val queuedSearchResults = ArrayDeque<DomainResult<StoryPage>>()

    override suspend fun searchStories(
        query: String,
        cursor: String?,
    ): DomainResult<StoryPage> {
        yield()
        searchRequests += query to cursor
        inFlightGate?.await()
        return queuedSearchResults.removeFirstOrNull()
            ?: DomainResult.Success(StoryPage(sampleStories(), nextCursor = null))
    }

    var featuredGenresResult: DomainResult<List<String>> = DomainResult.Success(listOf("로맨스", "판타지"))

    override suspend fun featuredGenres(): DomainResult<List<String>> = featuredGenresResult
}
