package app.manyak.notification.consent.presentation

import app.manyak.auth.domain.SessionRepository
import app.manyak.auth.entity.SessionState
import app.manyak.auth.entity.SignInOutcome
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.notification.consent.domain.MarketingConsentPromptRepository
import app.manyak.notification.consent.entity.ConsentChange
import app.manyak.notification.settings.domain.PushSettingsRepository
import app.manyak.notification.settings.entity.PushSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 시트를 띄우는 조건, 저장 요청의 모양, 거절·허용 기록 시점, 약관 시트의 선택 항목 처리를 고정한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class MarketingConsentViewModelTest {
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
    fun `물을 차례이고 서버가 미동의면 시트를 띄운다`() =
        runTest {
            val viewModel = prepared(FakePrompt(claim = true), FakeSettings(NOT_CONSENTED))

            assertTrue(viewModel.uiState.value.isSheetVisible)
        }

    @Test
    fun `물을 차례가 아니면 서버를 읽지 않고 띄우지 않는다`() =
        runTest {
            val settings = FakeSettings(NOT_CONSENTED)
            val viewModel = prepared(FakePrompt(claim = false), settings)

            assertFalse(viewModel.uiState.value.isSheetVisible)
            assertEquals(0, settings.getCount)
        }

    @Test
    fun `서버가 이미 동의 상태면 띄우지 않는다`() =
        runTest {
            val consented = FakeSettings(NOT_CONSENTED.copy(marketingPush = true))
            val viewModel = prepared(FakePrompt(claim = true), consented)

            assertFalse(viewModel.uiState.value.isSheetVisible)
        }

    @Test
    fun `허용은 서비스 값을 유지한 채 광고만 켜서 저장하고 닫힘으로 기록한 뒤 통지를 띄운다`() =
        runTest {
            val prompt = FakePrompt(claim = true)
            val settings = FakeSettings(NOT_CONSENTED.copy(servicePush = false))
            val viewModel = prepared(prompt, settings)

            viewModel.onIntent(MarketingConsentIntent.Accept)
            advanceUntilIdle()

            val expected = PushSettings(servicePush = false, marketingPush = true, marketingNightPush = false)
            assertEquals(listOf(expected), settings.updates)
            assertTrue(prompt.settled)
            assertFalse(viewModel.uiState.value.isSheetVisible)
            assertEquals(ConsentChange.MARKETING_ON, viewModel.noticeChange())
            assertTrue(viewModel.uiState.value.isBusy)
        }

    @Test
    fun `저장에 실패하면 시트를 유지하고 기록하지 않는다`() =
        runTest {
            val prompt = FakePrompt(claim = true)
            val settings = FakeSettings(NOT_CONSENTED, updateResult = { DomainResult.Failure(DomainError.Network) })
            val viewModel = prepared(prompt, settings)
            val effects = collectEffects(viewModel)

            viewModel.onIntent(MarketingConsentIntent.Accept)
            advanceUntilIdle()
            testScheduler.runCurrent()

            assertTrue(viewModel.uiState.value.isSheetVisible)
            assertFalse(viewModel.uiState.value.isSubmitting)
            assertEquals(0, prompt.declines)
            assertFalse(prompt.settled)
            assertNull(viewModel.uiState.value.notice)
            assertEquals(listOf(MarketingConsentEffect.SaveFailed), effects)
        }

    @Test
    fun `받지 않기는 저장 없이 닫고 거절로 기록한다`() =
        runTest {
            val prompt = FakePrompt(claim = true)
            val settings = FakeSettings(NOT_CONSENTED)
            val viewModel = prepared(prompt, settings)

            viewModel.onIntent(MarketingConsentIntent.Decline)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isSheetVisible)
            assertEquals(1, prompt.declines)
            assertEquals(emptyList<PushSettings>(), settings.updates)
        }

    @Test
    fun `약관 시트에서 허용하면 시트 없이 저장하고 통지하며 이번 진입에는 다시 묻지 않는다`() =
        runTest {
            val prompt = FakePrompt(claim = true)
            val settings = FakeSettings(NOT_CONSENTED)
            val viewModel = MarketingConsentViewModel(prompt, settings, FakeSession())

            viewModel.onIntent(MarketingConsentIntent.AnsweredInConsentSheet(optIn = true))
            viewModel.onIntent(MarketingConsentIntent.DismissNotice)
            viewModel.onIntent(MarketingConsentIntent.Prepare)
            advanceUntilIdle()

            assertEquals(listOf(NOT_CONSENTED.copy(marketingPush = true)), settings.updates)
            assertTrue(prompt.settled)
            assertFalse(viewModel.uiState.value.isSheetVisible)
            assertEquals(0, prompt.claims)
        }

    @Test
    fun `약관 시트에서 거절하면 첫 거절로 기록만 하고 이번 진입에는 묻지 않는다`() =
        runTest {
            val prompt = FakePrompt(claim = true)
            val settings = FakeSettings(NOT_CONSENTED)
            val viewModel = MarketingConsentViewModel(prompt, settings, FakeSession())

            viewModel.onIntent(MarketingConsentIntent.AnsweredInConsentSheet(optIn = false))
            viewModel.onIntent(MarketingConsentIntent.Prepare)
            advanceUntilIdle()

            assertEquals(1, prompt.declines)
            assertEquals(0, prompt.claims)
            assertEquals(0, settings.getCount)
            assertFalse(viewModel.uiState.value.isSheetVisible)
        }

    @Test
    fun `세션이 끝나면 상태를 비우고 다시 회원이 되면 다시 준비한다`() =
        runTest {
            val session = FakeSession()
            val prompt = FakePrompt(claim = true)
            val viewModel = MarketingConsentViewModel(prompt, FakeSettings(NOT_CONSENTED), session)
            viewModel.onIntent(MarketingConsentIntent.Prepare)
            advanceUntilIdle()

            session.state.value = SessionState.SignedOut(notice = null)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isSheetVisible)

            session.state.value = SessionState.Member
            viewModel.onIntent(MarketingConsentIntent.Prepare)
            advanceUntilIdle()

            assertEquals(2, prompt.claims)
            assertTrue(viewModel.uiState.value.isSheetVisible)
        }

    private fun MarketingConsentViewModel.noticeChange(): ConsentChange? = uiState.value.notice?.change

    private fun TestScope.prepared(
        prompt: FakePrompt,
        settings: FakeSettings,
    ): MarketingConsentViewModel {
        val viewModel = MarketingConsentViewModel(prompt, settings, FakeSession())
        viewModel.onIntent(MarketingConsentIntent.Prepare)
        advanceUntilIdle()
        return viewModel
    }

    private fun TestScope.collectEffects(viewModel: MarketingConsentViewModel): List<MarketingConsentEffect> {
        val effects = mutableListOf<MarketingConsentEffect>()
        backgroundScope.launch { viewModel.uiEffect.collect { effects += it } }
        // advanceUntilIdle 은 backgroundScope 의 작업을 돌리지 않는다. 수집기를 지금 시작시킨다.
        testScheduler.runCurrent()
        return effects
    }

    private class FakePrompt(
        private val claim: Boolean,
    ) : MarketingConsentPromptRepository {
        var claims = 0
        var declines = 0
        var settled = false

        override suspend fun claimPrompt(): Boolean {
            claims++
            return claim
        }

        override suspend fun markDeclined() {
            declines++
        }

        override suspend fun markSettled() {
            settled = true
        }
    }

    private class FakeSettings(
        private val current: PushSettings,
        private val updateResult: (PushSettings) -> DomainResult<PushSettings> = { DomainResult.Success(it) },
    ) : PushSettingsRepository {
        var getCount = 0
        val updates = mutableListOf<PushSettings>()

        override suspend fun get(): DomainResult<PushSettings> {
            getCount++
            return DomainResult.Success(current)
        }

        override suspend fun update(settings: PushSettings): DomainResult<PushSettings> {
            updates += settings
            return updateResult(settings)
        }
    }

    private class FakeSession : SessionRepository {
        val state = MutableStateFlow<SessionState>(SessionState.Member)
        override val sessionState: StateFlow<SessionState> = state
        override val signInInProgress: StateFlow<AuthProvider?> = MutableStateFlow(null)

        override suspend fun signIn(provider: AuthProvider): DomainResult<SignInOutcome> = error("unused")

        override suspend fun signOut() = Unit

        override suspend fun withdraw(): DomainResult<Unit> = error("unused")

        override suspend fun acknowledgeSessionEndNotice() = Unit
    }

    private companion object {
        val NOT_CONSENTED = PushSettings(servicePush = true, marketingPush = false, marketingNightPush = false)
    }
}
