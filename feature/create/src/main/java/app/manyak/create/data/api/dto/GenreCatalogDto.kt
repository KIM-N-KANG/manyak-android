package app.manyak.create.data.api.dto

import app.manyak.create.entity.GenreCatalog
import app.manyak.create.entity.StoryTag
import app.manyak.create.entity.StoryTagCategory
import kotlinx.serialization.Serializable

@Serializable
data class GenreCatalogDto(
    val genres: List<GenreCatalogItemDto>,
    val featuredGenres: List<GenreCatalogItemDto>,
)

@Serializable
data class GenreCatalogItemDto(
    val id: Long,
    val name: String,
)

internal fun GenreCatalogDto.toDomain(): GenreCatalog =
    GenreCatalog(
        genres = genres.map { StoryTag(it.id, it.name, StoryTagCategory.GENRE) },
        featuredGenres = featuredGenres.map { StoryTag(it.id, it.name, StoryTagCategory.GENRE) },
    )
