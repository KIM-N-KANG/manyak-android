package app.manyak.create.data.database

import app.manyak.common.entity.story.CreationResumePoint
import app.manyak.common.entity.story.CreationStage
import app.manyak.create.domain.toProgressSummary
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CreationStorageCompatibilityTest {
    @Test
    fun `기존 네 스테이지의 JSON을 복원하고 같은 페이로드로 저장한다`() {
        val fixtures =
            listOf(
                PendingStoryCreationEntity(stage = "KEYWORD_DRAFT", keywordSnapshot = keyword),
                PendingStoryCreationEntity(stage = "STORYLINE_GENERATION", generationCommand = generationCommand),
                PendingStoryCreationEntity(
                    stage = "STORY_DRAFT",
                    generationCommand = generationCommand,
                    completionCommand = completionCommand,
                    generation = generation,
                    progress = progress,
                ),
                PendingStoryCreationEntity(
                    stage = "STORY_COMPLETION",
                    generationCommand = generationCommand,
                    completionCommand = completionCommand,
                    generation = generation,
                    progress = progress,
                ),
            )
        val resumePoints =
            listOf(
                CreationResumePoint.KeywordStep,
                CreationResumePoint.StorylineStep,
                CreationResumePoint.AdditionalInfoStep(0),
                CreationResumePoint.AdditionalInfoStep(0),
            )

        for ((index, fixture) in fixtures.withIndex()) {
            val record = requireNotNull(fixture.toDomainOrNull())
            val saved = record.toEntity()
            val summary = record.toProgressSummary()
            assertEquals(fixture.stage, saved.stage)
            assertEquals(0, saved.id)
            assertEquals(CreationStage.valueOf(fixture.stage), summary.stage)
            assertEquals(resumePoints[index], summary.resumePoint)
            assertEquals(fixture.stage == "STORY_COMPLETION", summary.isCompleting)
            assertJsonEquals(fixture.generationCommand, saved.generationCommand)
            assertJsonEquals(fixture.completionCommand, saved.completionCommand)
            assertJsonEquals(fixture.generation, saved.generation)
            assertJsonEquals(fixture.progress, saved.progress)
            assertJsonEquals(fixture.keywordSnapshot, saved.keywordSnapshot)
        }
    }

    private fun assertJsonEquals(
        expected: String?,
        actual: String?,
    ) {
        assertEquals(expected?.let(Json::parseToJsonElement), actual?.let(Json::parseToJsonElement))
    }
}

private val generationCommand =
    """
    {"requestId":"request-legacy","genreTagIds":[1],"customGenreTags":[],
    "protagonist":{},"supportingCharacters":[]}
    """.trimIndent()
private val completionCommand =
    """
    {"requestId":"complete-legacy","simpleCreationId":7,"storylineId":21,"additionalInfos":["memo"]}
    """.trimIndent()
private val generation =
    """
    {"simpleCreationId":7,"storylines":[{"id":21,"storyline":"fixture"}]}
    """.trimIndent()
private val progress =
    """
    {"selectedStorylineIndex":0,"additionalInfoInputs":["memo",""],"selectedRecommendations":["pick"]}
    """.trimIndent()
private val keyword =
    """
    {"selectedGenreTagIds":[1],"customGenreTags":[{"name":"fixture","selected":false}],
    "protagonist":{"name":"fixture","gender":"MALE"}}
    """.trimIndent()
