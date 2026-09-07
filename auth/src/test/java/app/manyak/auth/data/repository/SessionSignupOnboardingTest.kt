package app.manyak.auth.data.repository

import app.manyak.auth.data.api.AccountApi
import app.manyak.auth.data.api.AuthApi
import app.manyak.auth.data.api.dto.LogoutRequestDto
import app.manyak.auth.data.api.dto.RefreshTokenRequestDto
import app.manyak.auth.data.api.dto.SocialLoginRequestDto
import app.manyak.auth.data.api.dto.TokenResponseDto
import app.manyak.auth.data.datastore.StoredSession
import app.manyak.auth.data.provider.ProviderCleanupResult
import app.manyak.auth.data.provider.SocialIdTokenProvider
import app.manyak.auth.data.session.ClockSnapshot
import app.manyak.auth.data.session.ProcessAnchorState
import app.manyak.auth.data.session.SessionClock
import app.manyak.auth.data.session.SessionStateHolder
import app.manyak.auth.data.session.SessionTokenManager
import app.manyak.auth.data.session.TokenReadResult
import app.manyak.auth.data.session.TokenStorage
import app.manyak.auth.domain.SessionEndSignal
import app.manyak.auth.domain.SessionGate
import app.manyak.auth.entity.SessionState
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.invite.SignupOnboardingWriter
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.common.entity.session.SessionEndNotice
import app.manyak.common.entity.user.UserProfile
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SessionSignupOnboardingTest {
    @Test
    fun `signup marker observes persisted tokens and published member state`() =
        runTest {
            val fixture = SignupFixture(backgroundScope, isNewUser = true)

            assertTrue(fixture.repository.signIn(AuthProvider.GOOGLE) is DomainResult.Success)

            assertEquals(1, fixture.markerCount)
            assertEquals(SessionState.Member, fixture.state.sessionState.value)
        }

    @Test
    fun `existing member login does not mark signup onboarding`() =
        runTest {
            val fixture = SignupFixture(backgroundScope, isNewUser = false)

            assertTrue(fixture.repository.signIn(AuthProvider.GOOGLE) is DomainResult.Success)

            assertEquals(0, fixture.markerCount)
        }

    @Test
    fun `token persistence failure does not publish member or mark onboarding`() =
        runTest {
            val fixture = SignupFixture(backgroundScope, isNewUser = true, writeSucceeds = false)

            assertTrue(fixture.repository.signIn(AuthProvider.GOOGLE) is DomainResult.Failure)

            assertEquals(0, fixture.markerCount)
            assertEquals(SessionState.Undetermined, fixture.state.sessionState.value)
            assertEquals(listOf(SessionEndNotice.TOKEN_PERSISTENCE_FAILED), fixture.notices)
        }
}

private class SignupFixture(
    scope: CoroutineScope,
    isNewUser: Boolean,
    writeSucceeds: Boolean = true,
) {
    val state = SessionStateHolder()
    var markerCount = 0
    val notices = mutableListOf<SessionEndNotice>()
    private val storage = SignupTokenStorage(writeSucceeds)
    private val gate = SessionGate()
    private val api = SignupAuthApi(isNewUser)
    private val signal: Lazy<SessionEndSignal> =
        Lazy {
            object : SessionEndSignal {
                override fun onSessionInvalidated(
                    notice: SessionEndNotice,
                    serverLogoutToken: String?,
                ) {
                    notices += notice
                }
            }
        }
    private val clock =
        object : SessionClock {
            override fun now(): ClockSnapshot = ClockSnapshot(100, 1000, 1)
        }
    private val profiles =
        object : UserProfileRepository {
            override val profile = MutableStateFlow<UserProfile?>(null)

            override suspend fun refresh(): DomainResult<UserProfile> = DomainResult.Failure(DomainError.Network)
        }
    private val marker =
        object : SignupOnboardingWriter {
            override suspend fun markPending() {
                assertTrue(storage.read() is TokenReadResult.Available)
                assertEquals(SessionState.Member, state.sessionState.value)
                markerCount += 1
            }
        }

    val repository =
        SessionRepositoryImpl(
            authApi = api,
            userApi =
                object : AccountApi {
                    override suspend fun withdraw(): Response<Unit> = error("not used")
                },
            tokenManager = SessionTokenManager(api, storage, clock, ProcessAnchorState(), gate, scope, signal),
            tokenStorage = storage,
            providers = mapOf(AuthProvider.GOOGLE to SignupProvider),
            stateHolder = state,
            gate = gate,
            sessionEndSignal = signal,
            profileRepository = profiles,
            inviteOnboarding = marker,
            applicationScope = scope,
        )
}

private class SignupTokenStorage(
    private val writeSucceeds: Boolean,
) : TokenStorage {
    private var stored: StoredSession? = null

    override suspend fun read(): TokenReadResult = stored?.let(TokenReadResult::Available) ?: TokenReadResult.Absent

    override suspend fun write(session: StoredSession): Boolean {
        if (writeSucceeds) stored = session
        return writeSucceeds
    }

    override suspend fun clear(): Boolean {
        stored = null
        return true
    }
}

private class SignupAuthApi(
    private val isNewUser: Boolean,
) : AuthApi {
    override suspend fun login(
        provider: String,
        request: SocialLoginRequestDto,
    ): Response<TokenResponseDto> = Response.success(TokenResponseDto("access", "refresh", 3600, isNewUser = isNewUser))

    override suspend fun refresh(request: RefreshTokenRequestDto): Response<TokenResponseDto> = error("not used")

    override suspend fun logout(request: LogoutRequestDto): Response<Unit> = error("not used")
}

private object SignupProvider : SocialIdTokenProvider {
    override val provider: AuthProvider = AuthProvider.GOOGLE

    override suspend fun requestIdToken(): DomainResult<String> = DomainResult.Success("fixture-id-token")

    override suspend fun requestFreshIdToken(): DomainResult<String> = error("not used")

    override suspend fun clearLocalState(): ProviderCleanupResult = error("not used")
}
