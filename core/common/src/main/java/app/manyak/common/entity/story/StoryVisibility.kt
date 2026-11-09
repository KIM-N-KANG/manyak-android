package app.manyak.common.entity.story

/** 스토리 공개 범위. 서버가 이 밖의 값을 주면 각 화면이 `null` 로 받아 표시를 생략한다. */
enum class StoryVisibility {
    PUBLIC,
    PRIVATE,
}
