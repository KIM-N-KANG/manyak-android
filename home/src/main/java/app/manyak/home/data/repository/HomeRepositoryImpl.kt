package app.manyak.home.data.repository

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.home.data.api.StoryApi
import app.manyak.home.data.dto.queryValue
import app.manyak.home.data.dto.toDomain
import app.manyak.home.domain.HomeRepository
import app.manyak.home.entity.StoryListQuery
import app.manyak.home.entity.StoryPage
import app.manyak.network.data.api.apiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepositoryImpl
    @Inject
    constructor(
        private val storyApi: StoryApi,
    ) : HomeRepository {
        override suspend fun publicStories(
            query: StoryListQuery,
            cursor: String?,
        ): DomainResult<StoryPage> =
            apiCall {
                storyApi.publicStories(
                    filter = query.filter.queryValue,
                    sort = query.sort.queryValue,
                    cursor = cursor,
                )
            }.map { page -> page.toDomain() }
    }
