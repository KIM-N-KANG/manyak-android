package app.manyak.core.data.repository

import app.manyak.core.data.api.SimpleStoryApi
import app.manyak.core.data.api.apiCall
import app.manyak.core.data.api.dto.toDomainOrNull
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.error.map
import app.manyak.core.domain.story.StoryCreationRepository
import app.manyak.core.domain.story.StoryTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoryCreationRepositoryImpl
    @Inject
    constructor(
        private val simpleStoryApi: SimpleStoryApi,
    ) : StoryCreationRepository {
        override suspend fun tags(): DomainResult<List<StoryTag>> =
            apiCall { simpleStoryApi.tags() }.map { tags -> tags.mapNotNull { it.toDomainOrNull() } }
    }
