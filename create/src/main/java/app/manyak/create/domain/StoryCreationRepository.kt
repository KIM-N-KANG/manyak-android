package app.manyak.create.domain

import app.manyak.common.domain.error.DomainResult
import app.manyak.create.entity.CompletedStory
import app.manyak.create.entity.CreationRequestSnapshot
import app.manyak.create.entity.StoryCompletionCommand
import app.manyak.create.entity.StoryTag
import app.manyak.create.entity.StorylineGeneration
import app.manyak.create.entity.StorylineGenerationCommand
import app.manyak.create.entity.StorylineRating

interface StoryCreationRepository {
    /** 활성화된 제공 태그 목록. 카테고리 → 정렬 순서로 정렬되어 온다. */
    suspend fun tags(): DomainResult<List<StoryTag>>

    /** 태그 선택으로 스토리라인 3개를 생성한다. AI 동기 호출이라 오래 걸릴 수 있다. */
    suspend fun generateStorylines(command: StorylineGenerationCommand): DomainResult<StorylineGeneration>

    /** 선택한 스토리라인과 추가 정보로 최종 스토리를 완성한다. AI 동기 호출이라 오래 걸릴 수 있다. */
    suspend fun completeStory(command: StoryCompletionCommand): DomainResult<CompletedStory>

    /** 응답을 못 받은 생성·완성 요청의 진행 상태·결과를 복구 조회한다. 미존재·타인 요청은 404 다. */
    suspend fun creationRequest(requestId: String): DomainResult<CreationRequestSnapshot>

    /** 평가 설정. 스토리라인당 1건 upsert 라 새 평가가 기존 평가를 덮는다. */
    suspend fun rateStoryline(
        storylineId: Long,
        rating: StorylineRating,
    ): DomainResult<Unit>

    /** 평가 취소. 평가가 없어도 성공하는 멱등 동작이다. */
    suspend fun clearStorylineRating(storylineId: Long): DomainResult<Unit>
}
