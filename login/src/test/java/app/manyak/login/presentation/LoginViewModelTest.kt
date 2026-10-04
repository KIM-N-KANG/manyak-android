package app.manyak.login.presentation

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.auth.domain.SessionRepository
import app.manyak.auth.entity.SessionState
import app.manyak.auth.entity.SignInOutcome
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.auth.AuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `동의를 기다리는 회원은 다시 로그인할 수 없고 로그아웃 후 버튼이 열린다`() =
        runTest(dispatcher) {
            val session = LoginSession()
            val viewModel = LoginViewModel(session, NoOpAnalytics)
            testScheduler.runCurrent()
            assertFalse(viewModel.uiState.value.canSignIn)
            viewModel.onIntent(LoginIntent.SignIn(AuthProvider.KAKAO))
            testScheduler.runCurrent()
            assertEquals(0, session.calls)
            session.sessionState.value = SessionState.SignedOut(null)
            testScheduler.runCurrent()
            assertTrue(viewModel.uiState.value.canSignIn)
            viewModel.onIntent(LoginIntent.SignIn(AuthProvider.KAKAO))
            testScheduler.runCurrent()
            assertEquals(1, session.calls)
        }
}

private class LoginSession : SessionRepository {
    override val sessionState = MutableStateFlow<SessionState>(SessionState.Member)
    override val signInInProgress = MutableStateFlow<AuthProvider?>(null)
    var calls = 0

    override suspend fun signIn(provider: AuthProvider): DomainResult<SignInOutcome> {
        calls++
        return DomainResult.Success(SignInOutcome(false))
    }

    override suspend fun signOut() = Unit

    override suspend fun withdraw(): DomainResult<Unit> = error("not used")

    override suspend fun acknowledgeSessionEndNotice() = Unit
}
