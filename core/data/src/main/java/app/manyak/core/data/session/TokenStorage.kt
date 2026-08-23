package app.manyak.core.data.session

import app.manyak.core.data.datastore.StoredSession

/**
 * 토큰 보관의 계약. 구현은 Keystore 로 암호화해 DataStore 에 쓰지만, 재발급 로직은 그 사실을 몰라도 된다.
 *
 * 이 경계 덕분에 회전·저장 실패 경로를 실기기 없이 단위 테스트로 고정할 수 있다.
 */
interface TokenStorage {
    suspend fun read(): TokenReadResult

    /** 원자적으로 한 번에 쓴다. 실패하면 false 이며 호출부는 메모리의 새 토큰을 쓰지 않는다. */
    suspend fun write(session: StoredSession): Boolean

    /**
     * 토큰을 지운다. 여러 번 실행해도 안전하다.
     *
     * **실패를 숨기지 않는다** — false 를 받은 호출부는 정리를 완료로 처리하면 안 된다.
     */
    suspend fun clear(): Boolean
}

/**
 * 토큰 조회 결과.
 *
 * "없음"과 "손상"을 같은 null 로 뭉개면 손상이 미로그인 복원으로 처리되어 이전 사용자의 프로필
 * 캐시·제공자 상태·`device_id` 가 그대로 남는다. 그래서 네 갈래를 구분한다.
 */
sealed interface TokenReadResult {
    /** 저장된 세션이 없다. 정상적인 미로그인이다. */
    data object Absent : TokenReadResult

    data class Available(
        val session: StoredSession,
    ) : TokenReadResult

    /** 복호화·역직렬화가 실패했다(기기 이전·백업 복원·키 무효화). 전체 세션 정리가 필요하다. */
    data object Corrupt : TokenReadResult

    /** 저장소 읽기 자체가 실패했다. 손상과 달리 일시적일 수 있어 그 자리에서 세션을 폐기하지 않는다. */
    data object Unavailable : TokenReadResult
}
