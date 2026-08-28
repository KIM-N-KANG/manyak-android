package app.manyak.core.domain.chat

/**
 * 채팅 생성(플레이 시작) 결과. 화면이 그릴 프롤로그·추천 입력은 라우트 규칙에 따라
 * 목적지에서 상세 조회로 다시 얻으므로 식별자만 담는다.
 */
data class CreatedChat(
    val id: String,
)

/**
 * 채팅 목록 카드 한 건. 카드가 그리지 않는 참조 스토리 ID·도달 엔딩은 담지 않는다.
 *
 * [updatedAtEpochMillis] 가 문자열이 아니라 시각인 이유는 카드가 상대 시간("3일 전")으로 그리기
 * 때문이다. 와이어 형식을 아는 곳은 데이터 계층이고, 형식이 예상과 다르면 `null` 로 와서 카드가
 * 시각 자리를 그리지 않는다.
 */
data class ChatSummary(
    val id: String,
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
)
