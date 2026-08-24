package app.manyak.core.domain.story

import app.manyak.core.domain.error.DomainResult

interface StoryCreationRepository {
    /** 활성화된 제공 태그 목록. 카테고리 → 정렬 순서로 정렬되어 온다. */
    suspend fun tags(): DomainResult<List<StoryTag>>

    /** 태그 선택으로 스토리라인 3개를 생성한다. AI 동기 호출이라 오래 걸릴 수 있다. */
    suspend fun generateStorylines(command: StorylineGenerationCommand): DomainResult<StorylineGeneration>

    /** 평가 설정. 스토리라인당 1건 upsert 라 새 평가가 기존 평가를 덮는다. */
    suspend fun rateStoryline(
        storylineId: Long,
        rating: StorylineRating,
    ): DomainResult<Unit>

    /** 평가 취소. 평가가 없어도 성공하는 멱등 동작이다. */
    suspend fun clearStorylineRating(storylineId: Long): DomainResult<Unit>
}
