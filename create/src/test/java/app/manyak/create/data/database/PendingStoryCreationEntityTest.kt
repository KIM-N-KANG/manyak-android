package app.manyak.create.data.database

import app.manyak.create.entity.CharacterGender
import app.manyak.create.entity.CreationProgress
import app.manyak.create.entity.KeywordCharacterSnapshot
import app.manyak.create.entity.KeywordCustomTagSnapshot
import app.manyak.create.entity.KeywordDraftSnapshot
import app.manyak.create.entity.PendingStoryCreation
import app.manyak.create.entity.StoryCharacterInput
import app.manyak.create.entity.StoryCompletionCommand
import app.manyak.create.entity.Storyline
import app.manyak.create.entity.StorylineGeneration
import app.manyak.create.entity.StorylineGenerationCommand
import app.manyak.create.entity.StorylineRecommendedInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingStoryCreationEntityTest {
    @Test
    fun `생성 진행 레코드는 왕복해도 같다`() {
        val record = PendingStoryCreation.GeneratingStorylines(command = generationCommand())

        assertEquals(record, record.toEntity().toDomainOrNull())
    }

    @Test
    fun `완성 진행 레코드는 왕복해도 같다`() {
        val record =
            PendingStoryCreation.CompletingStory(
                generationCommand = generationCommand(),
                generation = generation(),
                command = completionCommand(),
                progress = progress(),
            )

        assertEquals(record, record.toEntity().toDomainOrNull())
    }

    @Test
    fun `임시 저장본은 왕복해도 같다`() {
        val record =
            PendingStoryCreation.Draft(
                generationCommand = generationCommand(),
                generation = generation(),
                progress = progress(),
                lastCompletionCommand = completionCommand(),
            )

        assertEquals(record, record.toEntity().toDomainOrNull())
    }

    @Test
    fun `키워드 임시 저장본은 선택 해제된 커스텀 키워드까지 왕복한다`() {
        val record = PendingStoryCreation.KeywordDraft(snapshot = keywordSnapshot())

        assertEquals(record, record.toEntity().toDomainOrNull())
    }

    @Test
    fun `모든 레코드는 단일 행 식별자를 쓴다`() {
        assertEquals(
            PendingStoryCreationEntity.SINGLE_ROW_ID,
            PendingStoryCreation.KeywordDraft(keywordSnapshot()).toEntity().id,
        )
    }

    @Test
    fun `모르는 스테이지는 없는 것으로 취급한다`() {
        val entity = PendingStoryCreationEntity(stage = "SOMETHING_ELSE")

        assertNull(entity.toDomainOrNull())
    }

    @Test
    fun `필수 페이로드가 빠진 행은 없는 것으로 취급한다`() {
        val entity = PendingStoryCreationEntity(stage = "STORY_DRAFT", generation = null)

        assertNull(entity.toDomainOrNull())
    }

    @Test
    fun `깨진 JSON 은 없는 것으로 취급한다`() {
        val entity = PendingStoryCreationEntity(stage = "KEYWORD_DRAFT", keywordSnapshot = "{ not json")

        assertNull(entity.toDomainOrNull())
    }

    private fun generationCommand() =
        StorylineGenerationCommand(
            requestId = "req-1",
            genreTagIds = listOf(1L, 2L),
            customGenreTags = listOf("느와르"),
            protagonist =
                StoryCharacterInput(
                    name = "홍길동",
                    gender = CharacterGender.MALE,
                    featureTagIds = listOf(10L),
                    customTags = listOf("과묵함"),
                ),
            supportingCharacters = emptyList(),
            parentCreationId = null,
            isRegenerated = false,
        )

    private fun completionCommand() =
        StoryCompletionCommand(
            requestId = "req-2",
            simpleCreationId = 7L,
            storylineId = 21L,
            additionalInfos = listOf("배경은 현대의 서울"),
        )

    private fun generation() =
        StorylineGeneration(
            simpleCreationId = 7L,
            storylines =
                listOf(
                    Storyline(
                        id = 21L,
                        storyline = "첫 번째 스토리라인",
                        recommendedInfos = listOf(StorylineRecommendedInfo(id = 31L, text = "추천 정보")),
                    ),
                ),
        )

    private fun progress() =
        CreationProgress(
            selectedStorylineIndex = 1,
            activeStorylineIndex = 1,
            additionalInfoInputs = listOf("입력", ""),
            selectedRecommendations = listOf("추천 정보"),
        )

    private fun keywordSnapshot() =
        KeywordDraftSnapshot(
            selectedGenreTagIds = listOf(1L),
            customGenreTags = listOf(KeywordCustomTagSnapshot(name = "느와르", selected = false)),
            protagonist =
                KeywordCharacterSnapshot(
                    name = "홍길동",
                    gender = CharacterGender.MALE,
                    selectedTagIds = listOf(10L),
                    customTags = listOf(KeywordCustomTagSnapshot(name = "과묵함", selected = true)),
                ),
            supportingCharacters =
                listOf(
                    KeywordCharacterSnapshot(
                        name = "",
                        gender = null,
                        selectedTagIds = emptyList(),
                        customTags = emptyList(),
                    ),
                ),
        )
}
