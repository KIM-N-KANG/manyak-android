package app.manyak.story.detail.presentation.preview

import app.manyak.story.entity.StoryCharacter
import app.manyak.story.entity.StoryDetail
import app.manyak.story.entity.StoryStartSetting

/** 미리보기 전용 표본. 화면 파일이 재료 선언으로 길어지지 않게 여기 모은다. */
internal fun previewStory(
    startSettings: List<StoryStartSetting> = previewStartSettings(),
    reachedEndings: List<String> = listOf("시계탑의 아침"),
): StoryDetail =
    StoryDetail(
        id = "1",
        title = "두 번째 시계공",
        oneLineIntro = "멈춘 시계탑을 고치는 견습공의 하루",
        authorNickname = "마냑",
        description = "도시의 모든 시계가 같은 시각에 멈췄다. 당신은 그 이유를 아는 마지막 사람이다.",
        genres = listOf("판타지", "미스터리", "일상"),
        thumbnailUrl = null,
        turnCount = 1_284,
        createdDate = "2026-08-27",
        startSettings = startSettings,
        reachedEndings = reachedEndings,
        characters = previewCharacters(),
        isOwner = false,
    )

internal fun previewCharacters(): List<StoryCharacter> =
    listOf(
        StoryCharacter(name = "세린", imageUrl = "https://cdn.manyak.app/characters/generated/serin.webp"),
        StoryCharacter(name = "도윤", imageUrl = null),
    )

internal fun previewStartSettings(): List<StoryStartSetting> =
    listOf(
        StoryStartSetting(
            id = "a",
            name = "폐허가 된 시계탑 앞",
            startSituation = "문은 안에서 잠겨 있다.",
            endings = listOf("시계탑의 아침", "멈춘 채로 남은 도시", "두 번째 시계공"),
        ),
        StoryStartSetting(
            id = "b",
            name = "시계공의 작업실",
            startSituation = "책상 위에 낯선 열쇠가 놓여 있다.",
            endings = listOf("열쇠의 주인", "태엽을 되감다"),
        ),
    )
