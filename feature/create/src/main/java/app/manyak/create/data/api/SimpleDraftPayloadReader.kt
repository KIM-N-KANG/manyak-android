package app.manyak.create.data.api

import app.manyak.common.data.time.toEpochMillisOrNull
import app.manyak.common.entity.story.StoryVisibility
import app.manyak.create.data.api.dto.toDomainOrNull
import app.manyak.create.data.database.toStoryVisibility
import app.manyak.create.entity.CharacterGender
import app.manyak.create.entity.CreationProgress
import app.manyak.create.entity.CreationRequestSnapshot
import app.manyak.create.entity.KeywordCharacterSnapshot
import app.manyak.create.entity.KeywordCustomTagSnapshot
import app.manyak.create.entity.KeywordDraftSnapshot
import app.manyak.create.entity.PendingStoryCreation
import app.manyak.create.entity.SimpleDraftOpen
import app.manyak.create.entity.StoryCharacterInput
import app.manyak.create.entity.StoryCompletionCommand
import app.manyak.create.entity.StorylineGenerationCommand
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * 서버 상세를 퍼널 기록으로 읽는다. 다른 스키마 버전이나 단계, 키워드 입력 형태를 읽을 수 없으면 null 이고, 스토리라인 초안의
 * 생성 결과를 받을 수 없으면 초안이 없는 것과 같이 다룬다. 고른 스토리라인을 결과에서 찾지 못하면 스토리라인 선택 단계로 연다.
 */
internal fun StoryDraftDetailDto.toSimpleDraftOpen(json: Json): SimpleDraftOpen? {
    if (mode != SIMPLE_MODE || schemaVersion != SIMPLE_DRAFT_SCHEMA_VERSION) return null
    return try {
        when (stage) {
            SIMPLE_KEYWORD_STAGE ->
                SimpleDraftOpen.Found(
                    PendingStoryCreation.KeywordDraft(
                        payloadJson.decodeFromJsonElement<KeywordPayload>(payload).toSnapshot(),
                    ),
                    version,
                    createdAt?.toEpochMillisOrNull(),
                )

            SIMPLE_STORY_STAGE -> readStoryDraft(json)

            else -> null
        }
    } catch (_: IllegalArgumentException) {
        // 역직렬화 오류(SerializationException)도 여기로 온다.
        null
    }
}

private fun StoryDraftDetailDto.readStoryDraft(json: Json): SimpleDraftOpen? {
    val story = payloadJson.decodeFromJsonElement<StoryPayload>(payload)
    if (story.step != STEP_STORYLINE && story.step != STEP_ADDITIONAL_INFO) return null
    val result =
        try {
            generation?.toDomainOrNull(json)
        } catch (_: IllegalArgumentException) {
            null
        }
    val generated =
        (result as? CreationRequestSnapshot.StorylinesReady)?.generation ?: return SimpleDraftOpen.NotFound(version)
    val selectedIndex =
        story.selectedStorylineId
            ?.takeIf { story.step == STEP_ADDITIONAL_INFO }
            ?.let { id -> generated.storylines.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
    return SimpleDraftOpen.Found(
        PendingStoryCreation.Draft(
            generationCommand = story.generationRequest.toCommand(),
            generation = generated,
            progress =
                CreationProgress(
                    selectedStorylineIndex = selectedIndex,
                    activeStorylineIndex = story.activeStorylineIndex,
                    additionalInfoInputs = story.additionalInfos,
                    selectedRecommendations = story.selectedRecommendations,
                    artStyle = story.artStyle,
                    visibility = story.visibility.toStoryVisibility(default = StoryVisibility.PUBLIC),
                ),
            lastCompletionCommand = story.completionRequest?.toCommand(),
        ),
        version,
        createdAt?.toEpochMillisOrNull(),
    )
}

private fun KeywordPayload.toSnapshot(): KeywordDraftSnapshot =
    KeywordDraftSnapshot(
        selectedGenreTagIds = selectedGenreTagIds,
        customGenreTags = customGenreTags.map { KeywordCustomTagSnapshot(it.name, it.selected) },
        protagonist = protagonist.toSnapshot(),
        supportingCharacters = supportingCharacters.map { it.toSnapshot() },
        addedGenreTagIds = addedGenreTagIds,
    )

private fun KeywordCharacterPayload.toSnapshot(): KeywordCharacterSnapshot =
    KeywordCharacterSnapshot(
        name = name,
        gender = gender.toGender(),
        selectedTagIds = selectedTagIds,
        customTags = customTags.map { KeywordCustomTagSnapshot(it.name, it.selected) },
    )

private fun GenerationRequestPayload.toCommand(): StorylineGenerationCommand =
    StorylineGenerationCommand(
        requestId = requestId,
        genreTagIds = genreTagIds,
        customGenreTags = customGenreTags,
        protagonist = protagonist.toInput(),
        supportingCharacters = supportingCharacters.map { it.toInput() },
        parentCreationId = parentCreationId,
        isRegenerated = isRegenerated,
    )

private fun CharacterPayload.toInput(): StoryCharacterInput =
    StoryCharacterInput(name, gender.toGender(), featureTagIds, customTags)

private fun CompletionRequestPayload.toCommand(): StoryCompletionCommand =
    StoryCompletionCommand(
        requestId = requestId,
        simpleCreationId = simpleCreationId,
        storylineId = storylineId,
        additionalInfos = additionalInfos,
        artStyle = artStyle,
        visibility = visibility.toStoryVisibility(default = StoryVisibility.PRIVATE),
        draftId = draftId,
    )

private fun String?.toGender(): CharacterGender? = CharacterGender.entries.firstOrNull { it.name == this }
