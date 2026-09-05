package app.manyak.core.data.datastore

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.manyak.common.data.di.IoDispatcher
import app.manyak.core.data.crypto.KeystoreCipher
import app.manyak.core.data.di.AuthTokenDataStore
import app.manyak.core.data.session.TokenAnchors
import app.manyak.core.data.session.TokenReadResult
import app.manyak.core.data.session.TokenStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** 저장된 세션. 토큰과 만료 판정 근거는 항상 함께 읽고 함께 쓴다. */
data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val anchors: TokenAnchors,
)

/**
 * 토큰과 시계 앵커를 Keystore 로 암호화해 DataStore 에 보관한다.
 *
 * 토큰 쌍·`expiresIn`·두 시계 앵커·부팅 세대는 **한 번의 원자 갱신으로 함께 기록한다**.
 * 그래서 레코드 전체를 한 덩어리로 직렬화·암호화해 키 하나에 쓴다 — 일부만 새 값인 상태가 나올 수 없다.
 *
 * 토큰은 이 모듈 밖으로 나가지 않는다. 상위 계층은 세션 상태만 본다.
 */
@Singleton
class AuthTokenStore
    @Inject
    constructor(
        @param:AuthTokenDataStore private val dataStore: DataStore<Preferences>,
        private val cipher: KeystoreCipher,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : TokenStorage {
        /**
         * 손상을 여기서 지우지 않고 [TokenReadResult.Corrupt] 로 알린다.
         *
         * 기기 이전·백업 복원·키 무효화로 복호화가 실패할 수 있다. 이때 토큰만 지우고 "저장된 세션
         * 없음"으로 돌려주면 이전 사용자의 프로필 캐시·제공자 상태·`device_id` 가 남은 채 인증 화면이
         * 열린다. 폐기 범위를 정하는 것은 세션 종료 흐름의 몫이므로 판정만 올린다.
         *
         * 읽기 자체의 실패는 손상과 구분해 [TokenReadResult.Unavailable] 로 돌려준다 — 예외를
         * 그대로 올리면 앱 시작 코루틴이 죽어 상태가 미확정에 갇힌다.
         */
        override suspend fun read(): TokenReadResult =
            withContext(ioDispatcher) {
                val encoded =
                    runCatching { dataStore.data.first()[SESSION_KEY] }
                        .getOrElse { return@withContext TokenReadResult.Unavailable }
                        ?: return@withContext TokenReadResult.Absent
                val decoded =
                    runCatching { Base64.decode(encoded, Base64.NO_WRAP) }
                        .getOrNull()
                        ?.let(cipher::decrypt)
                        ?.let(::decodePayload)
                        ?: return@withContext TokenReadResult.Corrupt
                TokenReadResult.Available(decoded.toStoredSession())
            }

        /** 원자적으로 한 번에 쓴다. 암호화나 쓰기가 실패하면 false 이며, 호출부는 세션을 종료해야 한다. */
        override suspend fun write(session: StoredSession): Boolean =
            withContext(ioDispatcher) {
                val payload = StoredSessionPayload.from(session)
                val plain = json.encodeToString(payload).encodeToByteArray()
                val encrypted = cipher.encrypt(plain) ?: return@withContext false
                runCatching {
                    dataStore.edit { preferences ->
                        preferences[SESSION_KEY] = Base64.encodeToString(encrypted, Base64.NO_WRAP)
                    }
                }.isSuccess
            }

        /** 토큰과 모든 만료 판정 앵커를 지운다. 여러 번 실행해도 안전하고, 실패는 그대로 돌려준다. */
        override suspend fun clear(): Boolean =
            withContext(ioDispatcher) {
                val removed = runCatching { dataStore.edit { it.remove(SESSION_KEY) } }.isSuccess
                // 레코드가 남아 있는데 키만 지우면 다음 읽기가 손상으로 잡혀 정리가 다시 돈다.
                if (removed) cipher.destroyKey()
                removed
            }

        private fun decodePayload(bytes: ByteArray): StoredSessionPayload? =
            runCatching { json.decodeFromString<StoredSessionPayload>(bytes.decodeToString()) }.getOrNull()

        /**
         * 저장 형식. 기본값을 두지 않아 필드가 하나라도 없으면 역직렬화가 실패한다 —
         * 필드 누락·손상은 만료로 간주해야 하므로 조용히 기본값으로 메우면 안 된다.
         */
        @Serializable
        private data class StoredSessionPayload(
            val accessToken: String,
            val refreshToken: String,
            val expiresInSeconds: Long,
            val elapsedRealtimeAnchorMillis: Long,
            val wallClockAnchorMillis: Long,
            /** null 은 저장 당시 `BOOT_COUNT` 를 읽지 못했다는 명시적 표식이다. */
            val bootGeneration: Long?,
        ) {
            fun toStoredSession(): StoredSession =
                StoredSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    anchors =
                        TokenAnchors(
                            expiresInSeconds = expiresInSeconds,
                            elapsedRealtimeAnchorMillis = elapsedRealtimeAnchorMillis,
                            wallClockAnchorMillis = wallClockAnchorMillis,
                            bootGeneration = bootGeneration,
                        ),
                )

            companion object {
                fun from(session: StoredSession): StoredSessionPayload =
                    StoredSessionPayload(
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken,
                        expiresInSeconds = session.anchors.expiresInSeconds,
                        elapsedRealtimeAnchorMillis = session.anchors.elapsedRealtimeAnchorMillis,
                        wallClockAnchorMillis = session.anchors.wallClockAnchorMillis,
                        bootGeneration = session.anchors.bootGeneration,
                    )
            }
        }

        private companion object {
            val SESSION_KEY = stringPreferencesKey("session")
            val json = Json { encodeDefaults = true }
        }
    }
