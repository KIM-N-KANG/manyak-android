package app.manyak

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.manyak.auth.domain.SessionRepository
import app.manyak.auth.domain.SignupRepository
import app.manyak.auth.entity.PendingSignup
import app.manyak.auth.entity.SessionState
import app.manyak.auth.entity.SignInOutcome
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.common.entity.consent.ConsentItem
import app.manyak.common.entity.consent.RequiredConsent
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.legal.consent.domain.ConsentRepository
import app.manyak.legal.consent.entity.ConsentStatus
import app.manyak.legal.consent.presentation.LegalConsentSheet
import app.manyak.legal.consent.presentation.LegalConsentViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LegalConsentUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun forbiddenShowsLogoutWithoutRetry() {
        val session = UiSession()
        showSheet(session, DomainResult.Failure(DomainError.AccountSuspended))
        compose.onNodeWithText("지금 계정으로는 서비스를 이용할 수 없어요").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").assertDoesNotExist()
        compose.onNodeWithText("로그아웃").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, session.logouts) }
    }

    @Test
    fun requiredConsentOffersLogoutBelowSubmit() {
        val session = UiSession()
        showSheet(session, DomainResult.Success(ConsentStatus(REQUIRED)))
        compose.onNodeWithText("동의하기").assertIsDisplayed()
        compose.onNodeWithText("로그아웃").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, session.logouts) }
    }

    @Test
    fun signupLogoutCancelsSignupWithoutSignOut() {
        val session = UiSession(SessionState.SignedOut(notice = null), PendingSignup(REQUIRED))
        showSheet(session, DomainResult.Failure(DomainError.Unknown))
        compose.onNodeWithText("동의하기").assertIsDisplayed()
        compose.onNodeWithText("로그아웃").performClick()
        compose.runOnIdle {
            assertEquals(1, session.signupCancels)
            assertEquals(0, session.logouts)
        }
    }

    @Test
    fun loadFailureOffersRetryAndLogout() {
        val session = UiSession()
        showSheet(session, DomainResult.Failure(DomainError.Network))
        compose.onNodeWithText("동의 상태를 확인하지 못했어요").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").assertIsDisplayed()
        compose.onNodeWithText("로그아웃").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, session.logouts) }
    }

    private fun showSheet(
        session: UiSession,
        result: DomainResult<ConsentStatus>,
    ) {
        val repository =
            object : ConsentRepository {
                override suspend fun get(): DomainResult<ConsentStatus> = result

                override suspend fun record(versions: Map<ConsentItem, String>): DomainResult<ConsentStatus> = get()
            }
        val viewModel = LegalConsentViewModel(repository, session, session)
        compose.setContent {
            ManyakTheme {
                LegalConsentSheet(enabled = true, viewModel = viewModel)
            }
        }
    }
}

private val REQUIRED = listOf(RequiredConsent(ConsentItem.TERMS, "v1"))

private class UiSession(
    session: SessionState = SessionState.Member,
    signup: PendingSignup? = null,
) : SessionRepository,
    SignupRepository {
    var logouts = 0
    var signupCancels = 0
    override val sessionState = MutableStateFlow(session)
    override val signInInProgress = MutableStateFlow<AuthProvider?>(null)

    override suspend fun signIn(provider: AuthProvider): DomainResult<SignInOutcome> =
        DomainResult.Failure(DomainError.Unknown)

    override suspend fun signOut() {
        logouts++
    }

    override suspend fun withdraw(): DomainResult<Unit> = DomainResult.Success(Unit)

    override suspend fun acknowledgeSessionEndNotice() = Unit

    override val pendingSignup = MutableStateFlow(signup)

    override suspend fun completeSignup(versions: Map<ConsentItem, String>): DomainResult<Unit> = error("not used")

    override fun cancelSignup() {
        signupCancels++
    }
}
