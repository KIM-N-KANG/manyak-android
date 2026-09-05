package app.manyak.core.data.api.dto

import app.manyak.common.entity.story.CharacterGender
import app.manyak.common.entity.story.StoryCharacterInput
import app.manyak.common.entity.story.Storyline
import app.manyak.common.entity.story.StorylineGeneration
import app.manyak.common.entity.story.StorylineGenerationCommand
import app.manyak.common.entity.story.StorylineRecommendedInfo
import kotlinx.serialization.Serializable

@Serializable
data class StorylineGenerationRequestDto(
    val requestId: String,
    val genreTagIds: List<Long>,
    val customGenreTags: List<String>,
    val protagonist: CharacterInputDto,
    val supportingCharacters: List<CharacterInputDto>,
    val parentCreationId: String?,
    val isRegenerated: Boolean,
)

@Serializable
data class CharacterInputDto(
    val name: String?,
    val gender: String?,
    val featureTagIds: List<Long>,
    val customTags: List<String>,
)

fun StorylineGenerationCommand.toRequestDto(): StorylineGenerationRequestDto =
    StorylineGenerationRequestDto(
        requestId = requestId,
        genreTagIds = genreTagIds,
        customGenreTags = customGenreTags,
        protagonist = protagonist.toDto(),
        supportingCharacters = supportingCharacters.map(StoryCharacterInput::toDto),
        parentCreationId = parentCreationId,
        isRegenerated = isRegenerated,
    )

private fun StoryCharacterInput.toDto(): CharacterInputDto =
    CharacterInputDto(
        name = name,
        gender =
            when (gender) {
                CharacterGender.MALE -> "MALE"
                CharacterGender.FEMALE -> "FEMALE"
                null -> null
            },
        featureTagIds = featureTagIds,
        customTags = customTags,
    )

/** 응답의 `selectedTags` 는 화면이 쓰지 않아 역직렬화하지 않는다. */
@Serializable
data class StorylineGenerationResponseDto(
    val simpleCreationId: Long,
    val storylines: List<StorylineDto> = emptyList(),
)

@Serializable
data class StorylineDto(
    val id: Long,
    val storyline: String,
    val recommendedInfos: List<RecommendedInfoDto> = emptyList(),
)

@Serializable
data class RecommendedInfoDto(
    val id: Long,
    val text: String,
)

fun StorylineGenerationResponseDto.toDomain(): StorylineGeneration =
    StorylineGeneration(
        simpleCreationId = simpleCreationId,
        storylines =
            storylines.map { storyline ->
                Storyline(
                    id = storyline.id,
                    storyline = storyline.storyline,
                    recommendedInfos =
                        storyline.recommendedInfos.map { StorylineRecommendedInfo(id = it.id, text = it.text) },
                )
            },
    )
