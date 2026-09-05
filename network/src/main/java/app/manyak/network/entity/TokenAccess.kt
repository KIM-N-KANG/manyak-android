package app.manyak.network.entity

/** 요청에 쓸 access 토큰을 얻으려는 시도의 결과. */
sealed interface TokenAccess {
    data class Available(
        val accessToken: String,
    ) : TokenAccess

    /** 저장된 세션이 없다. 보호 요청을 보내지 않는다. */
    data object NoSession : TokenAccess

    /** 네트워크 때문에 실패했다. **세션은 유지**하고 이 요청만 실패시킨다. */
    data object TemporarilyUnavailable : TokenAccess

    /** 재로그인이 필요하다. 종료 절차가 이미 시작됐다. */
    data object SessionEnded : TokenAccess
}
