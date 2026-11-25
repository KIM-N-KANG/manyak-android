package app.manyak.common.entity.story

/** 완성 요청이 확정적으로 실패한 이유. 제작 탭이 안내 문구를 고른다. */
enum class CompletionFailure {
    /** 복구 조회의 실패처럼 이유를 모르는 실패. */
    FAILED,

    /** AI 가 입력 수위를 이유로 거절했다. 같은 입력으로는 다시 완성할 수 없다. */
    CONTENT_REJECTED,
}
