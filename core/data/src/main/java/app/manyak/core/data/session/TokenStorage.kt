package app.manyak.core.data.session

import app.manyak.core.data.datastore.StoredSession

/**
 * 토큰 보관의 계약. 구현은 Keystore 로 암호화해 DataStore 에 쓰지만, 재발급 로직은 그 사실을 몰라도 된다.
 *
 * 이 경계 덕분에 회전·저장 실패 경로를 실기기 없이 단위 테스트로 고정할 수 있다(검수 #4·#11).
 */
interface TokenStorage {
    suspend fun read(): StoredSession?

    /** 원자적으로 한 번에 쓴다. 실패하면 false 이며 호출부는 메모리의 새 토큰을 쓰지 않는다. */
    suspend fun write(session: StoredSession): Boolean

    suspend fun clear()
}
