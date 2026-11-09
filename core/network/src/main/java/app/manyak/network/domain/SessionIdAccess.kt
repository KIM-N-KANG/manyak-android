package app.manyak.network.domain

/** 분석 SDK가 제공하는 현재 세션 ID. 초기화되지 않았다면 null이다. */
fun interface SessionIdAccess {
    fun currentSessionId(): Long?
}
