package app.manyak.create.data.api

import app.manyak.create.entity.KeywordCharacterSnapshot
import app.manyak.create.entity.KeywordDraftSnapshot
import app.manyak.create.entity.PendingStoryCreation
import app.manyak.create.entity.StoryCharacterInput
import app.manyak.create.entity.StoryCompletionCommand
import app.manyak.create.entity.StorylineGenerationCommand
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * 서버는 payload를 해석하지 않아 웹과 같은 v1 형식으로 주고받는다. 형식의 정본은 웹의 간편 제작 payload 변환이다.
 * 스토리라인 초안은 생성 결과 원문을 싣지 않고 최상위 `generationRequestId`로 생성 요청에 잇는다.
 */
internal const val SIMPLE_DRAFT_SCHEMA_VERSION = 1
internal const val SIMPLE_MODE = "SIMPLE"
internal const val SIMPLE_KEYWORD_STAGE = "KEYWORD_DRAFT"
internal const val SIMPLE_STORY_STAGE = "STORY_DRAFT"
internal const val STEP_STORYLINE = "storyline-select"
internal const val STEP_ADDITIONAL_INFO = "additional-info"

/** 쓸 때는 웹처럼 모든 필드를 null 까지 싣고, 읽을 때는 빠졌거나 null 인 선택 필드를 기본값으로 채운다. */
internal val payloadJson =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

/** 키워드 초안과 생성 요청을 아는 스토리라인 초안만 서버에 둔다. 진행 중 기록은 기기 안에 남는다. */
internal fun PendingStoryCreation.toSimpleDraftRequest(baseVersion: Int?): PutStoryDraftDto? =
    when (this) {
        is PendingStoryCreation.KeywordDraft ->
            PutStoryDraftDto(
                mode = SIMPLE_MODE,
                stage = SIMPLE_KEYWORD_STAGE,
                schemaVersion = SIMPLE_DRAFT_SCHEMA_VERSION,
                payload = payloadJson.encodeToJsonElement(snapshot.toPayload()).jsonObject,
                baseVersion = baseVersion,
                title = null,
                oneLineIntro = null,
                thumbnailObjectKey = null,
                generationRequestId = null,
            )

        is PendingStoryCreation.Draft -> toStoryDraftRequest(baseVersion)

        is PendingStoryCreation.GeneratingStorylines -> null
    }

private fun PendingStoryCreation.Draft.toStoryDraftRequest(baseVersion: Int?): PutStoryDraftDto? {
    val command = generationCommand ?: return null
    val selected = progress.selectedStorylineIndex?.let(generation.storylines::getOrNull)
    val payload =
        StoryPayload(
            step = if (selected == null) STEP_STORYLINE else STEP_ADDITIONAL_INFO,
            generationRequest = command.toPayload(),
            activeStorylineIndex = progress.activeStorylineIndex,
            selectedStorylineId = selected?.id,
            additionalInfos = progress.additionalInfoInputs,
            selectedRecommendations = progress.selectedRecommendations,
            artStyle = progress.artStyle,
            createdStoryId = null,
            completionRequest = lastCompletionCommand?.toPayload(),
        )
    return PutStoryDraftDto(
        mode = SIMPLE_MODE,
        stage = SIMPLE_STORY_STAGE,
        schemaVersion = SIMPLE_DRAFT_SCHEMA_VERSION,
        payload = payloadJson.encodeToJsonElement(payload).jsonObject,
        baseVersion = baseVersion,
        title = null,
        // 제작 탭 카드 설명이 이 값을 고른 스토리라인 본문으로 보인다.
        oneLineIntro = selected?.storyline?.ifEmpty { null },
        thumbnailObjectKey = null,
        generationRequestId = command.requestId,
    )
}

@Serializable
internal data class KeywordPayload(
    val selectedGenreTagIds: List<Long>,
    val addedGenreTagIds: List<Long> = emptyList(),
    val customGenreTags: List<CustomTagPayload>,
    val protagonist: KeywordCharacterPayload,
    val supportingCharacters: List<KeywordCharacterPayload>,
)

@Serializable
internal data class KeywordCharacterPayload(
    val name: String,
    val gender: String?,
    val selectedTagIds: List<Long>,
    val customTags: List<CustomTagPayload>,
)

@Serializable
internal data class CustomTagPayload(
    val name: String,
    val selected: Boolean,
)

@Serializable
internal data class StoryPayload(
    val step: String,
    val generationRequest: GenerationRequestPayload,
    val activeStorylineIndex: Int = 0,
    val selectedStorylineId: Long? = null,
    val additionalInfos: List<String> = emptyList(),
    val selectedRecommendations: List<String> = emptyList(),
    val artStyle: String? = null,
    val createdStoryId: String? = null,
    val completionRequest: CompletionRequestPayload? = null,
)

/** 생성 요청 본문 그대로다. 웹은 새 요청에 직접 입력 장르와 재생성 플래그를 싣지 않아 기본값을 둔다. */
@Serializable
internal data class GenerationRequestPayload(
    val requestId: String,
    val genreTagIds: List<Long> = emptyList(),
    val customGenreTags: List<String> = emptyList(),
    val protagonist: CharacterPayload = CharacterPayload(),
    val supportingCharacters: List<CharacterPayload> = emptyList(),
    val parentCreationId: String? = null,
    val isRegenerated: Boolean = false,
)

@Serializable
internal data class CharacterPayload(
    val name: String? = null,
    val gender: String? = null,
    val featureTagIds: List<Long> = emptyList(),
    val customTags: List<String> = emptyList(),
)

@Serializable
internal data class CompletionRequestPayload(
    val requestId: String,
    val simpleCreationId: Long,
    val storylineId: Long,
    val additionalInfos: List<String> = emptyList(),
    val artStyle: String? = null,
    val draftId: String? = null,
)

private fun KeywordDraftSnapshot.toPayload(): KeywordPayload =
    KeywordPayload(
        selectedGenreTagIds = selectedGenreTagIds,
        addedGenreTagIds = addedGenreTagIds,
        customGenreTags = customGenreTags.map { CustomTagPayload(it.name, it.selected) },
        protagonist = protagonist.toPayload(),
        supportingCharacters = supportingCharacters.map { it.toPayload() },
    )

private fun KeywordCharacterSnapshot.toPayload(): KeywordCharacterPayload =
    KeywordCharacterPayload(
        name = name,
        gender = gender?.name,
        selectedTagIds = selectedTagIds,
        customTags = customTags.map { CustomTagPayload(it.name, it.selected) },
    )

private fun StorylineGenerationCommand.toPayload(): GenerationRequestPayload =
    GenerationRequestPayload(
        requestId = requestId,
        genreTagIds = genreTagIds,
        customGenreTags = customGenreTags,
        protagonist = protagonist.toPayload(),
        supportingCharacters = supportingCharacters.map { it.toPayload() },
        parentCreationId = parentCreationId,
        isRegenerated = isRegenerated,
    )

private fun StoryCharacterInput.toPayload(): CharacterPayload =
    CharacterPayload(name, gender?.name, featureTagIds, customTags)

private fun StoryCompletionCommand.toPayload(): CompletionRequestPayload =
    CompletionRequestPayload(requestId, simpleCreationId, storylineId, additionalInfos, artStyle, draftId)
