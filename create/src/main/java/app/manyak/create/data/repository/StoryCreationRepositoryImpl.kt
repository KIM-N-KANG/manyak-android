package app.manyak.create.data.repository

import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.create.data.api.CreationRequestApi
import app.manyak.create.data.api.SimpleStoryApi
import app.manyak.create.data.api.StoryGenerationApi
import app.manyak.create.data.api.StoryRatingApi
import app.manyak.create.data.api.dto.toDomain
import app.manyak.create.data.api.dto.toDomainOrNull
import app.manyak.create.data.api.dto.toRequestDto
import app.manyak.create.domain.StoryCreationRepository
import app.manyak.create.entity.CompletedStory
import app.manyak.create.entity.CreationRequestSnapshot
import app.manyak.create.entity.StoryCompletionCommand
import app.manyak.create.entity.StoryTag
import app.manyak.create.entity.StorylineGeneration
import app.manyak.create.entity.StorylineGenerationCommand
import app.manyak.create.entity.StorylineRating
import app.manyak.network.data.api.apiCall
import app.manyak.network.data.api.emptyBodyApiCall
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoryCreationRepositoryImpl
    @Inject
    constructor(
        private val simpleStoryApi: SimpleStoryApi,
        private val storyGenerationApi: StoryGenerationApi,
        private val storyRatingApi: StoryRatingApi,
        private val creationRequestApi: CreationRequestApi,
        private val json: Json,
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

        override suspend fun creationRequest(requestId: String): DomainResult<CreationRequestSnapshot> =
            when (val result = apiCall { creationRequestApi.creationRequest(requestId) }) {
                is DomainResult.Success ->
                    // 단계별 결과 해석은 응답 수신 뒤라 apiCall 의 직렬화 판정 밖이다. 여기서 같은 오류로 접는다.
                    try {
                        result.value
                            .toDomainOrNull(json)
                            ?.let { DomainResult.Success(it) }
                            ?: DomainResult.Failure(DomainError.Serialization)
                    } catch (_: SerializationException) {
                        DomainResult.Failure(DomainError.Serialization)
                    }

                is DomainResult.Failure -> result
            }

        override suspend fun rateStoryline(
            storylineId: Long,
            rating: StorylineRating,
        ): DomainResult<Unit> = apiCall { storyRatingApi.setRating(storylineId, rating.toRequestDto()) }.map { }

        override suspend fun clearStorylineRating(storylineId: Long): DomainResult<Unit> =
            emptyBodyApiCall { storyRatingApi.deleteRating(storylineId) }
    }
