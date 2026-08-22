package app.manyak.core.domain.session

/**
 * 화면이 관찰하는 공개 세션 상태. 앱에는 게스트가 없으므로 셋뿐이다.
 *
 * 종료 정리가 진행 중인 동안에도 공개 상태는 [Undetermined]에 머문다 — 정리가 끝나기 전에
 * 인증 그래프를 열면 새 로그인이 이전 사용자의 잔여 상태 위에서 시작될 수 있다.
 */
sealed interface SessionState {
    /** 저장된 토큰을 아직 읽지 못했거나 종료 정리가 끝나지 않았다. 어느 그래프도 그리지 않는다. */
    data object Undetermined : SessionState

    /** 복호화 가능한 토큰 쌍이 없다. 인증 그래프를 띄운다. */
    data class SignedOut(
        /** 직전 세션이 끝난 이유. 사용자가 확인하기 전까지 유지된다. 최초 실행이면 null. */
        val notice: SessionEndNotice?,
    ) : SessionState

    /** 복호화 가능한 토큰 쌍이 있고 종료 중이 아니다. 메인 그래프를 띄운다. */
    data object Member : SessionState
}

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
