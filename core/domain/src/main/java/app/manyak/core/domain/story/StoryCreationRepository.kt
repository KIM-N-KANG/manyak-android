package app.manyak.core.domain.story

import app.manyak.core.domain.error.DomainResult

interface StoryCreationRepository {
    /** 활성화된 제공 태그 목록. 카테고리 → 정렬 순서로 정렬되어 온다. */
    suspend fun tags(): DomainResult<List<StoryTag>>

    /** 태그 선택으로 스토리라인 3개를 생성한다. AI 동기 호출이라 오래 걸릴 수 있다. */
    suspend fun generateStorylines(command: StorylineGenerationCommand): DomainResult<StorylineGeneration>
}
