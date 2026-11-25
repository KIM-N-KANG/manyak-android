package app.manyak.common.entity.chat

/**
 * 채팅 생성(플레이 시작) 결과. 화면이 그릴 프롤로그·추천 입력은 라우트 규칙에 따라
 * 목적지에서 상세 조회로 다시 얻으므로 식별자만 담는다.
 */
data class CreatedChat(
    val id: String,
)
