package app.manyak.story.data.repository

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.network.data.api.apiCall
import app.manyak.story.data.api.StoryDetailApi
import app.manyak.story.data.dto.toDomain
import app.manyak.story.domain.StoryRepository
import app.manyak.story.entity.StoryDetail
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoryRepositoryImpl
    @Inject
    constructor(
        private val storyDetailApi: StoryDetailApi,
    ) : StoryRepository {
        // 상세는 회원 세션이 필요해 오리지널 목록과 다른 클라이언트를 쓴다(StoryDetailApi).
        override suspend fun storyDetail(storyId: String): DomainResult<StoryDetail> =
            apiCall { storyDetailApi.storyDetail(storyId) }.map { story -> story.toDomain() }
    }
