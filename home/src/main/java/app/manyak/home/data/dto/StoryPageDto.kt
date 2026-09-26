package app.manyak.home.data.dto

import app.manyak.common.data.story.StorySummaryDto
import app.manyak.common.data.story.toDomain
import app.manyak.home.entity.StoryListFilter
import app.manyak.home.entity.StoryListSort
import app.manyak.home.entity.StoryPage
import kotlinx.serialization.Serializable

@Serializable
data class StoryPageDto(
    val items: List<StorySummaryDto> = emptyList(),
    val nextCursor: String? = null,
)

fun StoryPageDto.toDomain(): StoryPage =
    StoryPage(
        items = items.map { story -> story.toDomain() },
        nextCursor = nextCursor?.takeIf { cursor -> cursor.isNotBlank() },
    )

internal val StoryListFilter.queryValue: String
    get() =
        when (this) {
            StoryListFilter.ALL -> "all"
            StoryListFilter.ORIGINAL -> "original"
        }

internal val StoryListSort.queryValue: String
    get() =
        when (this) {
            StoryListSort.LIKES -> "likes"
            StoryListSort.LATEST -> "latest"
            StoryListSort.CHATS -> "chats"
        }
