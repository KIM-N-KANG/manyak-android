package app.manyak.create.domain

import app.manyak.create.entity.CreationProgress
import app.manyak.create.entity.ServerDraftLink
import app.manyak.create.entity.StoryCompletionCommand
import app.manyak.create.entity.StoryCompletionRequest
import app.manyak.create.testing.sampleGenerationCommand
import app.manyak.create.testing.sampleStorylineGeneration
import org.junit.Assert.assertEquals
import org.junit.Test

class ReturnedDraftTest {
    @Test
    fun `완성에 실패한 요청은 제출한 입력 그대로 추가 정보 단계 초안이 된다`() {
        val generation = sampleStorylineGeneration()
        val command =
            StoryCompletionCommand(
                requestId = "c1",
                simpleCreationId = generation.simpleCreationId,
                storylineId = generation.storylines[1].id,
                additionalInfos = listOf("추천", "배경"),
                artStyle = "WATERCOLOR",
                draftId = "draft-a",
            )
        val request =
            StoryCompletionRequest(
                command = command,
                generationCommand = sampleGenerationCommand(),
                generation = generation,
                progress =
                    CreationProgress(
                        additionalInfoInputs = listOf("배경"),
                        selectedRecommendations = listOf("추천"),
                    ),
                submittedAt = 1,
                serverDraft = ServerDraftLink(3),
            )

        val draft = request.toReturnedDraft()

        assertEquals(sampleGenerationCommand(), draft.generationCommand)
        assertEquals(generation, draft.generation)
        // 같은 입력으로 다시 완성하면 같은 requestId 를 쓰도록 제출한 명령과 화풍을 되살린다.
        assertEquals(command, draft.lastCompletionCommand)
        assertEquals(
            CreationProgress(
                selectedStorylineIndex = 1,
                additionalInfoInputs = listOf("배경"),
                selectedRecommendations = listOf("추천"),
                artStyle = "WATERCOLOR",
            ),
            draft.progress,
        )
        assertEquals(ServerDraftLink(3), draft.serverDraft)
    }
}
