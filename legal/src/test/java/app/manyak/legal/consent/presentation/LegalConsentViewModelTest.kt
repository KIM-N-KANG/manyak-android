package app.manyak.legal.consent.presentation

import app.manyak.auth.domain.SessionRepository
import app.manyak.auth.entity.SessionState
import app.manyak.auth.entity.SignInOutcome
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.legal.consent.domain.ConsentRepository
import app.manyak.legal.consent.entity.ConsentItem
import app.manyak.legal.consent.entity.ConsentStatus
import app.manyak.legal.consent.entity.RequiredConsent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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

/** 회원 진입 판정, 체크·제출 규칙, 버전 불일치 재조회, 뒤로가기 로그아웃, 세션 교체를 고정한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class LegalConsentViewModelTest {
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
    fun `회원이 되면 조회하고 필요 항목이 있으면 시트를 띄운다`() =
        runTest {
            val viewModel = LegalConsentViewModel(FakeConsents(ALL_REQUIRED), FakeSession())
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(LegalConsentPhase.REQUIRED, state.phase)
            assertTrue(state.isSheetVisible)
            assertFalse(state.isSatisfied)
            assertEquals(ALL_REQUIRED.required, state.required)
        }

    @Test
    fun `필요 항목이 없으면 시트 없이 만족한다`() =
        runTest {
            val viewModel = LegalConsentViewModel(FakeConsents(SATISFIED), FakeSession())
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isSatisfied)
            assertNull(viewModel.uiState.value.marketingAnswer)
        }

    @Test
    fun `조회 실패는 실패 시트를 띄우고 다시 시도로 재조회한다`() =
        runTest {
            val consents = FakeConsents(ALL_REQUIRED, getResults = mutableListOf(NETWORK_FAILURE))
            val viewModel = LegalConsentViewModel(consents, FakeSession())
            advanceUntilIdle()
            assertEquals(LegalConsentPhase.LOAD_FAILED, viewModel.uiState.value.phase)

            viewModel.onIntent(LegalConsentIntent.Retry)
            advanceUntilIdle()

            assertEquals(LegalConsentPhase.REQUIRED, viewModel.uiState.value.phase)
            assertEquals(2, consents.getCount)
        }

    @Test
    fun `전체 동의는 선택 항목까지 켜고 필수만 모두 켜면 제출할 수 있다`() =
        runTest {
            val viewModel = LegalConsentViewModel(FakeConsents(ALL_REQUIRED), FakeSession())
            advanceUntilIdle()

            viewModel.onIntent(LegalConsentIntent.ToggleAll)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isAllChecked)
            assertTrue(viewModel.uiState.value.marketingOptIn)

            viewModel.onIntent(LegalConsentIntent.ToggleMarketing)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isAllChecked)
            assertTrue(viewModel.uiState.value.isEveryRequiredChecked)

            viewModel.onIntent(LegalConsentIntent.Toggle(ConsentItem.AGE14))
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isEveryRequiredChecked)
        }

    @Test
    fun `제출은 필요 항목의 요구 버전만 싣고 성공하면 만족하며 선택 항목 답을 남긴다`() =
        runTest {
            val consents = FakeConsents(ALL_REQUIRED)
            val viewModel = LegalConsentViewModel(consents, FakeSession())
            advanceUntilIdle()

            viewModel.onIntent(LegalConsentIntent.Submit)
            advanceUntilIdle()
            assertEquals(emptyList<Map<ConsentItem, String>>(), consents.records)

            viewModel.onIntent(LegalConsentIntent.ToggleAll)
            advanceUntilIdle()
            viewModel.onIntent(LegalConsentIntent.Submit)
            advanceUntilIdle()

            val expected = mapOf(ConsentItem.TERMS to "v1", ConsentItem.PRIVACY to "v2", ConsentItem.AGE14 to "1")
            assertEquals(listOf(expected), consents.records)
            assertTrue(viewModel.uiState.value.isSatisfied)
            assertEquals(true, viewModel.uiState.value.marketingAnswer)

            viewModel.onIntent(LegalConsentIntent.MarketingAnswerConsumed)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.marketingAnswer)
        }

    @Test
    fun `버전 불일치면 안내를 띄우고 재조회해 체크를 비운다`() =
        runTest {
            val mismatch = DomainResult.Failure(DomainError.Server(400, ConsentRepository.ERROR_VERSION_MISMATCH, null))
            val consents = FakeConsents(ALL_REQUIRED, recordResults = mutableListOf(mismatch))
            val viewModel = LegalConsentViewModel(consents, FakeSession())
            advanceUntilIdle()

            viewModel.onIntent(LegalConsentIntent.ToggleAll)
            advanceUntilIdle()
            viewModel.onIntent(LegalConsentIntent.Submit)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(LegalConsentNotice.VERSION_MISMATCH, state.notice)
            assertEquals(LegalConsentPhase.REQUIRED, state.phase)
            assertEquals(emptySet<ConsentItem>(), state.checked)
            assertEquals(2, consents.getCount)
        }

    @Test
    fun `기록 응답에 필요 항목이 남아도 불일치로 보고 다시 받는다`() =
        runTest {
            val remaining = ConsentStatus(listOf(RequiredConsent(ConsentItem.PRIVACY, "v3")))
            val consents = FakeConsents(ALL_REQUIRED, recordResults = mutableListOf(DomainResult.Success(remaining)))
            val viewModel = LegalConsentViewModel(consents, FakeSession())
            advanceUntilIdle()

            viewModel.onIntent(LegalConsentIntent.ToggleAll)
            advanceUntilIdle()
            viewModel.onIntent(LegalConsentIntent.Submit)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isSatisfied)
            assertEquals(LegalConsentNotice.VERSION_MISMATCH, state.notice)
            assertEquals(remaining.required, state.required)
        }

    @Test
    fun `저장 실패는 시트를 유지하고 재시도 안내를 띄운다`() =
        runTest {
            val consents = FakeConsents(ALL_REQUIRED, recordResults = mutableListOf(NETWORK_FAILURE))
            val viewModel = LegalConsentViewModel(consents, FakeSession())
            advanceUntilIdle()

            viewModel.onIntent(LegalConsentIntent.ToggleAll)
            advanceUntilIdle()
            viewModel.onIntent(LegalConsentIntent.Submit)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(LegalConsentNotice.RETRYABLE, state.notice)
            assertTrue(state.isSheetVisible)
            assertTrue(state.isAllChecked)
            assertFalse(state.isSubmitting)
        }

    @Test
    fun `뒤로가기는 로그아웃을 시작하고 잠근다`() =
        runTest {
            val session = FakeSession()
            val viewModel = LegalConsentViewModel(FakeConsents(ALL_REQUIRED), session)
            advanceUntilIdle()

            viewModel.onIntent(LegalConsentIntent.Abandon)
            advanceUntilIdle()
            viewModel.onIntent(LegalConsentIntent.Abandon)
            advanceUntilIdle()

            assertEquals(1, session.signOuts)
            assertTrue(viewModel.uiState.value.isLocked)
        }

    @Test
    fun `세션이 끝나면 상태를 비우고 다음 회원을 다시 판정한다`() =
        runTest {
            val session = FakeSession()
            val consents = FakeConsents(ALL_REQUIRED)
            val viewModel = LegalConsentViewModel(consents, session)
            advanceUntilIdle()
            viewModel.onIntent(LegalConsentIntent.ToggleAll)
            advanceUntilIdle()

            session.state.value = SessionState.SignedOut(notice = null)
            advanceUntilIdle()
            assertEquals(LegalConsentUiState(), viewModel.uiState.value)

            session.state.value = SessionState.Member
            advanceUntilIdle()

            assertEquals(2, consents.getCount)
            assertEquals(LegalConsentPhase.REQUIRED, viewModel.uiState.value.phase)
        }

    private class FakeConsents(
        private val current: ConsentStatus,
        private val getResults: MutableList<DomainResult<ConsentStatus>> = mutableListOf(),
        private val recordResults: MutableList<DomainResult<ConsentStatus>> = mutableListOf(),
    ) : ConsentRepository {
        var getCount = 0
        val records = mutableListOf<Map<ConsentItem, String>>()

        override suspend fun get(): DomainResult<ConsentStatus> {
            getCount++
            return getResults.removeFirstOrNull() ?: DomainResult.Success(current)
        }

        override suspend fun record(versions: Map<ConsentItem, String>): DomainResult<ConsentStatus> {
            records += versions
            return recordResults.removeFirstOrNull() ?: DomainResult.Success(SATISFIED)
        }
    }

    private class FakeSession : SessionRepository {
        val state = MutableStateFlow<SessionState>(SessionState.Member)
        var signOuts = 0
        override val sessionState: StateFlow<SessionState> = state
        override val signInInProgress: StateFlow<AuthProvider?> = MutableStateFlow(null)

        override suspend fun signIn(provider: AuthProvider): DomainResult<SignInOutcome> = error("unused")

        override suspend fun signOut() {
            signOuts++
        }

        override suspend fun withdraw(): DomainResult<Unit> = error("unused")

        override suspend fun acknowledgeSessionEndNotice() = Unit
    }

    private companion object {
        val ALL_REQUIRED =
            ConsentStatus(
                listOf(
                    RequiredConsent(ConsentItem.TERMS, "v1"),
                    RequiredConsent(ConsentItem.PRIVACY, "v2"),
                    RequiredConsent(ConsentItem.AGE14, "1"),
                ),
            )
        val SATISFIED = ConsentStatus(emptyList())
        val NETWORK_FAILURE = DomainResult.Failure(DomainError.Network)
    }
}
