package app.manyak.notification.consent.presentation

import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.notification.consent.domain.MarketingConsentPromptRepository
import app.manyak.notification.consent.entity.ConsentChange
import app.manyak.notification.settings.domain.PushSettingsRepository
import app.manyak.notification.settings.entity.PushSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** 시트를 띄우는 조건, 저장 요청의 모양, "물었다" 기록 시점을 고정한다. */
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
    fun `물은 적이 없고 서버가 미동의면 시트를 띄운다`() =
        runTest {
            val viewModel = prepared(FakePrompt(prompted = false), FakeSettings(NOT_CONSENTED))

            assertTrue(viewModel.uiState.value.isSheetVisible)
        }

    @Test
    fun `이미 물었으면 서버를 읽지 않고 띄우지 않는다`() =
        runTest {
            val settings = FakeSettings(NOT_CONSENTED)
            val viewModel = prepared(FakePrompt(prompted = true), settings)

            assertFalse(viewModel.uiState.value.isSheetVisible)
            assertEquals(0, settings.getCount)
        }

    @Test
    fun `서버가 이미 동의 상태면 띄우지 않는다`() =
        runTest {
            val consented = FakeSettings(NOT_CONSENTED.copy(marketingPush = true))
            val viewModel = prepared(FakePrompt(prompted = false), consented)

            assertFalse(viewModel.uiState.value.isSheetVisible)
        }

    @Test
    fun `허용은 서비스 값을 유지한 채 광고만 켜서 저장하고 통지를 띄운다`() =
        runTest {
            val prompt = FakePrompt(prompted = false)
            val settings = FakeSettings(NOT_CONSENTED.copy(servicePush = false))
            val viewModel = prepared(prompt, settings)

            viewModel.onIntent(MarketingConsentIntent.Accept)
            advanceUntilIdle()

            val expected = PushSettings(servicePush = false, marketingPush = true, marketingNightPush = false)
            assertEquals(listOf(expected), settings.updates)
            assertTrue(prompt.prompted)
            assertFalse(viewModel.uiState.value.isSheetVisible)
            assertEquals(ConsentChange.MARKETING_ON, viewModel.noticeChange())
        }

    @Test
    fun `저장에 실패하면 시트를 유지하고 물었다고 기록하지 않는다`() =
        runTest {
            val prompt = FakePrompt(prompted = false)
            val settings = FakeSettings(NOT_CONSENTED, updateResult = { DomainResult.Failure(DomainError.Network) })
            val viewModel = prepared(prompt, settings)
            val effects = collectEffects(viewModel)

            viewModel.onIntent(MarketingConsentIntent.Accept)
            advanceUntilIdle()
            testScheduler.runCurrent()

            assertTrue(viewModel.uiState.value.isSheetVisible)
            assertFalse(viewModel.uiState.value.isSubmitting)
            assertFalse(prompt.prompted)
            assertNull(viewModel.uiState.value.notice)
            assertEquals(listOf(MarketingConsentEffect.SaveFailed), effects)
        }

    @Test
    fun `받지 않기는 저장 없이 닫고 물었다고 기록한다`() =
        runTest {
            val prompt = FakePrompt(prompted = false)
            val settings = FakeSettings(NOT_CONSENTED)
            val viewModel = prepared(prompt, settings)

            viewModel.onIntent(MarketingConsentIntent.Decline)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isSheetVisible)
            assertTrue(prompt.prompted)
            assertEquals(emptyList<PushSettings>(), settings.updates)
        }

    private fun MarketingConsentViewModel.noticeChange(): ConsentChange? = uiState.value.notice?.change

    private fun TestScope.prepared(
        prompt: FakePrompt,
        settings: FakeSettings,
    ): MarketingConsentViewModel {
        val viewModel = MarketingConsentViewModel(prompt, settings)
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
        var prompted: Boolean,
    ) : MarketingConsentPromptRepository {
        override suspend fun wasPrompted(): Boolean = prompted

        override suspend fun markPrompted() {
            prompted = true
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

    private companion object {
        val NOT_CONSENTED = PushSettings(servicePush = true, marketingPush = false, marketingNightPush = false)
    }
}
