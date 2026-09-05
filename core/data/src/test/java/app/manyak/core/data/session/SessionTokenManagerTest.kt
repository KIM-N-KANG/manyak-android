package app.manyak.core.data.session

import app.manyak.common.entity.session.SessionEndNotice
import app.manyak.core.data.api.AuthApi
import app.manyak.core.data.api.dto.LogoutRequestDto
import app.manyak.core.data.api.dto.RefreshTokenRequestDto
import app.manyak.core.data.api.dto.SocialLoginRequestDto
import app.manyak.core.data.api.dto.TokenResponseDto
import app.manyak.core.data.datastore.StoredSession
import dagger.Lazy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * 실기기 없이 재발급 경로를 고정한다.
 *
 * refresh 는 1회용이고 이미 회전된 토큰이 다시 오면 서버가 세션 계열 전체를 폐기하므로,
 * 병렬 재발급과 저장 실패 경로가 이 계층에서 결정적으로 막혀 있어야 한다.
 */
class SessionTokenManagerTest {
    @Test
    fun `동시 요청이 만료를 만나도 재발급은 한 번만 실행된다`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val api =
                FakeAuthApi {
                    gate.await()
                    Response.success(issued(access = "new-access"))
                }
            val storage = FakeTokenStorage(expiredSession())
            val manager = manager(api, storage, this)

            val waiting = List(3) { async { manager.accessToken() } }
            testScheduler.advanceUntilIdle()
            gate.complete(Unit)
            val results = waiting.map { it.await() }

            assertEquals(1, api.refreshCount)
            assertTrue(results.all { it == TokenAccess.Available("new-access") })
        }

    @Test
    fun `재발급 응답을 저장하지 못하면 새 토큰을 쓰지 않고 세션을 끝낸다`() =
        runTest {
            val api = FakeAuthApi { Response.success(issued(access = "new-access", refresh = "new-refresh")) }
            val storage = FakeTokenStorage(expiredSession()).apply { writeSucceeds = false }
            val signal = RecordingSessionEndSignal()
            val manager = manager(api, storage, this, signal)

            val results = List(2) { async { manager.accessToken() } }.map { it.await() }

            assertTrue(results.all { it == TokenAccess.SessionEnded })
            assertEquals(SessionEndNotice.TOKEN_PERSISTENCE_FAILED, signal.notices.single())
            // 서버에서 구 refresh 는 이미 폐기됐으므로 새 refresh 로 서버 로그아웃을 시도해야 한다.
            assertEquals("new-refresh", signal.serverLogoutTokens.single())
            assertEquals("old-access", (storage.read() as TokenReadResult.Available).session.accessToken)
        }

    @Test
    fun `네트워크 오류로 재발급이 실패해도 세션을 유지한다`() =
        runTest {
            val api = FakeAuthApi { throw java.io.IOException("offline") }
            val signal = RecordingSessionEndSignal()
            val manager = manager(api, FakeTokenStorage(expiredSession()), this, signal)

            assertEquals(TokenAccess.TemporarilyUnavailable, manager.accessToken())
            assertTrue(signal.notices.isEmpty())
        }

    @Test
    fun `refresh 가 401 이면 재로그인이 필요한 종료로 처리한다`() =
        runTest {
            val signal = RecordingSessionEndSignal()
            val manager = manager(FakeAuthApi { errorResponse(401) }, FakeTokenStorage(expiredSession()), this, signal)

            assertEquals(TokenAccess.SessionEnded, manager.accessToken())
            assertEquals(SessionEndNotice.REAUTHENTICATION_REQUIRED, signal.notices.single())
            assertNull(signal.serverLogoutTokens.single())
        }

    @Test
    fun `refresh 가 403 이면 정지 계정으로 구분해 종료한다`() =
        runTest {
            val signal = RecordingSessionEndSignal()
            val manager = manager(FakeAuthApi { errorResponse(403) }, FakeTokenStorage(expiredSession()), this, signal)

            assertEquals(TokenAccess.SessionEnded, manager.accessToken())
            assertEquals(SessionEndNotice.ACCOUNT_SUSPENDED, signal.notices.single())
        }

    @Test
    fun `여유가 남은 토큰은 재발급 없이 그대로 쓴다`() =
        runTest {
            val api = FakeAuthApi { Response.success(issued(access = "unused")) }
            val manager = manager(api, FakeTokenStorage(freshSession()), this)

            assertEquals(TokenAccess.Available("old-access"), manager.accessToken())
            assertEquals(0, api.refreshCount)
        }

    private fun manager(
        api: AuthApi,
        storage: FakeTokenStorage,
        scope: TestScope,
        signal: SessionEndSignal = RecordingSessionEndSignal(),
    ) = SessionTokenManager(
        authApi = api,
        tokenStore = storage,
        clock = FixedClock,
        anchorState = ProcessAnchorState(),
        gate = SessionGate(),
        applicationScope = scope.backgroundScope,
        sessionEndSignal = Lazy { signal },
    )

    private fun issued(
        access: String,
        refresh: String = "next-refresh",
    ) = TokenResponseDto(accessToken = access, refreshToken = refresh, expiresIn = 1_800)

    private fun errorResponse(code: Int): Response<TokenResponseDto> =
        Response.error(code, "{}".toResponseBody("application/json".toMediaType()))

    private fun expiredSession() = storedSession(expiresInSeconds = 10)

    private fun freshSession() = storedSession(expiresInSeconds = 1_800)

    private fun storedSession(expiresInSeconds: Long) =
        StoredSession(
            accessToken = "old-access",
            refreshToken = "old-refresh",
            anchors =
                TokenAnchors(
                    expiresInSeconds = expiresInSeconds,
                    elapsedRealtimeAnchorMillis = FixedClock.now().elapsedRealtimeMillis,
                    wallClockAnchorMillis = FixedClock.now().wallClockMillis,
                    bootGeneration = BOOT_GENERATION,
                ),
        )

    private object FixedClock : SessionClock {
        override fun now() =
            ClockSnapshot(
                elapsedRealtimeMillis = 100_000,
                wallClockMillis = 1_700_000_000_000,
                bootGeneration = BOOT_GENERATION,
            )
    }

    private class FakeTokenStorage(
        initial: StoredSession?,
    ) : TokenStorage {
        private var stored: StoredSession? = initial
        var writeSucceeds: Boolean = true

        override suspend fun read(): TokenReadResult = stored?.let(TokenReadResult::Available) ?: TokenReadResult.Absent

        override suspend fun write(session: StoredSession): Boolean {
            if (!writeSucceeds) return false
            stored = session
            return true
        }

        override suspend fun clear(): Boolean {
            stored = null
            return true
        }
    }

    private class FakeAuthApi(
        private val onRefresh: suspend () -> Response<TokenResponseDto>,
    ) : AuthApi {
        var refreshCount: Int = 0
            private set

        override suspend fun login(
            provider: String,
            request: SocialLoginRequestDto,
        ): Response<TokenResponseDto> = error("이 테스트는 로그인을 호출하지 않는다")

        override suspend fun refresh(request: RefreshTokenRequestDto): Response<TokenResponseDto> {
            refreshCount++
            return onRefresh()
        }

        override suspend fun logout(request: LogoutRequestDto): Response<Unit> = error("이 테스트는 로그아웃을 호출하지 않는다")
    }

    private class RecordingSessionEndSignal : SessionEndSignal {
        val notices = mutableListOf<SessionEndNotice>()
        val serverLogoutTokens = mutableListOf<String?>()

        override fun onSessionInvalidated(
            notice: SessionEndNotice,
            serverLogoutToken: String?,
        ) {
            notices += notice
            serverLogoutTokens += serverLogoutToken
        }
    }

    private companion object {
        const val BOOT_GENERATION = 42L
    }
}
