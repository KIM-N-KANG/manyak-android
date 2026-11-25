package app.manyak.create.domain

import app.manyak.common.entity.story.CreationResumePoint
import app.manyak.create.domain.resumePoint
import app.manyak.create.entity.CharacterGender
import app.manyak.create.entity.CreationProgress
import app.manyak.create.entity.KeywordCharacterSnapshot
import app.manyak.create.entity.KeywordCustomTagSnapshot
import app.manyak.create.entity.KeywordDraftSnapshot
import app.manyak.create.entity.PendingStoryCreation
import app.manyak.create.entity.StorylineGeneration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreationResumePointTest {
    @Test
    fun `키워드 임시 저장본은 키워드 단계로 재개한다`() {
        val record = PendingStoryCreation.KeywordDraft(snapshot = snapshot(genreTagIds = listOf(1L)))

        assertEquals(CreationResumePoint.KeywordStep, record.resumePoint())
    }

    @Test
    fun `선택 순번이 없는 임시 저장본은 스토리라인 단계로 재개한다`() {
        val record = draft(CreationProgress(selectedStorylineIndex = null))

        assertEquals(CreationResumePoint.StorylineStep, record.resumePoint())
    }

    @Test
    fun `선택 순번이 있는 임시 저장본은 추가 정보 단계로 재개한다`() {
        val record = draft(CreationProgress(selectedStorylineIndex = 2))

        assertEquals(CreationResumePoint.AdditionalInfoStep(storylineIndex = 2), record.resumePoint())
    }

    @Test
    fun `아무것도 고르지 않은 키워드 스냅숏은 저장 대상이 아니다`() {
        assertFalse(snapshot().hasInput)
    }

    @Test
    fun `진입 시 놓인 빈 주변 인물 섹션만 있으면 저장 대상이 아니다`() {
        assertFalse(snapshot(supporting = listOf(character())).hasInput)
    }

    @Test
    fun `주인공 이름만 입력해도 저장 대상이다`() {
        assertTrue(snapshot(protagonist = character(name = "홍길동")).hasInput)
    }

    @Test
    fun `선택 해제된 커스텀 키워드도 저장 대상이다`() {
        assertTrue(
            snapshot(customGenre = listOf(KeywordCustomTagSnapshot(name = "느와르", selected = false))).hasInput,
        )
    }

    private fun character(
        name: String = "",
        gender: CharacterGender? = null,
        tagIds: List<Long> = emptyList(),
        customTags: List<KeywordCustomTagSnapshot> = emptyList(),
    ) = KeywordCharacterSnapshot(
        name = name,
        gender = gender,
        selectedTagIds = tagIds,
        customTags = customTags,
    )

    private fun snapshot(
        genreTagIds: List<Long> = emptyList(),
        customGenre: List<KeywordCustomTagSnapshot> = emptyList(),
        protagonist: KeywordCharacterSnapshot = character(),
        supporting: List<KeywordCharacterSnapshot> = emptyList(),
    ) = KeywordDraftSnapshot(
        selectedGenreTagIds = genreTagIds,
        customGenreTags = customGenre,
        protagonist = protagonist,
        supportingCharacters = supporting,
    )

    private fun draft(progress: CreationProgress) =
        PendingStoryCreation.Draft(
            generationCommand = null,
            generation = StorylineGeneration(simpleCreationId = 1L, storylines = emptyList()),
            progress = progress,
        )
}
