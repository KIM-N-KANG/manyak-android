package app.manyak.home.data.repository

import app.manyak.common.data.story.toDomain
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.common.entity.story.StorySummary
import app.manyak.home.data.api.StoryApi
import app.manyak.home.domain.HomeRepository
import app.manyak.network.data.api.apiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepositoryImpl
    @Inject
    constructor(
        private val storyApi: StoryApi,
    ) : HomeRepository {
        override suspend fun originalStories(): DomainResult<List<StorySummary>> =
            apiCall { storyApi.originalStories() }.map { stories -> stories.map { story -> story.toDomain() } }
    }
