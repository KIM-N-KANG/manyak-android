package app.manyak.common.domain.story

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.story.StorySummary

/** 다른 기능에는 목록 조회 조건을 숨기고 채팅이 많은 오리지널의 첫 페이지만 공개한다. */
interface PopularOriginalStories {
    suspend fun firstPage(): DomainResult<List<StorySummary>>
}
