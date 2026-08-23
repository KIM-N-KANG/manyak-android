package app.manyak.core.domain.story

import app.manyak.core.domain.error.DomainResult

/** 간편 제작 퍼널의 데이터 동작. 스토리라인 생성·완성은 해당 단계를 구현할 때 더한다. */
interface StoryCreationRepository {
    /** 활성화된 제공 태그 목록. 카테고리 → 정렬 순서로 정렬되어 온다. */
    suspend fun tags(): DomainResult<List<StoryTag>>
}
