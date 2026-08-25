package app.manyak.core.data.repository

import app.manyak.core.data.api.SimpleStoryApi
import app.manyak.core.data.api.StoryGenerationApi
import app.manyak.core.data.api.StoryRatingApi
import app.manyak.core.data.api.apiCall
import app.manyak.core.data.api.dto.toDomain
import app.manyak.core.data.api.dto.toDomainOrNull
import app.manyak.core.data.api.dto.toRequestDto
import app.manyak.core.data.api.emptyBodyApiCall
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.error.map
import app.manyak.core.domain.story.CompletedStory
import app.manyak.core.domain.story.StoryCompletionCommand
import app.manyak.core.domain.story.StoryCreationRepository
import app.manyak.core.domain.story.StoryTag
import app.manyak.core.domain.story.StorylineGeneration
import app.manyak.core.domain.story.StorylineGenerationCommand
import app.manyak.core.domain.story.StorylineRating
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoryCreationRepositoryImpl
    @Inject
    constructor(
        private val simpleStoryApi: SimpleStoryApi,
        private val storyGenerationApi: StoryGenerationApi,
        private val storyRatingApi: StoryRatingApi,
    ) : StoryCreationRepository {
        override suspend fun tags(): DomainResult<List<StoryTag>> =
            apiCall { simpleStoryApi.tags() }.map { tags -> tags.mapNotNull { it.toDomainOrNull() } }

        // 자동 재시도를 두지 않는다 — 재시도는 사용자의 "다시 만들기"가 같은 requestId 로 수행한다.
        override suspend fun generateStorylines(
            command: StorylineGenerationCommand,
        ): DomainResult<StorylineGeneration> =
            apiCall { storyGenerationApi.generateStorylines(command.toRequestDto()) }.map { it.toDomain() }

        override suspend fun completeStory(command: StoryCompletionCommand): DomainResult<CompletedStory> =
            apiCall { storyGenerationApi.completeStory(command.toRequestDto()) }.map { it.toDomain() }

        override suspend fun rateStoryline(
            storylineId: Long,
            rating: StorylineRating,
        ): DomainResult<Unit> = apiCall { storyRatingApi.setRating(storylineId, rating.toRequestDto()) }.map { }

        override suspend fun clearStorylineRating(storylineId: Long): DomainResult<Unit> =
            emptyBodyApiCall { storyRatingApi.deleteRating(storylineId) }
    }
