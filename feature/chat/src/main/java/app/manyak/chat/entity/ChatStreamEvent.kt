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
    /**
     * 서버가 생성을 시작했다. 다시 붙은 연결도 이 사건부터 처음부터 다시 보낸다.
     *
     * [generationId] 는 끊겼을 때 같은 생성에 다시 붙는 데 쓴다. 서버가 싣지 않았으면 `null` 이다.
     */
    data class Started(
        val generationId: String? = null,
    ) : ChatStreamEvent

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
     * 실패(스트림을 열지 못한 경우)는 그 상태를 실은 오류가 들어온다. 열린 뒤 읽다가 끊긴 것은 실패가
     * 아니라 [Interrupted] 다.
     */
    data class Failed(
        val error: DomainError,
        val message: String? = null,
    ) : ChatStreamEvent

    /**
     * 종단 사건 없이 스트림이 끊겼다.
     *
     * 서버는 연결이 끊겨도 생성을 계속하므로 화면은 같은 생성에 다시 붙는다. 끝내 받지 못하면 저장
     * 여부가 불명이라 임의로 복원하지 않고 상세를 다시 읽어 확정 상태를 표시한다.
     */
    data object Interrupted : ChatStreamEvent
}
