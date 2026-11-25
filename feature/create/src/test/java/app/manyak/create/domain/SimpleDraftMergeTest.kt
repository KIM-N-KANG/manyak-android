package app.manyak.create.domain

import app.manyak.common.entity.story.CreationResumePoint
import app.manyak.common.entity.story.CreationStage
import app.manyak.create.entity.CreationProgress
import app.manyak.create.entity.PendingStoryCreation
import app.manyak.create.entity.ServerDraftLink
import app.manyak.create.entity.SimpleServerDraft
import app.manyak.create.entity.StoredCreationDraft
import app.manyak.create.entity.StoryCompletionCommand
import app.manyak.create.entity.StoryCompletionRequest
import app.manyak.create.testing.sampleGenerationCommand
import app.manyak.create.testing.sampleStorylineGeneration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleDraftMergeTest {
    @Test
    fun `생성 중 기록은 그 뒤로 저장되지 않은 같은 제작의 서버 키워드 초안을 가려 한 장으로 보인다`() {
        val local = listOf(generating("d1", ServerDraftLink(baseVersion = 2)))

        val merged = mergeSimpleDrafts(local, emptyList(), listOf(server("d1", version = 2)))

        assertEquals(listOf(CreationStage.STORYLINE_GENERATION), merged.map { it.stage })
        assertEquals(server("d1", 2), local.single().hiddenServerDraft(listOf(server("d1", 2))))
    }

    @Test
    fun `기록 뒤에 다른 곳에서 저장한 서버 초안은 따로 보이고 기기 안 카드를 지워도 남긴다`() {
        val local = listOf(generating("d1", ServerDraftLink(baseVersion = 2)))
        val newer = server("d1", version = 3)

        val merged = mergeSimpleDrafts(local, emptyList(), listOf(newer))

        assertEquals(2, merged.size)
        assertNull(local.single().hiddenServerDraft(listOf(newer)))
    }

    @Test
    fun `서버에 저장한 적 없는 세션의 기록과 연결 없는 기록은 서버 카드를 가리지 않는다`() {
        val local =
            listOf(
                generating("d1", ServerDraftLink(baseVersion = null)),
                generating("d2", serverDraft = null),
            )

        val merged = mergeSimpleDrafts(local, emptyList(), listOf(server("d1", 1), server("d2", 1)))

        assertEquals(4, merged.size)
    }

    @Test
    fun `완성 요청이 가리키는 서버 초안은 서버가 지우기 전까지 숨긴다`() {
        val completion =
            StoryCompletionRequest(
                command = StoryCompletionCommand("c1", 7, 101, emptyList(), "ANIME", draftId = "d1"),
                generationCommand = sampleGenerationCommand(),
                generation = sampleStorylineGeneration(),
                progress = CreationProgress(),
                submittedAt = 0,
                serverDraft = ServerDraftLink(baseVersion = 5),
            )

        assertEquals(emptyList<Any>(), mergeSimpleDrafts(emptyList(), listOf(completion), listOf(server("d1", 5))))
    }

    @Test
    fun `서버 카드는 단계에 맞는 재개 지점과 고른 스토리라인 본문을 싣는다`() {
        val merged =
            mergeSimpleDrafts(
                emptyList(),
                emptyList(),
                listOf(
                    server("k", 1, CreationStage.KEYWORD_DRAFT),
                    server("s", 1, CreationStage.STORY_DRAFT, oneLineIntro = "둘째"),
                ),
            )

        assertEquals(CreationResumePoint.KeywordStep, merged[0].resumePoint)
        assertEquals(CreationResumePoint.StorylineStep, merged[1].resumePoint)
        assertEquals("둘째", merged[1].oneLineIntro)
        assertTrue(merged.all { it.onServer })
        assertEquals(10L, merged[1].createdAt)
    }

    private fun generating(
        draftId: String,
        serverDraft: ServerDraftLink?,
    ): StoredCreationDraft =
        StoredCreationDraft(
            draftId = draftId,
            createdAt = 1,
            record = PendingStoryCreation.GeneratingStorylines(sampleGenerationCommand(), serverDraft),
        )

    private fun server(
        draftId: String,
        version: Int,
        stage: CreationStage = CreationStage.KEYWORD_DRAFT,
        oneLineIntro: String? = null,
    ): SimpleServerDraft = SimpleServerDraft(draftId, stage, oneLineIntro, version, createdAt = 10)
}
