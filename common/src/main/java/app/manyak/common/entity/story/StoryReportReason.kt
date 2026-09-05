package app.manyak.common.entity.story

/**
 * 스토리 신고 사유. 이름은 서버 enum 과 1:1 로 맞춘다 — 데이터 계층이 `name` 을 그대로 실어 보낸다.
 */
enum class StoryReportReason {
    /** 도배·홍보. */
    SPAM,

    /** 부적절한 내용. */
    INAPPROPRIATE,

    /** 그 밖의 사유. 상세 서술로 맥락을 받는다. */
    ETC,
}
