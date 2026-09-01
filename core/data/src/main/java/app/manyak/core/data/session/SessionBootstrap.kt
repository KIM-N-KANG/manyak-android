package app.manyak.core.data.session

/** 앱 시작 시 저장된 세션을 읽어 공개 상태를 처음으로 확정한다. */
interface SessionBootstrap {
    /**
     * 저장된 세션을 판정한다.
     *
     * 정리가 필요하다는 판정만 하고 **직접 시작하지 않는다** — 종료 조정자는 여러 `:core:*` 를
     * 조합해야 해서 `:app` 이 소유하므로, 결과를 올려 그쪽이 절차를 시작하게 한다.
     */
    suspend fun restore(): SessionRestoreResult
}

/** 앱 시작 시 저장된 세션을 읽은 결과. */
enum class SessionRestoreResult {
    /** 쓸 수 있는 토큰이 있다. 회원 상태를 공개했다. */
    MEMBER,

    /** 저장된 세션이 없다. 미로그인 상태를 공개했다. */
    NO_SESSION,

    /**
     * 토큰이 손상됐거나 저장소를 끝내 읽지 못했다.
     *
     * 미로그인 복원이 아니라 **전체 세션 종료**로 넘긴다. 토큰만 지우고 인증 화면을 열면 이전
     * 사용자의 프로필 캐시·제공자 상태·`device_id` 가 그대로 남는다.
     */
    CLEANUP_REQUIRED,
}
