package app.manyak.core.data.repository

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.common.domain.story.StoryRepository
import app.manyak.common.entity.story.StoryDetail
import app.manyak.core.data.api.StoryDetailApi
import app.manyak.core.data.api.dto.toDomain
import app.manyak.network.data.api.apiCall
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
