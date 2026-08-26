package app.manyak.core.data.repository

import app.manyak.core.data.api.StoryApi
import app.manyak.core.data.api.UserApi
import app.manyak.core.data.api.apiCall
import app.manyak.core.data.api.dto.toDomain
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.error.map
import app.manyak.core.domain.story.StoryRepository
import app.manyak.core.domain.story.StorySummary
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoryRepositoryImpl
    @Inject
    constructor(
        private val storyApi: StoryApi,
        private val userApi: UserApi,
    ) : StoryRepository {
        override suspend fun originalStories(): DomainResult<List<StorySummary>> =
            apiCall { storyApi.originalStories() }.map { stories -> stories.map { story -> story.toDomain() } }

        // 내 스토리는 보호 경로라 인증 클라이언트를 쓰는 UserApi 쪽에 정의되어 있다.
        override suspend fun myStories(): DomainResult<List<StorySummary>> =
            apiCall { userApi.myStories() }.map { stories -> stories.map { story -> story.toDomain() } }
    }
