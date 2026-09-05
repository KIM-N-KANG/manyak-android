package app.manyak.chat.entity

/**
 * 채팅 목록 카드 한 건. 카드가 그리지 않는 도달 엔딩은 담지 않는다. 참조 스토리 ID 는 카드가
 * 그리지는 않지만 카드에서 여는 신고의 대상이라 담는다.
 *
 * [updatedAtEpochMillis] 가 문자열이 아니라 시각인 이유는 카드가 상대 시간("3일 전")으로 그리기
 * 때문이다. 와이어 형식을 아는 곳은 데이터 계층이고, 형식이 예상과 다르면 `null` 로 와서 카드가
 * 시각 자리를 그리지 않는다.
 */
data class ChatSummary(
    val id: String,
    /** 참조 스토리. 신고는 스토리 단위라 카드에서 신고를 열 때 이 값을 쓴다. */
    val storyId: String,
    val storyTitle: String,
    val thumbnailUrl: String?,
    /** 마지막 AI 출력 전문. 완료 턴이 없는 채팅은 빈 문자열이며 카드가 안내 문구로 대신한다. */
    val lastStoryPreview: String,
    val turnCount: Long,
    val updatedAtEpochMillis: Long?,
)

/** 채팅 상세. 렌더 순서는 프롤로그 → 각 턴(사용자 입력 → AI 출력)이다. */
data class ChatDetail(
    val id: String,
    val storyId: String,
    val storyTitle: String,
    val prologue: String,
    val turns: List<ChatTurn>,
    /** 첫 입력 후보. 턴이 0개일 때만 채워져 온다. */
    val suggestedInputs: List<String>,
)

/** 턴 하나 — 사용자 입력과 그에 대한 AI 출력의 짝. */
data class ChatTurn(
    val id: Long,
    val userInput: String,
    val aiOutput: String,
    /**
     * 다음 행동 선택지. 별도 요청으로 생성해 저장하는 값이라 생성 전에는 비어 있고, 화면은
     * 마지막 턴의 것만 그린다.
     */
    val choices: List<String> = emptyList(),
    /** 이 턴에서 도달한 엔딩의 이름. 도달하지 않았으면 `null` 이다. */
    val reachedEnding: String? = null,
)

/**
 * 전송한 문장의 출처. 서버는 문자열만으로 "추천과 같은 문장을 사용자가 직접 썼다"를 가릴 수 없어
 * 입력 방식을 아는 클라이언트가 정한다. [wireValue] 는 서버 계약의 표기다.
 */
enum class UserSource(
    val wireValue: String,
) {
    /** 직접 입력했다. */
    TYPED("typed"),

    /** 추천·선택지를 그대로 보냈다. */
    CHOICE("choice"),

    /** 추천·선택지를 채운 뒤 고쳐서 보냈다. */
    EDITED_CHOICE("edited_choice"),
}
