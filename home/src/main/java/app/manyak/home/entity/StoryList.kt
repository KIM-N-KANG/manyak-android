package app.manyak.home.entity

import app.manyak.common.entity.story.StorySummary

/** 공개 목록의 필터. 홈과 탐색은 쓰지 않고 첫 가입 안내의 오리지널 목록만 쓴다. */
enum class StoryListFilter {
    ALL,

    /** 마냑 공식 계정이 소유한 스토리만. */
    ORIGINAL,
}

/** 공개 목록의 정렬. */
enum class StoryListSort {
    POPULAR,
    LATEST,
    LIKES,
    CHATS,
}

/**
 * 공개 목록 조회 조건. 다음 페이지도 첫 페이지와 같은 조건으로 읽어야 서버가 커서를 받아 준다.
 * 정렬은 생략하지 않고 항상 보내 서버 기본값이 바뀌어도 받은 목록이 어긋나지 않는다.
 */
data class StoryListQuery(
    val filter: StoryListFilter = StoryListFilter.ALL,
    val sort: StoryListSort = StoryListSort.POPULAR,
)

/**
 * 홈에 세로로 쌓는 가로 목록 하나. 이름, 순서와 담을 스토리는 관리자가 정하고 서버 순서를 그대로 쓴다.
 * 관리자가 켠 섹션이 없을 때 서버가 주는 기본 섹션은 [id] 가 없다.
 */
data class HomeSection(
    val id: String?,
    val name: String,
    val stories: List<StorySummary>,
)

/** 목록 한 페이지. [nextCursor] 가 없으면 마지막 페이지다. */
data class StoryPage(
    val items: List<StorySummary>,
    val nextCursor: String?,
)
