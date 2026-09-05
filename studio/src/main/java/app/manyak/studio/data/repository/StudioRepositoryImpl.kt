package app.manyak.studio.data.repository

import app.manyak.common.data.story.toDomain
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.common.entity.story.StorySummary
import app.manyak.network.data.api.apiCall
import app.manyak.network.data.api.emptyBodyApiCall
import app.manyak.studio.data.api.StudioApi
import app.manyak.studio.domain.StudioRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudioRepositoryImpl
    @Inject
    constructor(
        private val userApi: StudioApi,
    ) : StudioRepository {
        // 내 스토리는 보호 경로라 인증 클라이언트를 쓰는 StudioApi 쪽에 정의되어 있다.
        override suspend fun myStories(): DomainResult<List<StorySummary>> =
            apiCall { userApi.myStories() }.map { stories -> stories.map { story -> story.toDomain() } }

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
