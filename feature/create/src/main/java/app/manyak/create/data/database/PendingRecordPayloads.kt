package app.manyak.create.data.database

import app.manyak.common.entity.story.StoryVisibility
import app.manyak.create.entity.CharacterGender
import app.manyak.create.entity.CreationProgress
import app.manyak.create.entity.StoryCharacterInput
import app.manyak.create.entity.StoryCompletionCommand
import app.manyak.create.entity.Storyline
import app.manyak.create.entity.StorylineGeneration
import app.manyak.create.entity.StorylineGenerationCommand
import app.manyak.create.entity.StorylineRecommendedInfo
import kotlinx.serialization.Serializable

// 컬럼 하나에 담기는 JSON 페이로드. 행마다 독립된 스냅숏이라 조인할 대상이 없어 중첩 구조는 여기 남는다.
@Serializable
internal data class GenerationCommandDto(
    val requestId: String,
    val genreTagIds: List<Long>,
    val customGenreTags: List<String>,
    val protagonist: CharacterInputSnapshotDto,
    val supportingCharacters: List<CharacterInputSnapshotDto>,
    val parentCreationId: String? = null,
    val isRegenerated: Boolean = false,
)

@Serializable
internal data class CharacterInputSnapshotDto(
    val name: String? = null,
    val gender: String? = null,
    val featureTagIds: List<Long> = emptyList(),
    val customTags: List<String> = emptyList(),
)

@Serializable
internal data class CompletionCommandDto(
    val requestId: String,
    val simpleCreationId: Long,
    val storylineId: Long,
    val additionalInfos: List<String>,
    val artStyle: String? = null,
    val visibility: String? = null,
    val draftId: String? = null,
)

@Serializable
internal data class GenerationSnapshotDto(
    val simpleCreationId: Long,
    val storylines: List<StorylineSnapshotDto>,
)

@Serializable
internal data class StorylineSnapshotDto(
    val id: Long,
    val storyline: String,
    val recommendedInfos: List<RecommendedInfoSnapshotDto> = emptyList(),
)

@Serializable
internal data class RecommendedInfoSnapshotDto(
    val id: Long,
    val text: String,
)

@Serializable
internal data class ProgressDto(
    val selectedStorylineIndex: Int? = null,
    val activeStorylineIndex: Int = 0,
    val additionalInfoInputs: List<String> = emptyList(),
    val selectedRecommendations: List<String> = emptyList(),
    val artStyle: String? = null,
    val visibility: String? = null,
)

internal fun StorylineGenerationCommand.toDto(): GenerationCommandDto {
    fun StoryCharacterInput.toDto(): CharacterInputSnapshotDto =
        CharacterInputSnapshotDto(
            name = name,
            gender = gender?.name,
            featureTagIds = featureTagIds,
            customTags = customTags,
        )
    return GenerationCommandDto(
        requestId = requestId,
        genreTagIds = genreTagIds,
        customGenreTags = customGenreTags,
        protagonist = protagonist.toDto(),
        supportingCharacters = supportingCharacters.map { it.toDto() },
        parentCreationId = parentCreationId,
        isRegenerated = isRegenerated,
    )
}

internal fun GenerationCommandDto.toDomain(): StorylineGenerationCommand {
    fun CharacterInputSnapshotDto.toDomain(): StoryCharacterInput =
        StoryCharacterInput(
            name = name,
            gender = gender?.let { value -> CharacterGender.entries.firstOrNull { it.name == value } },
            featureTagIds = featureTagIds,
            customTags = customTags,
        )
    return StorylineGenerationCommand(
        requestId = requestId,
        genreTagIds = genreTagIds,
        customGenreTags = customGenreTags,
        protagonist = protagonist.toDomain(),
        supportingCharacters = supportingCharacters.map { it.toDomain() },
        parentCreationId = parentCreationId,
        isRegenerated = isRegenerated,
    )
}

internal fun StoryCompletionCommand.toDto(): CompletionCommandDto =
    CompletionCommandDto(
        requestId = requestId,
        simpleCreationId = simpleCreationId,
        storylineId = storylineId,
        additionalInfos = additionalInfos,
        artStyle = artStyle,
        visibility = visibility.toPayloadValue(default = StoryVisibility.PRIVATE),
        draftId = draftId,
    )

internal fun CompletionCommandDto.toDomain(): StoryCompletionCommand =
    StoryCompletionCommand(
        requestId = requestId,
        simpleCreationId = simpleCreationId,
        storylineId = storylineId,
        additionalInfos = additionalInfos,
        artStyle = artStyle,
        visibility = visibility.toStoryVisibility(default = StoryVisibility.PRIVATE),
        draftId = draftId,
    )

internal fun StorylineGeneration.toDto(): GenerationSnapshotDto =
    GenerationSnapshotDto(
        simpleCreationId = simpleCreationId,
        storylines =
            storylines.map { storyline ->
                StorylineSnapshotDto(
                    id = storyline.id,
                    storyline = storyline.storyline,
                    recommendedInfos =
                        storyline.recommendedInfos.map { RecommendedInfoSnapshotDto(id = it.id, text = it.text) },
                )
            },
    )

internal fun GenerationSnapshotDto.toDomain(): StorylineGeneration =
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

internal fun CreationProgress.toDto(): ProgressDto =
    ProgressDto(
        selectedStorylineIndex = selectedStorylineIndex,
        activeStorylineIndex = activeStorylineIndex,
        additionalInfoInputs = additionalInfoInputs,
        selectedRecommendations = selectedRecommendations,
        artStyle = artStyle,
        visibility = visibility.toPayloadValue(default = StoryVisibility.PUBLIC),
    )

internal fun ProgressDto.toDomain(): CreationProgress =
    CreationProgress(
        selectedStorylineIndex = selectedStorylineIndex,
        activeStorylineIndex = activeStorylineIndex,
        additionalInfoInputs = additionalInfoInputs,
        selectedRecommendations = selectedRecommendations,
        artStyle = artStyle,
        visibility = visibility.toStoryVisibility(default = StoryVisibility.PUBLIC),
    )

/** 기본값은 비워 둬 이 기능 전과 같은 JSON 으로 남긴다. */
private fun StoryVisibility.toPayloadValue(default: StoryVisibility): String? = name.takeIf { this != default }

/**
 * 값이 없거나 모르면 [default] 다. 진행은 스위치 기본값인 공개로 열고, 완성 명령은 이 기능 전 요청이
 * 공개 범위 없이 나가 서버가 비공개로 만들었으므로 비공개로 읽는다.
 */
internal fun String?.toStoryVisibility(default: StoryVisibility): StoryVisibility =
    StoryVisibility.entries.firstOrNull { it.name == this } ?: default
