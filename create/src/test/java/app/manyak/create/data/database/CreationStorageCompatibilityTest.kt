package app.manyak.create.data.database

import app.manyak.common.entity.story.CreationResumePoint
import app.manyak.common.entity.story.CreationStage
import app.manyak.create.domain.toProgressSummary
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreationStorageCompatibilityTest {
    @Test
    fun `기존 편집 스테이지의 JSON을 복원하고 같은 페이로드로 저장한다`() {
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
            )
        val resumePoints =
            listOf(
                CreationResumePoint.KeywordStep,
                CreationResumePoint.StorylineStep,
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
            assertJsonEquals(fixture.generationCommand, saved.generationCommand)
            assertJsonEquals(fixture.completionCommand, saved.completionCommand)
            assertJsonEquals(fixture.generation, saved.generation)
            assertJsonEquals(fixture.progress, saved.progress)
            assertJsonEquals(fixture.keywordSnapshot, saved.keywordSnapshot)
        }
    }

    @Test
    fun `v1 완성 행은 requestId 와 모든 복구 입력을 유지한 채 요청 행이 된다`() {
        val request =
            requireNotNull(
                legacyCompletionToRequest(
                    generationCommand = generationCommand,
                    completionCommand = completionCommand,
                    generation = generation,
                    progress = progress,
                    submittedAt = 42L,
                ),
            )

        assertEquals("complete-legacy", request.requestId)
        assertEquals(StoryCompletionRequestEntity.STATUS_PENDING, request.status)
        assertEquals(42L, request.submittedAt)
        assertJsonEquals(generationCommand, request.generationCommand)
        assertJsonEquals(completionCommand, request.completionCommand)
        assertJsonEquals(generation, request.generation)
        assertJsonEquals(progress, request.progress)
        val domain = requireNotNull(request.toDomainOrNull())
        assertEquals(listOf("memo"), domain.command.additionalInfos)
        assertEquals(listOf("memo", ""), domain.progress.additionalInfoInputs)
    }

    @Test
    fun `v1 완성 행의 진행이 비어 있으면 빈 진행으로 채우고 명령이 깨졌으면 옮기지 않는다`() {
        val withoutProgress =
            requireNotNull(
                legacyCompletionToRequest(
                    generationCommand = null,
                    completionCommand = completionCommand,
                    generation = generation,
                    progress = null,
                    submittedAt = 1L,
                ),
            )
        assertEquals(
            emptyList<String>(),
            requireNotNull(withoutProgress.toDomainOrNull()).progress.additionalInfoInputs,
        )

        assertNull(
            legacyCompletionToRequest(
                generationCommand = null,
                completionCommand = "{ not json",
                generation = generation,
                progress = progress,
                submittedAt = 1L,
            ),
        )
        assertNull(
            legacyCompletionToRequest(
                generationCommand = null,
                completionCommand = completionCommand,
                generation = null,
                progress = progress,
                submittedAt = 1L,
            ),
        )
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
