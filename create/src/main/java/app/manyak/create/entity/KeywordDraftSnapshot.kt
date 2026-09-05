package app.manyak.create.entity

/**
 * 키워드 단계 입력 스냅숏.
 *
 * 요청 페이로드인 [StoryCharacterInput] 을 재사용하지 않는다 — 커스텀 키워드는 선택을 해제해도
 * 목록에 남는 편집 상태라, 선택된 것만 담는 요청 타입으로는 화면을 그대로 되살릴 수 없다.
 */
data class KeywordDraftSnapshot(
    val selectedGenreTagIds: List<Long>,
    val customGenreTags: List<KeywordCustomTagSnapshot>,
    val protagonist: KeywordCharacterSnapshot,
    val supportingCharacters: List<KeywordCharacterSnapshot>,
) {
    /**
     * 저장할 만한 입력이 있는지. 빈 화면을 열었다 닫은 것까지 배너로 남기면 배너가 신호를 잃는다.
     * 진입 시 놓여 있는 빈 주변 인물 섹션은 사용자가 넣은 입력이 아니므로 세지 않는다.
     */
    val hasInput: Boolean
        get() =
            selectedGenreTagIds.isNotEmpty() ||
                customGenreTags.isNotEmpty() ||
                protagonist.hasInput ||
                supportingCharacters.any(KeywordCharacterSnapshot::hasInput)
}

data class KeywordCharacterSnapshot(
    val name: String,
    val gender: CharacterGender?,
    val selectedTagIds: List<Long>,
    val customTags: List<KeywordCustomTagSnapshot>,
) {
    val hasInput: Boolean
        get() =
            name.isNotBlank() ||
                gender != null ||
                selectedTagIds.isNotEmpty() ||
                customTags.isNotEmpty()
}

/** 선택 해제된 항목도 목록에 남으므로 이름과 선택 여부를 함께 담는다. */
data class KeywordCustomTagSnapshot(
    val name: String,
    val selected: Boolean,
)
