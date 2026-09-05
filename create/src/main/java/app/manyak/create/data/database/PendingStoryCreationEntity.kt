package app.manyak.create.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.manyak.create.entity.CharacterGender
import app.manyak.create.entity.KeywordCharacterSnapshot
import app.manyak.create.entity.KeywordCustomTagSnapshot
import app.manyak.create.entity.KeywordDraftSnapshot
import app.manyak.create.entity.PendingStoryCreation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 간편 제작 진행 레코드의 단일 행.
 *
 * 슬롯이 하나라 [id] 는 항상 [SINGLE_ROW_ID] 이고, 새 레코드는 같은 행을 덮어쓴다. 중첩 구조는
 * 조인할 대상이 없어 필드별 JSON 문자열로 담는다. 스테이지에 맞는 페이로드가 없거나 JSON 을
 * 해석할 수 없으면 없는 것으로 취급한다 — 재생성 가능한 스냅숏이라 복구보다 폐기가 안전하다.
 */
@Entity(tableName = "pending_story_creation")
data class PendingStoryCreationEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val stage: String,
    val generationCommand: String? = null,
    val completionCommand: String? = null,
    val generation: String? = null,
    val progress: String? = null,
    val keywordSnapshot: String? = null,
) {
    companion object {
        const val SINGLE_ROW_ID: Int = 0
    }
}

private val json = Json { ignoreUnknownKeys = true }

private inline fun <reified T> encode(value: T): String = json.encodeToString(value)

private inline fun <reified T> decodeOrNull(raw: String?): T? =
    raw?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

internal fun PendingStoryCreation.toEntity(): PendingStoryCreationEntity =
    when (this) {
        is PendingStoryCreation.GeneratingStorylines ->
            PendingStoryCreationEntity(
                stage = STAGE_STORYLINE_GENERATION,
                generationCommand = encode(command.toDto()),
            )

        is PendingStoryCreation.CompletingStory ->
            PendingStoryCreationEntity(
                stage = STAGE_STORY_COMPLETION,
                generationCommand = generationCommand?.let { encode(it.toDto()) },
                completionCommand = encode(command.toDto()),
                generation = encode(generation.toDto()),
                progress = encode(progress.toDto()),
            )

        is PendingStoryCreation.Draft ->
            PendingStoryCreationEntity(
                stage = STAGE_STORY_DRAFT,
                generationCommand = generationCommand?.let { encode(it.toDto()) },
                completionCommand = lastCompletionCommand?.let { encode(it.toDto()) },
                generation = encode(generation.toDto()),
                progress = encode(progress.toDto()),
            )

        is PendingStoryCreation.KeywordDraft ->
            PendingStoryCreationEntity(
                stage = STAGE_KEYWORD_DRAFT,
                keywordSnapshot = encode(snapshot.toDto()),
            )
    }

internal fun PendingStoryCreationEntity.toDomainOrNull(): PendingStoryCreation? =
    when (stage) {
        STAGE_STORYLINE_GENERATION ->
            decodeOrNull<GenerationCommandDto>(generationCommand)
                ?.let { PendingStoryCreation.GeneratingStorylines(it.toDomain()) }

        STAGE_STORY_COMPLETION -> {
            val command = decodeOrNull<CompletionCommandDto>(completionCommand)
            val snapshot = decodeOrNull<GenerationSnapshotDto>(generation)
            if (command == null || snapshot == null) {
                null
            } else {
                PendingStoryCreation.CompletingStory(
                    generationCommand = decodeOrNull<GenerationCommandDto>(generationCommand)?.toDomain(),
                    generation = snapshot.toDomain(),
                    command = command.toDomain(),
                    progress = (decodeOrNull<ProgressDto>(progress) ?: ProgressDto()).toDomain(),
                )
            }
        }

        STAGE_STORY_DRAFT ->
            decodeOrNull<GenerationSnapshotDto>(generation)?.let { snapshot ->
                PendingStoryCreation.Draft(
                    generationCommand = decodeOrNull<GenerationCommandDto>(generationCommand)?.toDomain(),
                    generation = snapshot.toDomain(),
                    progress = (decodeOrNull<ProgressDto>(progress) ?: ProgressDto()).toDomain(),
                    lastCompletionCommand = decodeOrNull<CompletionCommandDto>(completionCommand)?.toDomain(),
                )
            }

        STAGE_KEYWORD_DRAFT ->
            decodeOrNull<KeywordSnapshotDto>(keywordSnapshot)
                ?.let { PendingStoryCreation.KeywordDraft(it.toDomain()) }

        else -> null
    }

@Serializable
private data class KeywordSnapshotDto(
    val selectedGenreTagIds: List<Long> = emptyList(),
    val customGenreTags: List<CustomTagDto> = emptyList(),
    val protagonist: KeywordCharacterDto = KeywordCharacterDto(),
    val supportingCharacters: List<KeywordCharacterDto> = emptyList(),
)

@Serializable
private data class KeywordCharacterDto(
    val name: String = "",
    val gender: String? = null,
    val selectedTagIds: List<Long> = emptyList(),
    val customTags: List<CustomTagDto> = emptyList(),
)

@Serializable
private data class CustomTagDto(
    val name: String,
    val selected: Boolean,
)

private fun KeywordDraftSnapshot.toDto(): KeywordSnapshotDto =
    KeywordSnapshotDto(
        selectedGenreTagIds = selectedGenreTagIds,
        customGenreTags = customGenreTags.map { CustomTagDto(it.name, it.selected) },
        protagonist = protagonist.toDto(),
        supportingCharacters = supportingCharacters.map { it.toDto() },
    )

private fun KeywordCharacterSnapshot.toDto(): KeywordCharacterDto =
    KeywordCharacterDto(
        name = name,
        gender = gender?.name,
        selectedTagIds = selectedTagIds,
        customTags = customTags.map { CustomTagDto(it.name, it.selected) },
    )

private fun KeywordSnapshotDto.toDomain(): KeywordDraftSnapshot =
    KeywordDraftSnapshot(
        selectedGenreTagIds = selectedGenreTagIds,
        customGenreTags = customGenreTags.map { KeywordCustomTagSnapshot(it.name, it.selected) },
        protagonist = protagonist.toDomain(),
        supportingCharacters = supportingCharacters.map { it.toDomain() },
    )

private fun KeywordCharacterDto.toDomain(): KeywordCharacterSnapshot =
    KeywordCharacterSnapshot(
        name = name,
        gender = gender?.let { value -> CharacterGender.entries.firstOrNull { it.name == value } },
        selectedTagIds = selectedTagIds,
        customTags = customTags.map { KeywordCustomTagSnapshot(it.name, it.selected) },
    )

private const val STAGE_STORYLINE_GENERATION = "STORYLINE_GENERATION"
private const val STAGE_STORY_COMPLETION = "STORY_COMPLETION"
private const val STAGE_STORY_DRAFT = "STORY_DRAFT"
private const val STAGE_KEYWORD_DRAFT = "KEYWORD_DRAFT"
