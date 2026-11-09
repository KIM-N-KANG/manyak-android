package app.manyak.create.data.api.dto

import app.manyak.create.entity.StorylineRating
import kotlinx.serialization.Serializable

@Serializable
data class StorylineRatingRequestDto(
    val rating: String,
)

fun StorylineRating.toRequestDto(): StorylineRatingRequestDto =
    StorylineRatingRequestDto(
        rating =
            when (this) {
                StorylineRating.GOOD -> "GOOD"
                StorylineRating.BAD -> "BAD"
            },
    )

/** 평가 설정 응답. 화면이 쓰지 않으므로 역직렬화만 하고 버린다. */
@Serializable
data class StorylineRatingResponseDto(
    val id: Long,
    val rating: String? = null,
)
