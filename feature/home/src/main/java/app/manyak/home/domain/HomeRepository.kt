package app.manyak.home.domain

import app.manyak.common.domain.error.DomainResult
import app.manyak.home.entity.HomeSection
import app.manyak.home.entity.StoryListQuery
import app.manyak.home.entity.StoryPage

interface HomeRepository {
    /** 발행·공개된 스토리 한 페이지. [cursor] 가 없으면 첫 페이지이고 서버 순서를 그대로 유지한다. */
    suspend fun publicStories(
        query: StoryListQuery,
        cursor: String? = null,
    ): DomainResult<StoryPage>

    /** 홈 섹션 전체. 카드가 없는 섹션도 그대로 담아 온다. */
    suspend fun homeSections(): DomainResult<List<HomeSection>>

    /** 탐색 한 페이지. [cursor] 가 없으면 새로 섞은 순서의 첫 페이지다. */
    suspend fun exploreStories(cursor: String? = null): DomainResult<StoryPage>

    /** 공개 스토리 검색 한 페이지. [query] 는 앞뒤 공백을 뺀 2자 이상이고 [cursor] 가 없으면 첫 페이지다. */
    suspend fun searchStories(
        query: String,
        cursor: String? = null,
    ): DomainResult<StoryPage>

    /** 대표 장르 이름. 서버가 정한 대표 순서다. */
    suspend fun featuredGenres(): DomainResult<List<String>>
}
