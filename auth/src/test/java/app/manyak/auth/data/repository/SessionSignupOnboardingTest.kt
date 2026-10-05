package app.manyak.auth.data.repository

import app.manyak.auth.data.api.AccountApi
import app.manyak.auth.data.api.AuthApi
import app.manyak.auth.data.api.dto.ConsentStatusDto
import app.manyak.auth.data.api.dto.LogoutRequestDto
import app.manyak.auth.data.api.dto.RefreshTokenRequestDto
import app.manyak.auth.data.api.dto.SignupConsentRequestDto
import app.manyak.auth.data.api.dto.SignupConsentsDto
import app.manyak.auth.data.api.dto.SocialAuthResponseDto
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
import app.manyak.auth.entity.PendingSignup
import app.manyak.auth.entity.SessionState
import app.manyak.auth.entity.SignInOutcome
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.invite.SignupOnboardingWriter
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.common.entity.consent.ConsentItem
import app.manyak.common.entity.consent.RequiredConsent
import app.manyak.common.entity.session.SessionEndNotice
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `consent required login waits without tokens and clears the previous notice`() =
        runTest {
            val fixture = SignupFixture(backgroundScope, isNewUser = true, consentRequired = true)
            fixture.state.publishSignedOut(SessionEndNotice.SIGNUP_EXPIRED)

            val result = fixture.repository.signIn(AuthProvider.GOOGLE)

            assertEquals(DomainResult.Success(SignInOutcome.ConsentRequired), result)
            assertEquals(PendingSignup(listOf(RequiredConsent(ConsentItem.TERMS, "v1.4"))), fixture.pending)
            assertEquals(SessionState.SignedOut(null), fixture.state.sessionState.value)
            assertEquals(TokenReadResult.Absent, fixture.storage.read())
            assertEquals(0, fixture.markerCount)
        }

    @Test
    fun `completing signup sends the consent code and opens the member session`() =
        runTest {
            val fixture = SignupFixture(backgroundScope, isNewUser = true, consentRequired = true)
            fixture.repository.signIn(AuthProvider.GOOGLE)

            val result = fixture.repository.completeSignup(mapOf(ConsentItem.TERMS to "v1.4"))

            assertEquals(DomainResult.Success(Unit), result)
            assertEquals("consent-code", fixture.api.sentConsentToken)
            assertEquals(SignupConsentRequestDto(terms = "v1.4"), fixture.api.sentConsents)
            assertEquals(SessionState.Member, fixture.state.sessionState.value)
            assertNull(fixture.pending)
            assertEquals(1, fixture.markerCount)
        }

    @Test
    fun `expired consent code ends the signup with a relogin notice`() =
        runTest {
            val fixture = SignupFixture(backgroundScope, isNewUser = true, consentRequired = true)
            fixture.api.completeResponse = errorResponse(401, "CONSENT_TOKEN_INVALID")
            fixture.repository.signIn(AuthProvider.GOOGLE)

            assertTrue(fixture.repository.completeSignup(mapOf(ConsentItem.TERMS to "v1.4")) is DomainResult.Failure)

            assertNull(fixture.pending)
            assertEquals(SessionState.SignedOut(SessionEndNotice.SIGNUP_EXPIRED), fixture.state.sessionState.value)
            assertEquals(TokenReadResult.Absent, fixture.storage.read())
        }

    @Test
    fun `changed terms end the signup instead of resubmitting a new version`() =
        runTest {
            val fixture = SignupFixture(backgroundScope, isNewUser = true, consentRequired = true)
            fixture.api.completeResponse = errorResponse(400, "CONSENT_VERSION_MISMATCH")
            fixture.repository.signIn(AuthProvider.GOOGLE)

            fixture.repository.completeSignup(mapOf(ConsentItem.TERMS to "v1.4"))

            assertNull(fixture.pending)
            assertEquals(SessionState.SignedOut(SessionEndNotice.SIGNUP_OUTDATED), fixture.state.sessionState.value)
            assertEquals(1, fixture.api.completeCount)
        }

    @Test
    fun `transient failure keeps the signup for another submit`() =
        runTest {
            val fixture = SignupFixture(backgroundScope, isNewUser = true, consentRequired = true)
            fixture.api.completeResponse = errorResponse(500, null)
            fixture.state.publishSignedOut(null)
            fixture.repository.signIn(AuthProvider.GOOGLE)

            assertTrue(fixture.repository.completeSignup(mapOf(ConsentItem.TERMS to "v1.4")) is DomainResult.Failure)

            assertEquals(PendingSignup(listOf(RequiredConsent(ConsentItem.TERMS, "v1.4"))), fixture.pending)
            assertEquals(SessionState.SignedOut(null), fixture.state.sessionState.value)
        }

    @Test
    fun `new sign in discards an unfinished signup`() =
        runTest {
            val fixture = SignupFixture(backgroundScope, isNewUser = false, consentRequired = true)
            fixture.repository.signIn(AuthProvider.GOOGLE)
            fixture.api.consentRequired = false

            fixture.repository.signIn(AuthProvider.GOOGLE)

            assertNull(fixture.pending)
            assertEquals(SessionState.Member, fixture.state.sessionState.value)
        }
}

private fun errorResponse(
    status: Int,
    code: String?,
): Response<TokenResponseDto> =
    Response.error(status, """{"code":${code?.let { "\"$it\"" }}}""".toResponseBody("application/json".toMediaType()))

private class SignupFixture(
    scope: CoroutineScope,
    isNewUser: Boolean,
    writeSucceeds: Boolean = true,
    consentRequired: Boolean = false,
) {
    val state = SessionStateHolder()
    var markerCount = 0
    val notices = mutableListOf<SessionEndNotice>()
    val storage = SignupTokenStorage(writeSucceeds)
    private val gate = SessionGate()
    val api = SignupAuthApi(isNewUser, consentRequired)
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
            inviteOnboarding = marker,
        )

    val pending: PendingSignup? get() = repository.pendingSignup.value
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
    isNewUser: Boolean,
    var consentRequired: Boolean,
) : AuthApi {
    private val token = TokenResponseDto("access", "refresh", 3600, isNewUser = isNewUser)
    var completeResponse: Response<TokenResponseDto> = Response.success(token)
    var completeCount = 0
    var sentConsentToken: String? = null
    var sentConsents: SignupConsentRequestDto? = null

    override suspend fun startSocial(
        provider: String,
        request: SocialLoginRequestDto,
    ): Response<SocialAuthResponseDto> =
        Response.success(
            if (consentRequired) {
                SocialAuthResponseDto(
                    status = "CONSENT_REQUIRED",
                    consentToken = "consent-code",
                    consents =
                        SignupConsentsDto(
                            terms = ConsentStatusDto("v1.4", needsConsent = true),
                            privacy = ConsentStatusDto("v1.7", needsConsent = false),
                            age14 = ConsentStatusDto("1", needsConsent = false),
                        ),
                )
            } else {
                SocialAuthResponseDto(status = "COMPLETED", token = token)
            },
        )

    override suspend fun completeSocial(
        consentToken: String,
        request: SignupConsentRequestDto,
    ): Response<TokenResponseDto> {
        completeCount += 1
        sentConsentToken = consentToken
        sentConsents = request
        return completeResponse
    }

    override suspend fun refresh(request: RefreshTokenRequestDto): Response<TokenResponseDto> = error("not used")

    override suspend fun logout(request: LogoutRequestDto): Response<Unit> = error("not used")
}

private object SignupProvider : SocialIdTokenProvider {
    override val provider: AuthProvider = AuthProvider.GOOGLE

    override suspend fun requestIdToken(): DomainResult<String> = DomainResult.Success("fixture-id-token")

    override suspend fun requestFreshIdToken(): DomainResult<String> = error("not used")

    override suspend fun clearLocalState(): ProviderCleanupResult = error("not used")
}
