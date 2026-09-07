package app.manyak.auth.data.repository

import app.manyak.auth.data.api.AccountApi
import app.manyak.auth.data.api.AuthApi
import app.manyak.auth.data.api.dto.SocialLoginRequestDto
import app.manyak.auth.data.api.dto.TokenResponseDto
import app.manyak.auth.data.provider.SocialIdTokenProvider
import app.manyak.auth.data.session.SessionStateHolder
import app.manyak.auth.data.session.SessionTokenManager
import app.manyak.auth.data.session.TokenPersistResult
import app.manyak.auth.data.session.TokenReadResult
import app.manyak.auth.data.session.TokenStorage
import app.manyak.auth.domain.AuthWork
import app.manyak.auth.domain.SessionBootstrap
import app.manyak.auth.domain.SessionEndSignal
import app.manyak.auth.domain.SessionGate
import app.manyak.auth.domain.SessionRepository
import app.manyak.auth.entity.SessionRestoreResult
import app.manyak.auth.entity.SessionState
import app.manyak.auth.entity.SignInOutcome
import app.manyak.common.data.di.ApplicationScope
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.errorOrNull
import app.manyak.common.domain.invite.SignupOnboardingWriter
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.common.entity.session.SessionEndNotice
import app.manyak.network.data.api.apiCall
import app.manyak.network.data.api.emptyBodyApiCall
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 로그인은 **SDK 인증 → 서버 로그인 → 원자 저장 → 상태 공개** 순서로만 진행한다.
 *
 * 네 단계 전체가 [SessionGate] 의 인증 작업으로 실행된다 — 시작 시 세대를 캡처하고, 저장과 상태
 * 발행은 같은 잠금 안에서 세대를 다시 확인한 뒤에만 일어난다. 종료가 먼저 시작되면 아예 시작하지
 * 않고, 도중에 시작되면 취소되며, 취소되지 않는 제공자 SDK 호출이 늦게 돌아와도 커밋되지 않는다.
 */
@Singleton
class SessionRepositoryImpl
    @Inject
    constructor(
        private val authApi: AuthApi,
        private val userApi: AccountApi,
        private val tokenManager: SessionTokenManager,
        private val tokenStorage: TokenStorage,
        private val providers: Map<AuthProvider, @JvmSuppressWildcards SocialIdTokenProvider>,
        private val stateHolder: SessionStateHolder,
        private val gate: SessionGate,
        private val sessionEndSignal: Lazy<SessionEndSignal>,
        private val profileRepository: UserProfileRepository,
        private val inviteOnboarding: SignupOnboardingWriter,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) : SessionRepository,
        SessionBootstrap {
        private val inProgress = MutableStateFlow<AuthProvider?>(null)

        override val sessionState: StateFlow<SessionState> = stateHolder.sessionState

        override val signInInProgress: StateFlow<AuthProvider?> = inProgress.asStateFlow()

        override suspend fun signIn(provider: AuthProvider): DomainResult<SignInOutcome> {
            val adapter =
                providers[provider]
                    ?: return DomainResult.Failure(DomainError.ProviderFailed(provider, "no-adapter"))

            return gate.withAuthWork(onBlocked = { DomainResult.Failure(DomainError.Unauthorized) }) { work ->
                inProgress.value = provider
                try {
                    runSignIn(provider, adapter, work)
                } finally {
                    inProgress.value = null
                }
            }
        }

        override suspend fun signOut() {
            // 종료를 여기서 기다리지 않는다. 이 코루틴이 인증 작업이면 장벽이 자기 자신을 기다리게 된다.
            sessionEndSignal.get().onSessionInvalidated(SessionEndNotice.USER_REQUESTED, null)
        }

        override suspend fun withdraw(): DomainResult<Unit> {
            val result = emptyBodyApiCall { userApi.withdraw() }
            // 실패하면 세션을 그대로 둔다 — 계정이 남았는데 기기에서만 로그아웃되면 안 된다.
            if (result is DomainResult.Success) signOut()
            return result
        }

        override suspend fun acknowledgeSessionEndNotice() {
            stateHolder.clearNotice()
        }

        /**
         * 앱 시작 시 한 번 호출한다. 회원 판정의 근거는 저장된 토큰이지 `/auth/me` 성공이 아니다.
         *
         * 판정만 하고 정리를 시작하지는 않는다 — 종료 조정자는 `:app` 이 소유하므로 결과를 올려
         * 그쪽이 결정하게 한다.
         */
        override suspend fun restore(): SessionRestoreResult =
            gate.withAuthWork(onBlocked = { SessionRestoreResult.CLEANUP_REQUIRED }, block = ::restoreSession)

        private suspend fun restoreSession(work: AuthWork): SessionRestoreResult {
            repeat(TOKEN_READ_ATTEMPTS) { attempt ->
                when (tokenStorage.read()) {
                    TokenReadResult.Absent -> return publishRestored(work, isMember = false)
                    is TokenReadResult.Available -> return publishRestored(work, isMember = true)
                    // 손상은 토큰만의 문제가 아니다. 프로필 캐시·제공자 상태·device_id 까지 함께 지워야 한다.
                    TokenReadResult.Corrupt -> return SessionRestoreResult.CLEANUP_REQUIRED
                    // 읽기 실패는 일시적일 수 있다. 유한하게 다시 읽어 본 뒤에야 정리로 넘긴다.
                    TokenReadResult.Unavailable -> delay(readBackoffMillis(attempt))
                }
            }
            // 저장소를 끝내 읽지 못했다. 토큰을 쓸 수 없는 세션이므로 정리로 넘긴다.
            return SessionRestoreResult.CLEANUP_REQUIRED
        }

        /** 상태 공개도 관문을 지난다. 복원 도중 종료가 시작됐다면 어느 그래프도 열지 않는다. */
        private suspend fun publishRestored(
            work: AuthWork,
            isMember: Boolean,
        ): SessionRestoreResult {
            gate.commit(work) {
                if (isMember) stateHolder.publishMember() else stateHolder.publishSignedOut(null)
            } ?: return SessionRestoreResult.CLEANUP_REQUIRED
            if (isMember) applicationScope.launch { refreshProfile() }
            return if (isMember) SessionRestoreResult.MEMBER else SessionRestoreResult.NO_SESSION
        }

        private suspend fun runSignIn(
            provider: AuthProvider,
            adapter: SocialIdTokenProvider,
            work: AuthWork,
        ): DomainResult<SignInOutcome> {
            val idToken =
                when (val authenticated = adapter.requestIdToken()) {
                    is DomainResult.Success -> authenticated.value
                    is DomainResult.Failure -> return authenticated
                }

            val issued =
                when (val response = apiCall { authApi.login(provider.wireName, SocialLoginRequestDto(idToken)) }) {
                    is DomainResult.Success -> response.value
                    is DomainResult.Failure -> return response
                }

            return finishSignIn(issued, work)
        }

        private suspend fun finishSignIn(
            issued: TokenResponseDto,
            work: AuthWork,
        ): DomainResult<SignInOutcome> {
            when (tokenManager.persistIssuedTokens(issued, work)) {
                TokenPersistResult.PERSISTED -> Unit
                TokenPersistResult.WRITE_FAILED -> {
                    // 서버에는 세션이 생겼지만 로컬에 남기지 못했다. 메모리 토큰을 쓰지 않고 정리한다.
                    sessionEndSignal
                        .get()
                        .onSessionInvalidated(SessionEndNotice.TOKEN_PERSISTENCE_FAILED, issued.refreshToken)
                    return DomainResult.Failure(DomainError.Unknown)
                }
                // 로그인 도중 종료가 시작됐다. 그 결과로 세션을 되살리지 않는다.
                TokenPersistResult.SESSION_ENDED -> return DomainResult.Failure(DomainError.Unauthorized)
            }
            // 상태 발행도 같은 관문을 지난다. 저장 직후 로그아웃이 끼어들면 회원 상태를 공개하지 않는다.
            gate.commit(work) { stateHolder.publishMember() }
                ?: return DomainResult.Failure(DomainError.Unauthorized)
            applicationScope.launch { refreshProfile() }
            // 신규 가입 안내는 로그인 화면이 아니라 회원 그래프에서 뜬다 — 로그인 성공과 동시에 인증
            // 백스택이 사라지므로, 여기서 표시를 남겨 두고 안내를 본 뒤에 지운다.
            if (issued.isNewUser) inviteOnboarding.markPending()
            return DomainResult.Success(SignInOutcome(isNewUser = issued.isNewUser))
        }

        /**
         * 로그인 직후·앱 시작 복원의 프로필 확인.
         *
         * 정지 계정은 프로필 갱신 성공이 아니라 **세션 종료 사유**다. 조회 실패는 세션을 바꾸지 않는다.
         */
        private suspend fun refreshProfile() {
            if (profileRepository.refresh().errorOrNull() == DomainError.AccountSuspended) {
                sessionEndSignal.get().onSessionInvalidated(SessionEndNotice.ACCOUNT_SUSPENDED, null)
            }
        }

        private fun readBackoffMillis(attempt: Int): Long = TOKEN_READ_BACKOFF_MILLIS shl attempt

        private companion object {
            const val TOKEN_READ_ATTEMPTS = 3
            const val TOKEN_READ_BACKOFF_MILLIS = 100L
        }
    }
