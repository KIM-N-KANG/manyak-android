package app.manyak.core.data.session

import app.manyak.common.data.di.ApplicationScope
import app.manyak.common.entity.session.SessionEndNotice
import app.manyak.core.data.api.AuthApi
import app.manyak.core.data.api.dto.RefreshTokenRequestDto
import app.manyak.core.data.api.dto.TokenResponseDto
import app.manyak.core.data.datastore.StoredSession
import app.manyak.network.domain.SessionTokenAccess
import app.manyak.network.entity.TokenAccess
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 발급받은 토큰을 저장하려는 시도의 결과. */
enum class TokenPersistResult {
    PERSISTED,

    /** 저장이 실패했다. 서버 회전은 이미 끝났으므로 메모리의 새 토큰을 쓰면 안 된다. */
    WRITE_FAILED,

    /** 저장하려는 사이에 세션이 끝났다. 결과를 버린다. */
    SESSION_ENDED,
}

/**
 * 토큰 재발급의 단일 지점.
 *
 * - **선제 재발급** — 요청 직전에 만료가 임박했으면 먼저 재발급하고 본 요청을 보낸다.
 * - **단일 비행** — 재발급은 앱 전역에서 하나만 돈다. 병렬 재발급의 두 번째 요청은 서버의 재사용 탐지에
 *   걸려 세션 계열 전체가 폐기되고 사용자가 예고 없이 로그아웃된다.
 * - **회전 원자 저장** — 새 토큰·앵커를 한 번에 저장한 뒤에야 대기 요청을 깨운다.
 * - **저장 실패는 세션 종료** — 서버에서 구 refresh 는 이미 폐기됐으므로 메모리의 새 토큰을 쓰지 않는다.
 * - **커밋은 관문 안에서** — 저장은 [SessionGate.commit] 안에서만 일어나므로, 로그아웃이 검사와
 *   저장 사이에 끼어들어 지운 토큰이 되살아나는 일이 없다.
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
        private val gate: SessionGate,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
        private val sessionEndSignal: Lazy<SessionEndSignal>,
    ) : SessionTokenAccess {
        private val refreshMutex = Mutex()
        private var inFlightRefresh: Deferred<RefreshOutcome>? = null

        /** 요청을 보내기 전에 관찰한 세션 세대. 401 재시도가 다음 사용자의 토큰으로 실행되지 않게 한다. */
        override val currentGeneration: Long get() = gate.currentGeneration

        /** 저장된 세션을 그대로 쓸 수 있는지 판정하고, 필요하면 먼저 재발급한다. */
        override suspend fun accessToken(): TokenAccess {
            if (gate.isTerminating) return TokenAccess.SessionEnded
            return when (val read = tokenStore.read()) {
                TokenReadResult.Absent -> TokenAccess.NoSession
                // 저장소를 읽지 못한 것뿐이라 세션을 폐기하지 않는다. 이 요청만 실패시킨다.
                TokenReadResult.Unavailable -> TokenAccess.TemporarilyUnavailable
                // 손상은 토큰만의 문제가 아니다. 사용자 귀속 데이터 전체를 지우는 종료로 넘긴다.
                TokenReadResult.Corrupt ->
                    endSession(SessionEndNotice.REAUTHENTICATION_REQUIRED, null).toTokenAccess()
                is TokenReadResult.Available -> accessWith(read.session)
            }
        }

        private suspend fun accessWith(stored: StoredSession): TokenAccess {
            val freshness =
                TokenFreshnessEvaluator.evaluate(
                    anchors = stored.anchors,
                    now = clock.now(),
                    anchorVerifiedInThisProcess = anchorState.isAnchorVerifiedInThisProcess,
                )
            if (freshness == TokenFreshness.FRESH) return TokenAccess.Available(stored.accessToken)
            return refresh().toTokenAccess()
        }

        /**
         * 401 을 만난 뒤의 반응형 재발급. 재시도는 호출부가 **1회만** 한다.
         *
         * @param observedGeneration 원 요청을 보내기 전에 읽은 세대. 그 사이 세션이 바뀌었으면
         *  재발급 결과를 쓰지 않는다 — 이전 사용자의 401 처리가 새 사용자의 토큰으로 재시도되면 안 된다.
         */
        override suspend fun refreshAfterUnauthorized(observedGeneration: Long): TokenAccess {
            if (!gate.isCurrentGeneration(observedGeneration)) return TokenAccess.SessionEnded
            val outcome = refresh()
            if (!gate.isCurrentGeneration(observedGeneration)) return TokenAccess.SessionEnded
            return outcome.toTokenAccess()
        }

        /** 발급받은 토큰을 관문 안에서 저장한다. 세대가 어긋나면 저장하지 않는다. */
        suspend fun persistIssuedTokens(
            response: TokenResponseDto,
            work: AuthWork,
        ): TokenPersistResult {
            val stored =
                gate.commit(work) {
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
                    tokenStore.write(session)
                } ?: return TokenPersistResult.SESSION_ENDED
            if (stored) anchorState.markVerified()
            return if (stored) TokenPersistResult.PERSISTED else TokenPersistResult.WRITE_FAILED
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
            } catch (_: CancellationException) {
                // 호출자 자신이 취소된 것이면 그대로 전파한다.
                currentCoroutineContext().ensureActive()
                // 종료 장벽이 진행 중인 재발급을 취소했다. 이 요청은 세션 종료로 끝난다.
                RefreshOutcome.SessionEnded
            } finally {
                // 취소된 문맥에서도 단일 비행 슬롯은 비워야 다음 재발급이 막히지 않는다.
                withContext(NonCancellable) {
                    refreshMutex.withLock { if (inFlightRefresh === running) inFlightRefresh = null }
                }
            }
        }

        /** 재발급 전체가 관문 안에서 돈다. 종료가 시작됐으면 시작하지 않고, 도중에 시작되면 취소된다. */
        private suspend fun runRefresh(): RefreshOutcome =
            gate.withAuthWork(onBlocked = { RefreshOutcome.SessionEnded }) { work ->
                val stored =
                    when (val read = tokenStore.read()) {
                        is TokenReadResult.Available -> read.session
                        TokenReadResult.Unavailable -> return@withAuthWork RefreshOutcome.TransientFailure
                        TokenReadResult.Absent, TokenReadResult.Corrupt ->
                            return@withAuthWork endSession(SessionEndNotice.REAUTHENTICATION_REQUIRED, null)
                    }
                val response =
                    try {
                        authApi.refresh(RefreshTokenRequestDto(stored.refreshToken))
                    } catch (_: IOException) {
                        // 네트워크 실패로 세션을 폐기하지 않는다. 연결이 불안정한 곳에서 앱을 열 때마다 로그아웃된다.
                        return@withAuthWork RefreshOutcome.TransientFailure
                    }
                val issued = response.body()
                if (!response.isSuccessful || issued == null) return@withAuthWork response.toFailureOutcome()

                // 서버 회전은 성공했다. 여기서 저장에 실패하면 구 refresh 는 이미 폐기됐으므로
                // 메모리의 새 토큰을 쓰지 않고, 새 refresh 로 서버 로그아웃을 시도한 뒤 종료한다.
                when (persistIssuedTokens(issued, work)) {
                    TokenPersistResult.PERSISTED -> RefreshOutcome.Refreshed(issued.accessToken)
                    TokenPersistResult.WRITE_FAILED ->
                        endSession(SessionEndNotice.TOKEN_PERSISTENCE_FAILED, issued.refreshToken)
                    // 이미 종료가 시작됐다. 새 종료를 부르지 않고 결과만 버린다.
                    TokenPersistResult.SESSION_ENDED -> RefreshOutcome.SessionEnded
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
