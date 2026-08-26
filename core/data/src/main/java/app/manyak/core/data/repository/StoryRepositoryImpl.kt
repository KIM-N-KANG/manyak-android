package app.manyak.core.data.repository

import app.manyak.core.data.api.StoryApi
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
    ) : StoryRepository {
        override suspend fun originalStories(): DomainResult<List<StorySummary>> =
            apiCall { storyApi.originalStories() }.map { stories -> stories.map { story -> story.toDomain() } }
    }
