package app.manyak.create.data.api.dto

import app.manyak.create.entity.ArtStyle
import kotlinx.serialization.Serializable

/** 응답의 `description` 은 화면에 쓰지 않아 역직렬화하지 않는다. */
@Serializable
data class ArtStyleDto(
    val code: String,
    val name: String,
    val previewImageUrl: String? = null,
)

fun ArtStyleDto.toDomain(): ArtStyle = ArtStyle(code = code, name = name, previewImageUrl = previewImageUrl)
