package app.manyak.home.entity

import app.manyak.common.entity.story.StorySummary

/** 홈 목록의 필터. 칩 하나가 값 하나에 대응한다. */
enum class StoryListFilter {
    ALL,

    /** 마냑 공식 계정이 소유한 스토리만. */
    ORIGINAL,
}

/** 홈 목록의 정렬. 선언 순서가 정렬 메뉴의 순서다 — 기본값인 최신순이 맨 위에 온다. */
enum class StoryListSort {
    LATEST,
    LIKES,
    CHATS,
}

/**
 * 목록 조회 조건. 다음 페이지도 첫 페이지와 같은 조건으로 읽어야 서버가 커서를 받아 준다.
 *
 * 기본값은 서버 기본값과 같은 전체·최신순이다. 그래도 정렬은 생략하지 않고 항상 보낸다 — 서버 기본값이
 * 바뀌어도 화면에 보이는 선택과 받은 목록이 어긋나지 않는다.
 */
data class StoryListQuery(
    val filter: StoryListFilter = StoryListFilter.ALL,
    val sort: StoryListSort = StoryListSort.LATEST,
)

/** 목록 한 페이지. [nextCursor] 가 없으면 마지막 페이지다. */
data class StoryPage(
    val items: List<StorySummary>,
    val nextCursor: String?,
)
