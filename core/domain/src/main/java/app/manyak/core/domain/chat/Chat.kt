package app.manyak.core.domain.chat

/**
 * 채팅 생성(플레이 시작) 결과. 화면이 그릴 프롤로그·추천 입력은 라우트 규칙에 따라
 * 목적지에서 상세 조회로 다시 얻으므로 식별자만 담는다.
 */
data class CreatedChat(
    val id: String,
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
