package app.manyak.home.domain

import app.manyak.common.domain.error.DomainResult
import app.manyak.home.entity.StoryListQuery
import app.manyak.home.entity.StoryPage

interface HomeRepository {
    /** 발행·공개된 스토리 한 페이지. [cursor] 가 없으면 첫 페이지이고 서버 순서를 그대로 유지한다. */
    suspend fun publicStories(
        query: StoryListQuery,
        cursor: String? = null,
    ): DomainResult<StoryPage>
}
