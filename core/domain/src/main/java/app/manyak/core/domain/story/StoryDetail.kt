package app.manyak.core.domain.story

/**
 * 스토리 상세 화면이 그리는 스토리 한 건.
 *
 * 서버 상세 응답에는 등록 상태·공개 범위·로어북·주요 사건·해시태그·좋아요 수도 있지만 이 화면이
 * 그리는 것만 담는다 — 쓰지 않는 필드를 도메인에 두면 화면이 무엇에 의존하는지 흐려진다.
 * 다른 화면이 필요로 할 때 그 화면과 함께 넓힌다.
 */
data class StoryDetail(
    val id: String,
    val title: String,
    /** 한 줄 소개. 서버가 없는 값을 빈 문자열로 주므로 비면 줄 자체를 그리지 않는다. */
    val oneLineIntro: String,
    /** 주요 내용. 없으면 `null` 이고 섹션을 그리지 않는다. */
    val description: String?,
    val genres: List<String>,
    /** 히어로용 **원본** 썸네일. 목록 카드가 쓰는 축소본과 다른 URL 이다. 없으면 `null`. */
    val thumbnailUrl: String?,
    /** 누적 사용자 입력 턴 수 — 스토리의 모든 채팅 완료 턴 합. */
    val turnCount: Long,
    /** `YYYY-MM-DD`. 서버 값이 날짜로 읽히지 않으면 `null` 이고 줄 자체를 그리지 않는다. */
    val createdDate: String?,
    /** 등록 순서. 비어 있을 수 있고, 그때는 채팅 시작이 시작 설정 없이 나간다. */
    val startSettings: List<StoryStartSetting>,
    /**
     * 요청자가 이 스토리에서 도달한 엔딩 이름. 앱은 로그인 필수라 항상 서버 집계이며
     * 게스트 로컬 서재 합산 분기는 없다.
     */
    val reachedEndings: List<String>,
)

/** 채팅을 시작할 상황 하나. 프롤로그와 추천 입력은 상세가 그리지 않고 채팅 화면이 노출한다. */
data class StoryStartSetting(
    /** `POST /chats` 의 `startSettingId` 로 그대로 쓰는 공개 식별자. */
    val id: String,
    val name: String,
    val startSituation: String,
    /**
     * 이 갈래에서 닿을 수 있는 엔딩 이름. 엔딩은 스토리가 아니라 시작 설정에 딸리며, 이름의
     * 유니크는 한 시작 설정 안에서만 보장된다. 달성 조건과 에필로그는 미리 읽히면 플레이할 것이
     * 남지 않아 받지 않는다. 비어 있을 수 있다.
     */
    val endings: List<String>,
)
