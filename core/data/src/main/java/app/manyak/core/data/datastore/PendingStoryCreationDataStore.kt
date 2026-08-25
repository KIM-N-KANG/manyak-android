package app.manyak.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.manyak.core.data.di.IoDispatcher
import app.manyak.core.data.di.PendingCreationDataStore
import app.manyak.core.data.session.UserScopedStore
import app.manyak.core.domain.story.CharacterGender
import app.manyak.core.domain.story.CreationProgress
import app.manyak.core.domain.story.PendingStoryCreation
import app.manyak.core.domain.story.PendingStoryCreationStore
import app.manyak.core.domain.story.StoryCharacterInput
import app.manyak.core.domain.story.StoryCompletionCommand
import app.manyak.core.domain.story.Storyline
import app.manyak.core.domain.story.StorylineGeneration
import app.manyak.core.domain.story.StorylineGenerationCommand
import app.manyak.core.domain.story.StorylineRecommendedInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 간편 제작 진행 레코드의 단일 슬롯.
 *
 * 손상됐거나 스키마가 바뀌어 해석할 수 없는 레코드는 없는 것으로 취급한다 — 슬롯은 재생성 가능한
 * 진행 스냅숏이라 마이그레이션보다 폐기가 안전하다. 사용자 귀속 데이터이므로 [UserScopedStore]
 * 정리 계약에 참여한다.
 */
@Singleton
class PendingStoryCreationDataStore
    @Inject
    constructor(
        @param:PendingCreationDataStore private val dataStore: DataStore<Preferences>,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : PendingStoryCreationStore,
        UserScopedStore {
        override val storeName: String = "pending_creation"

        override val record: Flow<PendingStoryCreation?> =
            dataStore.data
                .map { preferences -> preferences[RECORD_KEY]?.let(::decodeOrNull) }
                .flowOn(ioDispatcher)

        override suspend fun read(): PendingStoryCreation? =
            withContext(ioDispatcher) {
                runCatching { dataStore.data.first()[RECORD_KEY] }.getOrNull()?.let(::decodeOrNull)
            }

        override suspend fun write(record: PendingStoryCreation) {
            withContext(ioDispatcher) {
                val encoded = json.encodeToString(record.toDto())
                runCatching { dataStore.edit { it[RECORD_KEY] = encoded } }
            }
        }

        override suspend fun clear() {
            withContext(ioDispatcher) {
                runCatching { dataStore.edit { it.remove(RECORD_KEY) } }
            }
        }

        override suspend fun clearUserData(): Boolean =
            withContext(ioDispatcher) {
                runCatching { dataStore.edit { it.remove(RECORD_KEY) } }.isSuccess
            }

        private fun decodeOrNull(raw: String): PendingStoryCreation? =
            runCatching { json.decodeFromString<PendingRecordDto>(raw).toDomainOrNull() }.getOrNull()

        private companion object {
            val RECORD_KEY = stringPreferencesKey("record")
            val json = Json { ignoreUnknownKeys = true }
        }
    }

/** 저장 스키마. 서버 stage 어휘를 그대로 쓰되 임시 저장본은 3-1 계약의 `STORY_DRAFT` 다. */
@Serializable
private data class PendingRecordDto(
    val stage: String,
    val generationCommand: GenerationCommandDto? = null,
    val completionCommand: CompletionCommandDto? = null,
    val generation: GenerationSnapshotDto? = null,
    val progress: ProgressDto? = null,
)

@Serializable
private data class GenerationCommandDto(
    val requestId: String,
    val genreTagIds: List<Long>,
    val customGenreTags: List<String>,
    val protagonist: CharacterInputSnapshotDto,
    val supportingCharacters: List<CharacterInputSnapshotDto>,
    val parentCreationId: String? = null,
    val isRegenerated: Boolean = false,
)

@Serializable
private data class CharacterInputSnapshotDto(
    val name: String? = null,
    val gender: String? = null,
    val featureTagIds: List<Long> = emptyList(),
    val customTags: List<String> = emptyList(),
)

@Serializable
private data class CompletionCommandDto(
    val requestId: String,
    val simpleCreationId: Long,
    val storylineId: Long,
    val additionalInfos: List<String>,
)

@Serializable
private data class GenerationSnapshotDto(
    val simpleCreationId: Long,
    val storylines: List<StorylineSnapshotDto>,
)

@Serializable
private data class StorylineSnapshotDto(
    val id: Long,
    val storyline: String,
    val recommendedInfos: List<RecommendedInfoSnapshotDto> = emptyList(),
)

@Serializable
private data class RecommendedInfoSnapshotDto(
    val id: Long,
    val text: String,
)

@Serializable
private data class ProgressDto(
    val selectedStorylineIndex: Int? = null,
    val activeStorylineIndex: Int = 0,
    val additionalInfoInputs: List<String> = emptyList(),
    val selectedRecommendations: List<String> = emptyList(),
)

private const val STAGE_STORYLINE_GENERATION = "STORYLINE_GENERATION"
private const val STAGE_STORY_COMPLETION = "STORY_COMPLETION"
private const val STAGE_STORY_DRAFT = "STORY_DRAFT"

private fun PendingStoryCreation.toDto(): PendingRecordDto =
    when (this) {
        is PendingStoryCreation.KeywordDraft ->
            error("Keyword drafts require the Room-backed pending creation store")

        is PendingStoryCreation.GeneratingStorylines ->
            PendingRecordDto(
                stage = STAGE_STORYLINE_GENERATION,
                generationCommand = command.toDto(),
            )

        is PendingStoryCreation.CompletingStory ->
            PendingRecordDto(
                stage = STAGE_STORY_COMPLETION,
                generationCommand = generationCommand?.toDto(),
                completionCommand = command.toDto(),
                generation = generation.toDto(),
                progress = progress.toDto(),
            )

        is PendingStoryCreation.Draft ->
            PendingRecordDto(
                stage = STAGE_STORY_DRAFT,
                generationCommand = generationCommand?.toDto(),
                completionCommand = lastCompletionCommand?.toDto(),
                generation = generation.toDto(),
                progress = progress.toDto(),
            )
    }

private fun PendingRecordDto.toDomainOrNull(): PendingStoryCreation? =
    when (stage) {
        STAGE_STORYLINE_GENERATION ->
            generationCommand?.let { PendingStoryCreation.GeneratingStorylines(it.toDomain()) }

        STAGE_STORY_COMPLETION -> {
            val command = completionCommand ?: return null
            val generation = generation ?: return null
            PendingStoryCreation.CompletingStory(
                generationCommand = generationCommand?.toDomain(),
                generation = generation.toDomain(),
                command = command.toDomain(),
                progress = (progress ?: ProgressDto()).toDomain(),
            )
        }

        STAGE_STORY_DRAFT -> {
            val generation = generation ?: return null
            PendingStoryCreation.Draft(
                generationCommand = generationCommand?.toDomain(),
                generation = generation.toDomain(),
                progress = (progress ?: ProgressDto()).toDomain(),
                lastCompletionCommand = completionCommand?.toDomain(),
            )
        }

        else -> null
    }

private fun StorylineGenerationCommand.toDto(): GenerationCommandDto {
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

private fun GenerationCommandDto.toDomain(): StorylineGenerationCommand {
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

private fun StoryCompletionCommand.toDto(): CompletionCommandDto =
    CompletionCommandDto(
        requestId = requestId,
        simpleCreationId = simpleCreationId,
        storylineId = storylineId,
        additionalInfos = additionalInfos,
    )

private fun CompletionCommandDto.toDomain(): StoryCompletionCommand =
    StoryCompletionCommand(
        requestId = requestId,
        simpleCreationId = simpleCreationId,
        storylineId = storylineId,
        additionalInfos = additionalInfos,
    )

private fun StorylineGeneration.toDto(): GenerationSnapshotDto =
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

private fun GenerationSnapshotDto.toDomain(): StorylineGeneration =
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

private fun CreationProgress.toDto(): ProgressDto =
    ProgressDto(
        selectedStorylineIndex = selectedStorylineIndex,
        activeStorylineIndex = activeStorylineIndex,
        additionalInfoInputs = additionalInfoInputs,
        selectedRecommendations = selectedRecommendations,
    )

private fun ProgressDto.toDomain(): CreationProgress =
    CreationProgress(
        selectedStorylineIndex = selectedStorylineIndex,
        activeStorylineIndex = activeStorylineIndex,
        additionalInfoInputs = additionalInfoInputs,
        selectedRecommendations = selectedRecommendations,
    )
