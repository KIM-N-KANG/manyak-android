package app.manyak.home.data.dto

import kotlinx.serialization.Serializable

/** 제공 장르 목록 응답. 검색 화면은 대표 장르의 이름만 쓴다. */
@Serializable
data class GenreCatalogDto(
    val featuredGenres: List<GenreDto> = emptyList(),
)

@Serializable
data class GenreDto(
    val name: String,
)
