package app.manyak.home.entity

import app.manyak.common.entity.story.StorySummary

/** 홈 목록의 필터. 칩 하나가 값 하나에 대응한다. */
enum class StoryListFilter {
    ALL,

    /** 마냑 공식 계정이 소유한 스토리만. */
    ORIGINAL,
}

/** 홈 목록의 정렬. */
enum class StoryListSort {
    LIKES,
    LATEST,
    CHATS,
}

/**
 * 목록 조회 조건. 다음 페이지도 첫 페이지와 같은 조건으로 읽어야 서버가 커서를 받아 준다.
 *
 * 기본값은 전체·인기순이다. 서버 기본 정렬(최신순)과 다르므로 정렬을 생략하지 않고 항상 보낸다.
 */
data class StoryListQuery(
    val filter: StoryListFilter = StoryListFilter.ALL,
    val sort: StoryListSort = StoryListSort.LIKES,
)

/** 목록 한 페이지. [nextCursor] 가 없으면 마지막 페이지다. */
data class StoryPage(
    val items: List<StorySummary>,
    val nextCursor: String?,
)
