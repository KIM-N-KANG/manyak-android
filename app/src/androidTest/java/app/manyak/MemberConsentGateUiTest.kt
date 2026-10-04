package app.manyak

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.espresso.Espresso.pressBack
import app.manyak.analytics.domain.NoOpAnalytics
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
import app.manyak.legal.consent.entity.RequiredConsent
import app.manyak.legal.consent.presentation.LegalConsentPhase
import app.manyak.legal.consent.presentation.LegalConsentSheet
import app.manyak.legal.consent.presentation.LegalConsentViewModel
import app.manyak.login.presentation.LoginScreen
import app.manyak.login.presentation.LoginViewModel
import app.manyak.root.MemberConsentGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MemberConsentGateUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val session = GateSession()
    private val consents = GateConsents()
    private var memberStarts = 0
    private var loginStarts = 0
    private var exits = 0

    @Test
    fun consentStaysOnLoginThroughRestoreAndSaveFailure() {
        val restoration = showGate()
        consents.response.complete(DomainResult.Success(required))
        compose.onNodeWithText("서비스 이용을 위해 동의가 필요해요").assertIsDisplayed()
        compose.onNodeWithText("동의하기").assertIsNotEnabled()
        assertNoMember()
        compose.onNodeWithText("전체 동의").performClick()
        compose.onNodeWithText("전체 동의").assertIsOn()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("전체 동의").assertIsOn()
        assertNoMember()

        compose.onNodeWithText("동의하기").performClick()
        compose.onNodeWithText("동의를 저장하지 못했어요. 잠시 후 다시 시도해주세요").assertIsDisplayed()
        assertNoMember()
        consents.saveSucceeds = true
        compose.onNodeWithText("동의하기").performClick()
        compose.onNodeWithText("회원 홈").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, memberStarts) }
    }

    @Test
    fun loadFailureAndForbiddenNeverOpenMemberContent() {
        showGate()
        consents.response.complete(DomainResult.Failure(DomainError.Network))
        compose.onNodeWithText("동의 상태를 확인하지 못했어요").assertIsDisplayed()
        assertNoMember()
        consents.response = CompletableDeferred(DomainResult.Failure(DomainError.AccountSuspended))
        compose.onNodeWithText("다시 시도").performClick()
        compose.onNodeWithText("지금 계정으로는 서비스를 이용할 수 없어요").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").assertDoesNotExist()
        compose.onNodeWithText("로그아웃").performClick()
        compose.onNodeWithText("카카오로 시작하기").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, session.logouts) }
        assertNoMember()
    }

    @Test
    fun checkingLocksLoginAndBackSignsOutWithoutOpeningHome() {
        showGate()
        compose.onNodeWithText("카카오로 시작하기").assertIsNotEnabled()
        compose.onNodeWithText("Google로 시작하기").assertIsNotEnabled()
        assertNoMember()
        pressBack()
        compose.runOnIdle { assertEquals(1, session.logouts) }
        consents.response.complete(DomainResult.Success(ConsentStatus(emptyList())))
        assertNoMember()
    }

    @Test
    fun alreadyConsentedMemberOpensHomeAfterServerCheck() {
        showGate()
        assertNoMember()
        consents.response.complete(DomainResult.Success(ConsentStatus(emptyList())))
        compose.onNodeWithText("회원 홈").assertIsDisplayed()
        compose.onNodeWithText("서비스 이용을 위해 동의가 필요해요").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, memberStarts) }
    }

    @Test
    fun restoredMemberChecksAndOpensHomeWithoutLogin() {
        val restoration = showGate(isStartup = true)
        assertNoLogin()
        assertNoMember()
        restoration.emulateSavedInstanceStateRestore()
        assertNoLogin()
        compose.runOnIdle { assertEquals(1, consents.loads) }
        pressBack()
        compose.runOnIdle {
            assertEquals(1, exits)
            assertEquals(0, session.logouts)
        }
        consents.response.complete(DomainResult.Success(ConsentStatus(emptyList())))
        compose.onNodeWithText("회원 홈").assertIsDisplayed()
        assertNoLogin()
    }

    @Test
    fun startupFailureRetriesOnStartupAndForbiddenOffersLogout() {
        showGate(isStartup = true)
        consents.response.complete(DomainResult.Failure(DomainError.Network))
        compose.onNodeWithText("동의 상태를 확인하지 못했어요").assertIsDisplayed()
        assertNoLogin()
        assertNoMember()
        consents.response = CompletableDeferred()
        compose.onNodeWithText("다시 시도").performClick()
        compose.onNodeWithText("동의 상태를 확인하지 못했어요").assertDoesNotExist()
        assertNoLogin()
        consents.response.complete(DomainResult.Failure(DomainError.AccountSuspended))
        compose.onNodeWithText("지금 계정으로는 서비스를 이용할 수 없어요").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").assertDoesNotExist()
        assertNoLogin()
        compose.onNodeWithText("로그아웃").performClick()
        compose.onNodeWithText("카카오로 시작하기").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, session.logouts) }
        assertNoMember()
    }

    @Test
    fun requiredConsentOnStartupPreservesChecksAndEntersHomeAfterSave() {
        val restoration = showGate(isStartup = true)
        consents.response.complete(DomainResult.Success(required))
        compose.onNodeWithText("서비스 이용을 위해 동의가 필요해요").assertIsDisplayed()
        assertNoLogin()
        compose.onNodeWithText("전체 동의").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("전체 동의").assertIsOn()
        assertNoLogin()
        assertNoMember()
        consents.saveSucceeds = true
        compose.onNodeWithText("동의하기").performClick()
        compose.onNodeWithText("회원 홈").assertIsDisplayed()
        assertNoLogin()
    }

    private fun showGate(isStartup: Boolean = false): StateRestorationTester {
        val consentViewModel = LegalConsentViewModel(consents, session)
        val loginViewModel = LoginViewModel(session, NoOpAnalytics)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val state by consentViewModel.uiState.collectAsStateWithLifecycle()
            val confirmed by consents.confirmed.collectAsStateWithLifecycle()
            val sessionState by session.sessionState.collectAsStateWithLifecycle()
            ManyakTheme {
                BackHandler { exits++ }
                if (sessionState == SessionState.Member) {
                    MemberConsentGate(
                        state = state,
                        consentConfirmed = confirmed,
                        onIntent = consentViewModel::onIntent,
                        isStartup = isStartup,
                        loginContent = {
                            LaunchedEffect(Unit) { loginStarts++ }
                            LoginScreen(onOpenLegalDocument = {}, viewModel = loginViewModel)
                        },
                        consentContent = {
                            LegalConsentSheet(
                                enabled = !isStartup || state.phase == LegalConsentPhase.REQUIRED,
                                viewModel = consentViewModel,
                            )
                        },
                    ) {
                        LaunchedEffect(Unit) { memberStarts++ }
                        Text("회원 홈")
                    }
                } else {
                    LoginScreen(onOpenLegalDocument = {}, viewModel = loginViewModel)
                }
            }
        }
        return restoration
    }

    private fun assertNoMember() {
        compose.onNodeWithText("회원 홈").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, memberStarts) }
    }

    private fun assertNoLogin() {
        compose.onNodeWithText("카카오로 시작하기").assertDoesNotExist()
        compose.onNodeWithText("Google로 시작하기").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, loginStarts) }
    }
}

private val required = ConsentStatus(ConsentItem.entries.map { RequiredConsent(it, "v1") })

private class GateConsents : ConsentRepository {
    var loads = 0
    var response = CompletableDeferred<DomainResult<ConsentStatus>>()
    val confirmed = MutableStateFlow(false)
    var saveSucceeds = false

    override suspend fun get(): DomainResult<ConsentStatus> {
        loads++
        return response.await().also { confirmed.value = it is DomainResult.Success && it.value.isSatisfied }
    }

    override suspend fun record(versions: Map<ConsentItem, String>): DomainResult<ConsentStatus> =
        if (saveSucceeds) {
            confirmed.value = true
            DomainResult.Success(ConsentStatus(emptyList()))
        } else {
            DomainResult.Failure(DomainError.Network)
        }
}

private class GateSession : SessionRepository {
    override val sessionState = MutableStateFlow<SessionState>(SessionState.Member)
    override val signInInProgress = MutableStateFlow<AuthProvider?>(null)
    var logouts = 0

    override suspend fun signIn(provider: AuthProvider): DomainResult<SignInOutcome> = error("not used")

    override suspend fun signOut() {
        logouts++
        sessionState.value = SessionState.SignedOut(null)
    }

    override suspend fun withdraw(): DomainResult<Unit> = error("not used")

    override suspend fun acknowledgeSessionEndNotice() = Unit
}
