package app.manyak.feature.create

import app.manyak.core.domain.story.StoryTag
import app.manyak.core.domain.story.StoryTagCategory
import app.manyak.core.domain.story.Storyline
import app.manyak.core.domain.story.StorylineRecommendedInfo

/** 프리뷰 확인용 예시 생성 결과. 스토리라인·추가 정보 화면 프리뷰가 같은 짝을 쓴다. */
internal fun previewStorylines(): List<Storyline> =
    listOf(
        Storyline(
            id = 1,
            storyline =
                """
                첫 번째 게이트가 열린 날, 동우는 주변의 모든 것을 잃었다.

                그 후, 그는 혼자서 살아남는 법을 배웠고, 그 과정에서 천재적인 능력을 각성했다.

                도윤은 폐허에서 다시 만난 후, 냉담한 말투로 동우를 밀어냈지만 끝내 함께하기로 했다.
                """.trimIndent(),
            recommendedInfos =
                listOf(
                    StorylineRecommendedInfo(id = 1, text = "게이트가 열린 도시의 폐허 묘사를 자세히 그려줘"),
                    StorylineRecommendedInfo(id = 2, text = "동우의 각성 장면을 회상으로 보여줘"),
                    StorylineRecommendedInfo(id = 3, text = "도윤과의 재회 장면에 긴장감을 더해줘"),
                ),
        ),
        Storyline(
            id = 2,
            storyline =
                """
                게이트가 열리던 순간, 동우는 아무 힘도 없는 평범한 사람이었다.

                동우는 도윤의 곁에서 천천히 자신의 능력을 깨달아 갔지만, *그 힘이 괴물과 같은 근원에서 나온다는 사실*은 숨겼다.

                조직이 동우를 **제거 대상**으로 분류한 날, 도윤과 서연은 각자의 이유로 그의 앞을 막아섰다.
                """.trimIndent(),
            recommendedInfos =
                listOf(
                    StorylineRecommendedInfo(id = 4, text = "동우가 힘의 근원을 숨기는 이유를 초반에 암시해줘"),
                    StorylineRecommendedInfo(id = 5, text = "서연이 보고서를 덮는 장면을 비중 있게 다뤄줘"),
                    StorylineRecommendedInfo(id = 6, text = "게이트 안의 풍경을 낯설고 아름답게 그려줘"),
                ),
        ),
        Storyline(
            id = 3,
            storyline =
                """
                세 번째 게이트가 닫힌 뒤, 세상은 동우를 **영웅**이라 불렀다.

                *도윤이 사라진 자리에서, 동우는 그의 힘을 물려받았다.*

                서연은 처음으로 동우에게 손을 내밀며 말한다. "같이 가. 진실은 둘이 들어야 덜 무거워."
                """.trimIndent(),
            recommendedInfos =
                listOf(
                    StorylineRecommendedInfo(id = 7, text = "도윤이 사라지던 날의 회상으로 시작해줘"),
                    StorylineRecommendedInfo(id = 8, text = "동우의 죄책감을 독백으로 드러내줘"),
                    StorylineRecommendedInfo(id = 9, text = "서연이 손을 내미는 마지막 장면을 여운 있게 마무리해줘"),
                ),
        ),
    )

/** 프리뷰 확인용 키워드 단계 기본 상태. 태그 조회가 끝나 장르 목록이 놓인 시점이다. */
internal fun previewKeywordState(): CreateKeywordUiState =
    CreateKeywordUiState(
        isRestoring = false,
        providedTags =
            ProvidedTags.Loaded(
                mapOf(
                    StoryTagCategory.GENRE to
                        listOf(
                            StoryTag(id = 1, name = "로맨스", category = StoryTagCategory.GENRE),
                            StoryTag(id = 2, name = "판타지", category = StoryTagCategory.GENRE),
                            StoryTag(id = 3, name = "미스터리", category = StoryTagCategory.GENRE),
                        ),
                ),
            ),
    )
