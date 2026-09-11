package app.manyak.notification.settings.presentation

import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** 야간·광고의 종속 규칙과 저장 실패의 되돌리기를 고정한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {
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
    fun `불러온 값을 그대로 든다`() =
        runTest {
            val repository = FakeRepository(initial = ALL_ON)
            val viewModel = loaded(repository)

            assertEquals(ALL_ON, viewModel.uiState.value.settings)
            assertEquals(false, viewModel.uiState.value.isLoading)
        }

    @Test
    fun `광고를 끄면 야간도 함께 내려 한 요청으로 보낸다`() =
        runTest {
            val repository = FakeRepository(initial = ALL_ON)
            val viewModel = loaded(repository)

            viewModel.onIntent(NotificationSettingsIntent.Toggle(PushSettingKind.MARKETING))
            advanceUntilIdle()

            val expected = ALL_ON.copy(marketingPush = false, marketingNightPush = false)
            assertEquals(listOf(expected), repository.updates)
            assertEquals(expected, viewModel.uiState.value.settings)
        }

    @Test
    fun `광고가 꺼져 있으면 야간 토글은 요청을 보내지 않는다`() =
        runTest {
            val repository = FakeRepository(initial = ALL_ON.copy(marketingPush = false, marketingNightPush = false))
            val viewModel = loaded(repository)

            viewModel.onIntent(NotificationSettingsIntent.Toggle(PushSettingKind.MARKETING_NIGHT))
            advanceUntilIdle()

            assertEquals(emptyList<PushSettings>(), repository.updates)
        }

    @Test
    fun `저장에 실패하면 이전 값으로 되돌리고 실패를 알린다`() =
        runTest {
            val repository =
                FakeRepository(initial = ALL_ON, updateResult = { DomainResult.Failure(DomainError.Network) })
            val viewModel = loaded(repository)
            val effects = collectEffects(viewModel)

            viewModel.onIntent(NotificationSettingsIntent.Toggle(PushSettingKind.SERVICE))
            advanceUntilIdle()
            testScheduler.runCurrent()

            assertEquals(ALL_ON, viewModel.uiState.value.settings)
            assertEquals(listOf(NotificationSettingsEffect.SaveFailed), effects)
            assertEquals(1, repository.getCount)
        }

    /** 앱이 미리 막는 조합이라, 이 응답은 들고 있던 값이 서버와 어긋났다는 뜻이다. */
    @Test
    fun `야간 단독 400 이면 되돌린 뒤 다시 조회한다`() =
        runTest {
            val server400 =
                DomainError.Server(
                    status = 400,
                    code = PushSettingsRepository.ERROR_NIGHT_PUSH_REQUIRES_MARKETING,
                    requestId = null,
                )
            val stale = ALL_ON.copy(marketingNightPush = false)
            val repository = FakeRepository(initial = stale, updateResult = { DomainResult.Failure(server400) })
            val viewModel = loaded(repository)
            val effects = collectEffects(viewModel)
            repository.current = ALL_ON.copy(marketingPush = false, marketingNightPush = false)

            viewModel.onIntent(NotificationSettingsIntent.Toggle(PushSettingKind.MARKETING_NIGHT))
            advanceUntilIdle()
            testScheduler.runCurrent()

            assertEquals(listOf(NotificationSettingsEffect.SaveFailed), effects)
            assertEquals(2, repository.getCount)
            assertEquals(repository.current, viewModel.uiState.value.settings)
        }

    @Test
    fun `성공하면 서버가 돌려준 값으로 교체한다`() =
        runTest {
            val fromServer = ALL_ON.copy(servicePush = false, marketingNightPush = false)
            val repository = FakeRepository(initial = ALL_ON, updateResult = { DomainResult.Success(fromServer) })
            val viewModel = loaded(repository)

            viewModel.onIntent(NotificationSettingsIntent.Toggle(PushSettingKind.SERVICE))
            advanceUntilIdle()

            assertEquals(fromServer, viewModel.uiState.value.settings)
        }

    @Test
    fun `광고를 끄면 철회 통지를 띄우고 닫으면 사라진다`() =
        runTest {
            val viewModel = loaded(FakeRepository(initial = ALL_ON))

            viewModel.onIntent(NotificationSettingsIntent.Toggle(PushSettingKind.MARKETING))
            advanceUntilIdle()
            assertEquals(ConsentChange.MARKETING_OFF, viewModel.noticeChange())

            viewModel.onIntent(NotificationSettingsIntent.DismissNotice)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.notice)
        }

    @Test
    fun `야간을 켜면 야간 동의 통지를 띄운다`() =
        runTest {
            val viewModel = loaded(FakeRepository(initial = ALL_ON.copy(marketingNightPush = false)))

            viewModel.onIntent(NotificationSettingsIntent.Toggle(PushSettingKind.MARKETING_NIGHT))
            advanceUntilIdle()

            assertEquals(ConsentChange.NIGHT_ON, viewModel.noticeChange())
        }

    @Test
    fun `서비스 알림과 저장 실패는 통지하지 않는다`() =
        runTest {
            val ok = loaded(FakeRepository(initial = ALL_ON))
            ok.onIntent(NotificationSettingsIntent.Toggle(PushSettingKind.SERVICE))
            advanceUntilIdle()
            assertNull(ok.uiState.value.notice)

            val failing =
                loaded(FakeRepository(initial = ALL_ON, updateResult = { DomainResult.Failure(DomainError.Network) }))
            failing.onIntent(NotificationSettingsIntent.Toggle(PushSettingKind.MARKETING))
            advanceUntilIdle()
            assertNull(failing.uiState.value.notice)
        }

    private fun NotificationSettingsViewModel.noticeChange(): ConsentChange? = uiState.value.notice?.change

    private fun TestScope.loaded(repository: FakeRepository): NotificationSettingsViewModel {
        val viewModel = NotificationSettingsViewModel(repository)
        viewModel.onIntent(NotificationSettingsIntent.Load)
        advanceUntilIdle()
        return viewModel
    }

    private fun TestScope.collectEffects(viewModel: NotificationSettingsViewModel): List<NotificationSettingsEffect> {
        val effects = mutableListOf<NotificationSettingsEffect>()
        backgroundScope.launch { viewModel.uiEffect.collect { effects += it } }
        // advanceUntilIdle 은 backgroundScope 의 작업을 돌리지 않는다. 수집기를 지금 시작시킨다.
        testScheduler.runCurrent()
        return effects
    }

    private class FakeRepository(
        initial: PushSettings,
        private val updateResult: (PushSettings) -> DomainResult<PushSettings> = { DomainResult.Success(it) },
    ) : PushSettingsRepository {
        var current: PushSettings = initial
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
        val ALL_ON = PushSettings(servicePush = true, marketingPush = true, marketingNightPush = true)
    }
}
