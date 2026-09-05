package app.manyak.common.domain.chat

/** 채팅 입력 모드. 기본은 [BLOCK] 이다. */
enum class ChatInputMode {
    /** 상황과 대사를 나눠 적는 블럭 입력. */
    BLOCK,

    /** 한 입력창에 자유롭게 적는 일반 입력. */
    PLAIN,
}

/**
 * 채팅방의 기기 설정.
 *
 * **계정이 아니라 기기에 남는다** — 로그아웃해도 지우지 않으며 언어·테마와 같은 분류다. 그래서 세션
 * 종료 정리 계약(`UserScopedStore`)에 참여하지 않는다.
 *
 * 모두 한 번 읽는 값이고 관찰 흐름을 노출하지 않는다. 화면은 진입할 때 읽어 상태로 들고, 바꿀 때
 * 상태를 먼저 바꾼 뒤 저장한다 — **저장에 실패해도 지금 세션의 선택은 유지되어야 하기 때문**이다.
 * 저장소를 정본으로 삼아 흐름을 구독하면 저장이 실패한 순간 사용자가 방금 누른 선택이 되돌아간다.
 */
interface ChatPreferencesRepository {
    /** 읽지 못하면 [ChatInputMode.BLOCK]. */
    suspend fun inputMode(): ChatInputMode

    suspend fun setInputMode(mode: ChatInputMode)

    /** 읽지 못하면 켬. */
    suspend fun choicesEnabled(): Boolean

    suspend fun setChoicesEnabled(enabled: Boolean)

    /**
     * 추천 입력 사용법 힌트를 이미 봤는지. 읽지 못하면 보지 않은 것으로 본다.
     *
     * 노출하는 순간 열람으로 기록하므로 화면은 **진입 시점의 값을 붙잡아** 그 방에 머무는 동안
     * 유지한다. 관찰 흐름이었다면 힌트가 뜨자마자 스스로 사라진다.
     */
    suspend fun isChoicesHintSeen(): Boolean

    suspend fun markChoicesHintSeen()
}
