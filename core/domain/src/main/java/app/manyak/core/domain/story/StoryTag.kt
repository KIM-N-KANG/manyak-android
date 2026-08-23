package app.manyak.core.domain.story

/**
 * 간편 제작 키워드 선택의 카테고리. 서버 태그 계약과 같은 값이며, 순서가 곧 키워드 단계의 탭 순서다.
 */
enum class StoryTagCategory {
    GENRE,
    PROTAGONIST,
    SUPPORTING_CHARACTER,
}

/** 서버가 제공하는 사전 정의 태그. */
data class StoryTag(
    val id: Long,
    val name: String,
    val category: StoryTagCategory,
)

/** 인물 성별. 고르지 않으면(null) AI 가 정한다. */
enum class CharacterGender {
    MALE,
    FEMALE,
}
