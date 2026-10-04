package app.manyak

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.manyak.auth.domain.SessionRepository
import app.manyak.auth.entity.SessionState
import app.manyak.auth.entity.SignInOutcome
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.legal.consent.domain.ConsentRepository
import app.manyak.legal.consent.entity.ConsentItem
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
        val repository =
            object : ConsentRepository {
                override suspend fun get(): DomainResult<ConsentStatus> =
                    DomainResult.Failure(DomainError.AccountSuspended)

                override suspend fun record(versions: Map<ConsentItem, String>): DomainResult<ConsentStatus> = get()
            }
        val viewModel = LegalConsentViewModel(repository, session)
        compose.setContent {
            ManyakTheme {
                LegalConsentSheet(enabled = true, viewModel = viewModel)
            }
        }
        compose.onNodeWithText("지금 계정으로는 서비스를 이용할 수 없어요").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").assertDoesNotExist()
        compose.onNodeWithText("로그아웃").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, session.logouts) }
    }
}

private class UiSession : SessionRepository {
    var logouts = 0
    override val sessionState = MutableStateFlow<SessionState>(SessionState.Member)
    override val signInInProgress = MutableStateFlow<AuthProvider?>(null)

    override suspend fun signIn(provider: AuthProvider): DomainResult<SignInOutcome> =
        DomainResult.Failure(DomainError.Unknown)

    override suspend fun signOut() {
        logouts++
    }

    override suspend fun withdraw(): DomainResult<Unit> = DomainResult.Success(Unit)

    override suspend fun acknowledgeSessionEndNotice() = Unit
}
