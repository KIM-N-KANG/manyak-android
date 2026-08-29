package app.manyak.core.domain.chat

import app.manyak.core.domain.error.DomainResult
import kotlinx.coroutines.flow.Flow

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

    /**
     * 사용자 입력으로 다음 턴을 진행한다.
     *
     * **cold Flow 다** — 구독이 곧 요청이라 공유하지 않는다. 두 번 구독하면 턴이 두 번 생긴다.
     * 수집을 취소하면 스트림이 끊기고 아무 사건도 더 오지 않는다.
     *
     * @param sourceTurnId 고른 선택지가 달린 턴. 시작 추천처럼 원본 턴이 없으면 `null`
     * @param choiceOrder 고른 선택지의 순번(1부터). [sourceTurnId] 와 짝이다
     */
    fun streamTurn(
        chatId: String,
        userInput: String,
        userSource: UserSource,
        sourceTurnId: Long? = null,
        choiceOrder: Int? = null,
    ): Flow<ChatStreamEvent>

    /**
     * 마지막 턴의 AI 출력을 같은 사용자 입력으로 다시 생성해 교체한다. 사건 계약은 [streamTurn] 과 같다.
     *
     * 서버가 보는 마지막 턴과 [turnId] 가 다르면 스트림이 열리지 않고 409 가 온다.
     */
    fun regenerateTurn(
        chatId: String,
        turnId: Long,
    ): Flow<ChatStreamEvent>

    /**
     * 마지막 턴의 다음 행동 선택지를 생성해 저장한다.
     *
     * 응답 본문을 돌려주지 않는 이유는 **렌더 소스가 아니기 때문**이다 — 성공은 저장이 끝났다는
     * 신호이고 화면은 상세를 다시 읽어 `turns[].choices` 로 그린다.
     */
    suspend fun generateChoices(
        chatId: String,
        turnId: Long,
    ): DomainResult<Unit>

    /**
     * 채팅을 삭제한다.
     *
     * **없는 채팅(404)은 성공으로 돌려준다** — 이미 지워진 것을 지우려 한 것이라 사용자가 할 일이 없다.
     */
    suspend fun deleteChat(chatId: String): DomainResult<Unit>
}
