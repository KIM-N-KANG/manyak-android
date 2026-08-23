package app.manyak.feature.create

import app.manyak.core.domain.story.StoryTagCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateKeywordUiStateTest {
    @Test
    fun `앞선 필수 카테고리가 완료되어야 다음 카테고리가 잠금 해제된다`() {
        val empty = CreateKeywordUiState()
        assertTrue(empty.isUnlocked(StoryTagCategory.GENRE))
        assertFalse(empty.isUnlocked(StoryTagCategory.PROTAGONIST))
        assertFalse(empty.isUnlocked(StoryTagCategory.SUPPORTING_CHARACTER))

        val genreSelected = empty.copy(selectedGenreTagIds = setOf(1L))
        assertTrue(genreSelected.isUnlocked(StoryTagCategory.PROTAGONIST))
        assertFalse(genreSelected.isUnlocked(StoryTagCategory.SUPPORTING_CHARACTER))

        val complete = genreSelected.copy(protagonist = genreSelected.protagonist.copy(selectedTagIds = setOf(10L)))
        assertTrue(complete.isUnlocked(StoryTagCategory.SUPPORTING_CHARACTER))
    }

    @Test
    fun `선택 상한은 제공 태그와 선택된 커스텀 태그를 합산한다`() {
        val state =
            CreateKeywordUiState(
                selectedGenreTagIds = setOf(1L, 2L),
                customGenreTags = listOf(CustomTag(name = "타임루프", selected = true)),
            )
        assertTrue(state.isAtSelectionCap(KeywordTarget.Genre))

        val deselectedCustom = state.copy(customGenreTags = listOf(CustomTag(name = "타임루프", selected = false)))
        assertFalse(deselectedCustom.isAtSelectionCap(KeywordTarget.Genre))
    }

    @Test
    fun `이름 중복은 정규화 키로 판정하고 나중에 쓴 인물만 표시한다`() {
        val state =
            CreateKeywordUiState(
                protagonist = KeywordCharacter(id = 0, name = "서지우"),
                supportingCharacters =
                    listOf(
                        // NFC 정규화 → 공백 제거 → 소문자 기준으로 주인공과 같은 이름이다.
                        KeywordCharacter(id = 1, name = "서 지우"),
                        KeywordCharacter(id = 2, name = "한도윤"),
                    ),
            )
        assertEquals(setOf(1L), state.duplicateNameCharacterIds)
    }

    @Test
    fun `비워 둔 이름은 중복 판정 대상이 아니다`() {
        val state =
            CreateKeywordUiState(
                protagonist = KeywordCharacter(id = 0, name = ""),
                supportingCharacters =
                    listOf(
                        KeywordCharacter(id = 1, name = ""),
                        KeywordCharacter(id = 2, name = " "),
                    ),
            )
        assertTrue(state.duplicateNameCharacterIds.isEmpty())
    }

    @Test
    fun `스토리라인 생성은 장르·주인공 완료와 이름 중복 없음을 모두 요구한다`() {
        val incomplete = CreateKeywordUiState(selectedGenreTagIds = setOf(1L))
        assertFalse(incomplete.canGenerateStorylines)

        val complete =
            incomplete.copy(protagonist = incomplete.protagonist.copy(selectedTagIds = setOf(10L)))
        assertTrue(complete.canGenerateStorylines)

        val duplicated =
            complete.copy(
                protagonist = complete.protagonist.copy(name = "지우"),
                supportingCharacters = listOf(KeywordCharacter(id = 1, name = "지우")),
            )
        assertFalse(duplicated.canGenerateStorylines)
    }
}
