package app.manyak.chat.entity

import app.manyak.common.domain.error.DomainError

/**
 * 채팅 턴 스트리밍이 화면으로 올리는 사건.
 *
 * 스트림은 취소를 빼면 **종단 사건 하나로 끝난다** — [Completed]·[Failed]·[Interrupted] 셋 중 하나다.
 * 실패를 예외가 아니라 사건으로 흘리는 이유는 `DomainResult` 와 같다: 상위 계층은 예외를 다루지 않는다.
 *
 * 사용자가 방을 떠나 스트림이 취소된 경우에는 **아무 사건도 만들지 않는다.** 취소를 [Failed] 로
 * 흘리면 이탈할 때마다 실패 안내가 뜬다.
 */
sealed interface ChatStreamEvent {
    /** 서버가 생성을 시작했다. 화면이 할 일은 없다. */
    data object Started : ChatStreamEvent

    /** 이어 붙일 본문 조각. */
    data class Token(
        val text: String,
    ) : ChatStreamEvent

    /** 지금 위치에 끼울 인물 이미지. 같은 사건이 다시 오면 이미지도 다시 붙는다. */
    data class CharacterImage(
        val name: String,
        val imageUrl: String,
    ) : ChatStreamEvent

    /**
     * 턴이 저장됐다.
     *
     * 서버가 함께 보내는 확정 본문은 싣지 않는다 — 화면은 상세를 다시 읽어 서버 확정본으로 교체하므로
     * 여기서 받은 값을 쓰지 않는다.
     */
    data object Completed : ChatStreamEvent

    /**
     * 실패로 끝났다. [message] 는 서버가 준 문구이고 없으면 화면이 기본 문구를 쓴다.
     *
     * 서버가 보내는 실패 사건에는 HTTP 상태가 없어 [DomainError.Unknown] 으로 올린다. 상태가 있는
     * 실패(스트림을 열지 못한 경우)는 그 상태를 실은 오류가 들어온다.
     */
    data class Failed(
        val error: DomainError,
        val message: String? = null,
    ) : ChatStreamEvent

    /**
     * 종단 사건 없이 스트림이 끊겼다.
     *
     * 서버 저장 여부가 불명이므로 화면은 임의로 복원하지 않고 상세를 다시 읽어 확정 상태를 표시한다.
     */
    data object Interrupted : ChatStreamEvent
}
