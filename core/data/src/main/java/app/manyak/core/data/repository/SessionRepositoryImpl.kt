package app.manyak.core.data.repository

import app.manyak.core.data.api.AuthApi
import app.manyak.core.data.api.apiCall
import app.manyak.core.data.api.dto.SocialLoginRequestDto
import app.manyak.core.data.api.dto.TokenResponseDto
import app.manyak.core.data.di.ApplicationScope
import app.manyak.core.data.provider.SocialIdTokenProvider
import app.manyak.core.data.session.SessionBootstrap
import app.manyak.core.data.session.SessionStateHolder
import app.manyak.core.data.session.SessionTerminator
import app.manyak.core.data.session.SessionTokenManager
import app.manyak.core.data.session.TokenStorage
import app.manyak.core.domain.auth.AuthProvider
import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.session.SessionEndNotice
import app.manyak.core.domain.session.SessionRepository
import app.manyak.core.domain.session.SessionState
import app.manyak.core.domain.session.SignInOutcome
import app.manyak.core.domain.user.UserProfileRepository
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 로그인은 **SDK 인증 → 서버 로그인 → 원자 저장 → 상태 공개** 순서로만 진행한다.
 *
 * 각 단계 사이에 종료가 끼어들 수 있으므로 저장·공개 직전에 세션 세대를 다시 확인한다.
 */
@Singleton
class SessionRepositoryImpl
    @Inject
    constructor(
        private val authApi: AuthApi,
        private val tokenManager: SessionTokenManager,
        private val tokenStorage: TokenStorage,
        private val providers: Map<AuthProvider, @JvmSuppressWildcards SocialIdTokenProvider>,
        private val stateHolder: SessionStateHolder,
        private val terminator: Lazy<SessionTerminator>,
        private val profileRepository: UserProfileRepository,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) : SessionRepository,
        SessionBootstrap {
        override val sessionState: StateFlow<SessionState> = stateHolder.sessionState

        override suspend fun signIn(provider: AuthProvider): DomainResult<SignInOutcome> {
            val adapter =
                providers[provider]
                    ?: return DomainResult.Failure(DomainError.ProviderFailed(provider, "no-adapter"))
            val generation = stateHolder.currentGeneration

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

            return finishSignIn(issued, generation)
        }

        override suspend fun signOut() {
            terminator.get().terminate(SessionEndNotice.USER_REQUESTED)
        }

        override suspend fun acknowledgeSessionEndNotice() {
            stateHolder.clearNotice()
        }

        /** 앱 시작 시 한 번 호출한다. 회원 판정의 근거는 저장된 토큰이지 `/auth/me` 성공이 아니다. */
        override suspend fun restore() {
            if (tokenStorage.read() == null) {
                stateHolder.publishSignedOut(null)
            } else {
                stateHolder.publishMember()
                applicationScope.launch { profileRepository.refresh() }
            }
        }

        private suspend fun finishSignIn(
            issued: TokenResponseDto,
            generation: Long,
        ): DomainResult<SignInOutcome> {
            // 로그인 도중 종료가 시작됐다면 그 결과로 세션을 되살리지 않는다.
            if (!stateHolder.isCurrentGeneration(generation)) {
                return DomainResult.Failure(DomainError.Unknown)
            }
            if (!tokenManager.persistIssuedTokens(issued)) {
                // 서버에는 세션이 생겼지만 로컬에 남기지 못했다. 메모리 토큰을 쓰지 않고 정리한다.
                terminator.get().terminate(SessionEndNotice.TOKEN_PERSISTENCE_FAILED, issued.refreshToken)
                return DomainResult.Failure(DomainError.Unknown)
            }
            stateHolder.publishMember()
            applicationScope.launch { profileRepository.refresh() }
            return DomainResult.Success(SignInOutcome(isNewUser = issued.isNewUser))
        }
    }
