package app.manyak.auth.entity

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
