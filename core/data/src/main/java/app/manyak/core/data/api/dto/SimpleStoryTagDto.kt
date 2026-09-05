package app.manyak.core.data.api.dto

import app.manyak.common.entity.story.StoryTag
import app.manyak.common.entity.story.StoryTagCategory
import kotlinx.serialization.Serializable

@Serializable
data class SimpleStoryTagDto(
    val id: Long,
    val name: String,
    val category: String? = null,
)

/**
 * 카테고리를 문자열로 받아 아는 값만 남긴다. 서버에 새 카테고리(예: 배경)가 추가되어도
 * 역직렬화가 깨지지 않고, 앱이 그리지 못하는 태그만 조용히 빠진다.
 */
fun SimpleStoryTagDto.toDomainOrNull(): StoryTag? {
    val category = StoryTagCategory.entries.firstOrNull { it.name == category } ?: return null
    return StoryTag(id = id, name = name, category = category)
}
