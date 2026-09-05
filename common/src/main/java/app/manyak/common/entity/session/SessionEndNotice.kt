package app.manyak.common.entity.session

/**
 * 세션이 끝난 이유. 화면 수명 효과가 아니라 지속 상태다 — 종료 시점에 화면이 없을 수 있으므로
 * 사용자가 명시적으로 확인할 때까지 남는다.
 */
enum class SessionEndNotice {
    /** 사용자가 로그아웃을 눌렀다. 별도 안내가 필요 없다. */
    USER_REQUESTED,

    /** refresh 가 401 로 거절됐거나 토큰을 복호화하지 못했다. 다시 로그인해야 한다. */
    REAUTHENTICATION_REQUIRED,

    /** 정지된 계정이다. 일반 로그아웃과 구분되는 안내를 보여야 하며 사유는 노출하지 않는다. */
    ACCOUNT_SUSPENDED,

    /** 서버 회전은 성공했지만 새 토큰을 저장하지 못했다. 구 토큰은 이미 폐기되어 재로그인이 필요하다. */
    TOKEN_PERSISTENCE_FAILED,
}
