package app.manyak.core.data.session

import app.manyak.core.data.api.AuthApi
import app.manyak.core.data.api.dto.RefreshTokenRequestDto
import app.manyak.core.data.api.dto.TokenResponseDto
import app.manyak.core.data.datastore.StoredSession
import app.manyak.core.data.di.ApplicationScope
import app.manyak.core.domain.session.SessionEndNotice
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

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

/**
 * 토큰 재발급의 단일 지점.
 *
 * - **선제 재발급** — 요청 직전에 만료가 임박했으면 먼저 재발급하고 본 요청을 보낸다.
 * - **단일 비행** — 재발급은 앱 전역에서 하나만 돈다. 병렬 재발급의 두 번째 요청은 서버의 재사용 탐지에
 *   걸려 세션 계열 전체가 폐기되고 사용자가 예고 없이 로그아웃된다.
 * - **회전 원자 저장** — 새 토큰·앵커를 한 번에 저장한 뒤에야 대기 요청을 깨운다.
 * - **저장 실패는 세션 종료** — 서버에서 구 refresh 는 이미 폐기됐으므로 메모리의 새 토큰을 쓰지 않는다.
 *
 * 재발급 작업은 앱 스코프에서 돈다. 화면 이탈로 취소되면 대기 중인 다른 요청까지 함께 죽는다.
 */
@Singleton
class SessionTokenManager
    @Inject
    constructor(
        private val authApi: AuthApi,
        private val tokenStore: TokenStorage,
        private val clock: SessionClock,
        private val anchorState: ProcessAnchorState,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
        private val sessionEndSignal: Lazy<SessionEndSignal>,
    ) {
        private val refreshMutex = Mutex()
        private var inFlightRefresh: Deferred<RefreshOutcome>? = null

        /** 저장된 세션을 그대로 쓸 수 있는지 판정하고, 필요하면 먼저 재발급한다. */
        suspend fun accessToken(): TokenAccess {
            val stored = tokenStore.read() ?: return TokenAccess.NoSession
            val freshness =
                TokenFreshnessEvaluator.evaluate(
                    anchors = stored.anchors,
                    now = clock.now(),
                    anchorVerifiedInThisProcess = anchorState.isAnchorVerifiedInThisProcess,
                )
            if (freshness == TokenFreshness.FRESH) return TokenAccess.Available(stored.accessToken)
            return refresh().toTokenAccess()
        }

        /** 401 을 만난 뒤의 반응형 재발급. 재시도는 호출부가 **1회만** 한다. */
        suspend fun refreshAfterUnauthorized(): TokenAccess = refresh().toTokenAccess()

        /** 저장된 refresh 토큰. 서버 로그아웃 호출에만 쓰고 그 밖으로 내보내지 않는다. */
        suspend fun refreshTokenForLogout(): String? = tokenStore.read()?.refreshToken

        suspend fun persistIssuedTokens(response: TokenResponseDto): Boolean {
            val snapshot = clock.now()
            val session =
                StoredSession(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    anchors =
                        TokenAnchors(
                            expiresInSeconds = response.expiresIn,
                            elapsedRealtimeAnchorMillis = snapshot.elapsedRealtimeMillis,
                            wallClockAnchorMillis = snapshot.wallClockMillis,
                            bootGeneration = snapshot.bootGeneration,
                        ),
                )
            val stored = tokenStore.write(session)
            if (stored) anchorState.markVerified()
            return stored
        }

        private suspend fun refresh(): RefreshOutcome {
            val running =
                refreshMutex.withLock {
                    inFlightRefresh ?: applicationScope
                        .async { runRefresh() }
                        .also { inFlightRefresh = it }
                }
            return try {
                running.await()
            } finally {
                refreshMutex.withLock { if (inFlightRefresh === running) inFlightRefresh = null }
            }
        }

        private suspend fun runRefresh(): RefreshOutcome {
            val stored = tokenStore.read() ?: return endSession(SessionEndNotice.REAUTHENTICATION_REQUIRED, null)
            val response =
                try {
                    authApi.refresh(RefreshTokenRequestDto(stored.refreshToken))
                } catch (_: IOException) {
                    // 네트워크 실패로 세션을 폐기하지 않는다. 연결이 불안정한 곳에서 앱을 열 때마다 로그아웃된다.
                    return RefreshOutcome.TransientFailure
                }
            val issued = response.body()
            if (!response.isSuccessful || issued == null) return response.toFailureOutcome()

            // 서버 회전은 성공했다. 여기서 저장에 실패하면 구 refresh 는 이미 폐기됐으므로
            // 메모리의 새 토큰을 쓰지 않고, 새 refresh 로 서버 로그아웃을 시도한 뒤 종료한다.
            return if (persistIssuedTokens(issued)) {
                RefreshOutcome.Refreshed(issued.accessToken)
            } else {
                endSession(SessionEndNotice.TOKEN_PERSISTENCE_FAILED, issued.refreshToken)
            }
        }

        private fun Response<TokenResponseDto>.toFailureOutcome(): RefreshOutcome =
            when (code()) {
                HTTP_UNAUTHORIZED -> endSession(SessionEndNotice.REAUTHENTICATION_REQUIRED, null)
                HTTP_FORBIDDEN -> endSession(SessionEndNotice.ACCOUNT_SUSPENDED, null)
                // 5xx 나 예상하지 못한 응답으로 세션을 폐기하지 않는다. 이 요청만 실패시킨다.
                else -> RefreshOutcome.TransientFailure
            }

        private fun endSession(
            notice: SessionEndNotice,
            serverLogoutToken: String?,
        ): RefreshOutcome {
            sessionEndSignal.get().onSessionInvalidated(notice, serverLogoutToken)
            return RefreshOutcome.SessionEnded
        }

        private fun RefreshOutcome.toTokenAccess(): TokenAccess =
            when (this) {
                is RefreshOutcome.Refreshed -> TokenAccess.Available(accessToken)
                RefreshOutcome.TransientFailure -> TokenAccess.TemporarilyUnavailable
                RefreshOutcome.SessionEnded -> TokenAccess.SessionEnded
            }

        private companion object {
            const val HTTP_UNAUTHORIZED = 401
            const val HTTP_FORBIDDEN = 403
        }
    }

sealed interface RefreshOutcome {
    data class Refreshed(
        val accessToken: String,
    ) : RefreshOutcome

    /** 네트워크 실패. 세션을 유지한다. */
    data object TransientFailure : RefreshOutcome

    /** 재로그인이 필요하다. 종료 신호는 이미 보냈다. */
    data object SessionEnded : RefreshOutcome
}
