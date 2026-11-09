package app.manyak.home.data.repository

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.common.domain.story.PopularOriginalStories
import app.manyak.common.entity.story.StorySummary
import app.manyak.home.domain.HomeRepository
import app.manyak.home.entity.StoryListFilter
import app.manyak.home.entity.StoryListQuery
import app.manyak.home.entity.StoryListSort
import javax.inject.Inject

class PopularOriginalStoriesImpl
    @Inject
    constructor(
        private val repository: HomeRepository,
    ) : PopularOriginalStories {
        // ponytail: 첫 페이지(20편)만 읽는다. 오리지널이 그보다 많아지면 다음 커서까지 이어 읽는다.
        override suspend fun firstPage(): DomainResult<List<StorySummary>> =
            repository.publicStories(StoryListQuery(StoryListFilter.ORIGINAL, StoryListSort.CHATS)).map { it.items }
    }
