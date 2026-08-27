package app.manyak.core.domain.story

/**
 * 목록 카드가 그리는 스토리 한 건. 홈(오리지널)과 제작(내가 만든) 카드가 함께 쓴다.
 *
 * 서버의 목록 응답에는 좋아요 수·등록 상태·생성 시각도 있지만, 두 카드가 그리는 것의 합집합만
 * 담는다 — 쓰지 않는 필드를 도메인에 두면 화면이 무엇에 의존하는지 흐려진다. 상세나 다른
 * 목록이 필요로 할 때 그 화면과 함께 넓힌다.
 */
data class StorySummary(
    val id: String,
    val title: String,
    /** 제작자 닉네임. 작성자가 없는 스토리는 `null` 이라 카드에서 줄 자체를 그리지 않는다. */
    val authorNickname: String?,
    /** 목록용 축소 썸네일. 원본이 없으면 `null` 이고 카드가 placeholder 를 그린다. */
    val thumbnailUrl: String?,
    /** 한 줄 소개. 서버가 없는 값을 빈 문자열로 주므로 비면 카드에서 줄 자체를 그리지 않는다. */
    val oneLineIntro: String,
    /** 장르명 목록. 카드에는 폭에 들어가는 만큼만 보이고 나머지는 +N 으로 접힌다. */
    val genres: List<String>,
    /** 누적 사용자 입력 턴 수 — 스토리의 모든 채팅 완료 턴 합. */
    val turnCount: Long,
)
