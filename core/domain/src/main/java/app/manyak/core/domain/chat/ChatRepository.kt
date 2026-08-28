package app.manyak.core.domain.chat

import app.manyak.core.domain.error.DomainResult

interface ChatRepository {
    /**
     * 스토리로 채팅을 생성한다(플레이 시작).
     *
     * @param startSettingId 상세에서 고른 시작 설정. `null` 이면 서버가 스토리의 첫 시작 설정으로
     *  폴백한다 — 간편 제작 완성 직후 진입처럼 고를 것이 하나뿐인 경로가 그렇다.
     */
    suspend fun createChat(
        storyId: String,
        startSettingId: String? = null,
    ): DomainResult<CreatedChat>

    /**
     * 내 채팅 목록 — 최근 활동순. 서버가 준 순서를 그대로 돌려주고 여기서 다시 정렬하지 않는다.
     *
     * 앱은 로그인 필수라 서재의 정본이 언제나 서버다. 웹처럼 로컬에 채팅 ID 를 모아 두었다가
     * 배치로 조회하는 경로는 두지 않는다.
     */
    suspend fun myChats(): DomainResult<List<ChatSummary>>

    /** 채팅 상세 — 스토리 제목·프롤로그·턴 이력·추천 입력. */
    suspend fun chatDetail(chatId: String): DomainResult<ChatDetail>
}
