package app.manyak.auth.data.repository

import app.manyak.auth.data.api.AccountApi
import app.manyak.auth.data.api.AuthApi
import app.manyak.auth.data.api.dto.SignupConsentRequestDto
import app.manyak.auth.data.api.dto.SocialAuthResponseDto
import app.manyak.auth.data.api.dto.SocialLoginRequestDto
import app.manyak.auth.data.api.dto.TokenResponseDto
import app.manyak.auth.data.api.dto.requiredOrNull
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
import app.manyak.auth.domain.SignupRepository
import app.manyak.auth.entity.PendingSignup
import app.manyak.auth.entity.SessionRestoreResult
import app.manyak.auth.entity.SessionState
import app.manyak.auth.entity.SignInOutcome
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.invite.SignupOnboardingWriter
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.common.entity.consent.ConsentItem
import app.manyak.common.entity.session.SessionEndNotice
import app.manyak.network.data.api.apiCall
import app.manyak.network.data.api.emptyBodyApiCall
import dagger.Lazy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 로그인은 **SDK 인증 → 서버 로그인 → 원자 저장 → 상태 공개** 순서로만 진행한다. 필수 동의가 남았으면
 * 서버 로그인이 토큰 대신 대기 코드를 주고, 동의 제출이 성공해야 저장과 상태 공개로 넘어간다.
 *
 * 네 단계 전체가 [SessionGate] 의 인증 작업으로 실행된다 — 시작 시 세대를 캡처하고, 저장과 상태
 * 발행은 같은 잠금 안에서 세대를 다시 확인한 뒤에만 일어난다. 종료가 먼저 시작되면 아예 시작하지
 * 않고, 도중에 시작되면 취소되며, 취소되지 않는 제공자 SDK 호출이 늦게 돌아와도 커밋되지 않는다.
 */
@Suppress("TooManyFunctions") // 로그인·가입 완료·복원이 같은 토큰 저장과 상태 공개 경로를 공유한다.
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
        private val inviteOnboarding: SignupOnboardingWriter,
    ) : SessionRepository,
        SignupRepository,
        SessionBootstrap {
        private val inProgress = MutableStateFlow<AuthProvider?>(null)
        private val pending = MutableStateFlow<PendingSignup?>(null)

        /** 완료 API 전용 불투명 코드. 메모리에만 두고 로그·분석·디스크에 남기지 않는다. */
        @Volatile
        private var consentToken: String? = null

        override val pendingSignup: StateFlow<PendingSignup?> = pending.asStateFlow()

        override val sessionState: StateFlow<SessionState> = stateHolder.sessionState

        override val signInInProgress: StateFlow<AuthProvider?> = inProgress.asStateFlow()

        override suspend fun signIn(provider: AuthProvider): DomainResult<SignInOutcome> {
            val adapter =
                providers[provider]
                    ?: return DomainResult.Failure(DomainError.ProviderFailed(provider, "no-adapter"))

            // 공유 기기에서 끝내지 않은 가입이 다음 로그인에 섞이지 않게 먼저 버린다.
            cancelSignup()
            return gate.withAuthWork(onBlocked = { DomainResult.Failure(DomainError.Unauthorized) }) { work ->
                inProgress.value = provider
                try {
                    runSignIn(provider, adapter, work)
                } finally {
                    inProgress.value = null
                }
            }
        }

        override suspend fun completeSignup(versions: Map<ConsentItem, String>): DomainResult<Unit> =
            gate.withAuthWork(
                onBlocked = {
                    // 종료 절차가 끼어들어도 대기를 버린다. 남기면 시트가 제출 중으로 멈춘다.
                    cancelSignup()
                    DomainResult.Failure(DomainError.Unauthorized)
                },
            ) { work ->
                val token = consentToken ?: return@withAuthWork endSignup(work, SessionEndNotice.SIGNUP_EXPIRED)
                val request =
                    SignupConsentRequestDto(
                        terms = versions[ConsentItem.TERMS],
                        privacy = versions[ConsentItem.PRIVACY],
                        age14 = versions[ConsentItem.AGE14],
                    )
                when (val response = apiCall { authApi.completeSocial(token, request) }) {
                    is DomainResult.Success -> {
                        val result = finishSignIn(response.value, work)
                        // 서버는 토큰을 발급하며 코드를 소비했다. 저장에 실패했어도 같은 코드로 다시 보낼 수 없다.
                        // 회원 상태를 공개한 뒤에 비워야 시트 쪽이 가입 취소로 오인하지 않는다.
                        cancelSignup()
                        if (result is DomainResult.Failure) result else DomainResult.Success(Unit)
                    }
                    is DomainResult.Failure ->
                        response.error.signupEndNotice()?.let { notice -> endSignup(work, notice) } ?: response
                }
            }

        override fun cancelSignup() {
            consentToken = null
            pending.value = null
        }

        /** 소셜 인증부터 다시 해야 한다. 대기를 버리고 로그인 화면에 이유를 남긴다. */
        private suspend fun endSignup(
            work: AuthWork,
            notice: SessionEndNotice,
        ): DomainResult<Unit> {
            cancelSignup()
            gate.commit(work) { stateHolder.publishSignedOut(notice) }
            return DomainResult.Failure(DomainError.Unauthorized)
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

            val request = SocialLoginRequestDto(idToken)
            val started =
                when (val response = apiCall { authApi.startSocial(provider.wireName, request) }) {
                    is DomainResult.Success -> response.value
                    is DomainResult.Failure -> return response
                }

            return when (started.status) {
                STATUS_COMPLETED -> started.token?.let { finishSignIn(it, work) }
                STATUS_CONSENT_REQUIRED -> awaitConsent(started, work)
                else -> null
            } ?: DomainResult.Failure(DomainError.Serialization)
        }

        /** 계정과 토큰은 아직 없다. 응답을 해석할 수 없으면 null 이며 그런 응답으로 동의를 받지 않는다. */
        private suspend fun awaitConsent(
            started: SocialAuthResponseDto,
            work: AuthWork,
        ): DomainResult<SignInOutcome>? {
            val token = started.consentToken?.takeIf(String::isNotBlank) ?: return null
            val required = started.consents?.requiredOrNull()?.takeIf(List<*>::isNotEmpty) ?: return null
            return gate.commit(work) {
                consentToken = token
                pending.value = PendingSignup(required)
                // 직전 가입의 만료 안내가 새 시트 뒤에 남지 않게 지운다.
                stateHolder.clearNotice()
                DomainResult.Success(SignInOutcome.ConsentRequired)
            } ?: DomainResult.Failure(DomainError.Unauthorized)
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
            // 가입 사실만 기록한다. 안내는 필수 동의를 마친 뒤 회원 화면에서 소비한다.
            if (issued.isNewUser) inviteOnboarding.markPending()
            return DomainResult.Success(SignInOutcome.Completed(isNewUser = issued.isNewUser))
        }

        private fun readBackoffMillis(attempt: Int): Long = TOKEN_READ_BACKOFF_MILLIS shl attempt

        private companion object {
            const val STATUS_COMPLETED = "COMPLETED"
            const val STATUS_CONSENT_REQUIRED = "CONSENT_REQUIRED"
            const val TOKEN_READ_ATTEMPTS = 3
            const val TOKEN_READ_BACKOFF_MILLIS = 100L
        }
    }

/** 400 은 코드를 소비하지 않지만, 버전만 바꿔 자동으로 다시 보내지 않고 새 소셜 인증으로 현행 버전을 다시 받는다. */
private fun DomainError.signupEndNotice(): SessionEndNotice? =
    when {
        this == DomainError.Unauthorized -> SessionEndNotice.SIGNUP_EXPIRED
        this is DomainError.Server && code in signupRestartCodes -> SessionEndNotice.SIGNUP_OUTDATED
        else -> null
    }

private val signupRestartCodes = setOf("CONSENT_VERSION_MISMATCH", "CONSENT_REQUIRED_MISSING")
