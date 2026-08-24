package app.manyak.core.domain.story

import app.manyak.core.domain.error.DomainResult

interface StoryCreationRepository {
    /** 활성화된 제공 태그 목록. 카테고리 → 정렬 순서로 정렬되어 온다. */
    suspend fun tags(): DomainResult<List<StoryTag>>
}
