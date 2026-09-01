package app.manyak.core.data.repository

import app.manyak.core.data.api.StoryApi
import app.manyak.core.data.api.StoryDetailApi
import app.manyak.core.data.api.UserApi
import app.manyak.core.data.api.apiCall
import app.manyak.core.data.api.dto.toDomain
import app.manyak.core.data.api.emptyBodyApiCall
import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.error.map
import app.manyak.core.domain.story.StoryDetail
import app.manyak.core.domain.story.StoryRepository
import app.manyak.core.domain.story.StorySummary
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoryRepositoryImpl
    @Inject
    constructor(
        private val storyApi: StoryApi,
        private val storyDetailApi: StoryDetailApi,
        private val userApi: UserApi,
    ) : StoryRepository {
        override suspend fun originalStories(): DomainResult<List<StorySummary>> =
            apiCall { storyApi.originalStories() }.map { stories -> stories.map { story -> story.toDomain() } }

        // 내 스토리는 보호 경로라 인증 클라이언트를 쓰는 UserApi 쪽에 정의되어 있다.
        override suspend fun myStories(): DomainResult<List<StorySummary>> =
            apiCall { userApi.myStories() }.map { stories -> stories.map { story -> story.toDomain() } }

        // 상세는 회원 세션이 필요해 오리지널 목록과 다른 클라이언트를 쓴다(StoryDetailApi).
        override suspend fun storyDetail(storyId: String): DomainResult<StoryDetail> =
            apiCall { storyDetailApi.storyDetail(storyId) }.map { story -> story.toDomain() }

        override suspend fun deleteStory(storyId: String): DomainResult<Unit> {
            val result = emptyBodyApiCall { userApi.deleteStory(storyId) }
            val error = (result as? DomainResult.Failure)?.error
            // 이미 삭제된 스토리(404)는 목록에서 사라지는 목표가 달성된 상태이므로 성공으로 본다.
            return if (error is DomainError.Server && error.status == HTTP_NOT_FOUND) {
                DomainResult.Success(Unit)
            } else {
                result
            }
        }
    }

private const val HTTP_NOT_FOUND = 404
