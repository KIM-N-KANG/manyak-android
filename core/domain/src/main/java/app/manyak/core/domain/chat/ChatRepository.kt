package app.manyak.core.domain.chat

import app.manyak.core.domain.error.DomainResult

interface ChatRepository {
    /**
     * 스토리로 채팅을 생성한다(플레이 시작). 시작 설정은 지정하지 않으며 서버가 스토리의
     * 첫 시작 설정으로 폴백한다.
     */
    suspend fun createChat(storyId: String): DomainResult<CreatedChat>

    /** 채팅 상세 — 스토리 제목·프롤로그·턴 이력·추천 입력. */
    suspend fun chatDetail(chatId: String): DomainResult<ChatDetail>
}
