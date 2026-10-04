package app.manyak.my.invite.presentation.onboarding

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.common.entity.user.UserProfile
import app.manyak.my.invite.domain.InviteOnboardingRepository
import app.manyak.my.invite.domain.InviteRepository
import app.manyak.my.invite.entity.Invite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class InviteOnboardingViewModelTest {
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
    fun `보상 지급 후 닫기 기록 실패는 지급 실패와 구분하고 중복 제출하지 않는다`() =
        runTest(dispatcher) {
            val invite = FakeInviteRepository()
            val onboarding = FakeOnboardingRepository(false)
            val vm = InviteOnboardingViewModel(invite, onboarding, FakeProfileRepository(), NoOpAnalytics)
            advanceUntilIdle()
            vm.onIntent(InviteOnboardingIntent.CodeChanged("ABC12345"))
            advanceUntilIdle()
            vm.onIntent(InviteOnboardingIntent.Submit)
            advanceUntilIdle()
            assertEquals(InviteOnboardingEffect.Redeemed(closeFailed = true), vm.uiEffect.first())
            assertTrue(vm.uiState.value.dismissed)
            assertFalse(vm.uiState.value.isSubmitting)
            assertTrue(onboarding.pending.value)
            vm.onIntent(InviteOnboardingIntent.Submit)
            advanceUntilIdle()
            assertEquals(1, invite.redeemCount)
        }

    @Test
    fun `보상과 닫기 기록 성공은 정상 지급 안내를 보낸다`() =
        runTest(dispatcher) {
            val onboarding = FakeOnboardingRepository(true)
            val vm =
                InviteOnboardingViewModel(FakeInviteRepository(), onboarding, FakeProfileRepository(), NoOpAnalytics)
            advanceUntilIdle()
            vm.onIntent(InviteOnboardingIntent.CodeChanged("ABC12345"))
            advanceUntilIdle()
            vm.onIntent(InviteOnboardingIntent.Submit)
            advanceUntilIdle()
            assertEquals(InviteOnboardingEffect.Redeemed(closeFailed = false), vm.uiEffect.first())
            assertFalse(onboarding.pending.value)
            assertFalse(vm.uiState.value.isVisible)
        }

    @Test
    fun `건너뛰기 저장 실패는 보상 안내 없이 기록 실패를 알린다`() =
        runTest(dispatcher) {
            val invite = FakeInviteRepository()
            val vm =
                InviteOnboardingViewModel(
                    invite,
                    FakeOnboardingRepository(false),
                    FakeProfileRepository(),
                    NoOpAnalytics,
                )
            advanceUntilIdle()
            vm.onIntent(InviteOnboardingIntent.Skip)
            advanceUntilIdle()
            assertEquals(InviteOnboardingEffect.DismissFailed, vm.uiEffect.first())
            assertFalse(vm.uiState.value.isVisible)
            assertEquals(0, invite.redeemCount)
        }
}

private class FakeOnboardingRepository(
    private val canAcknowledge: Boolean,
) : InviteOnboardingRepository {
    override val pending = MutableStateFlow(true)

    override suspend fun markPending() {
        pending.value = true
    }

    override suspend fun acknowledge(): Boolean {
        if (canAcknowledge) pending.value = false
        return canAcknowledge
    }
}

private class FakeInviteRepository : InviteRepository {
    var redeemCount = 0

    override suspend fun getMyInvite(): DomainResult<Invite> = DomainResult.Failure(DomainError.Network)

    override suspend fun redeemInviteCode(code: String): DomainResult<Unit> {
        redeemCount++
        return DomainResult.Success(Unit)
    }
}

private class FakeProfileRepository : UserProfileRepository {
    override val profile = MutableStateFlow<UserProfile?>(null)

    override suspend fun refresh(): DomainResult<UserProfile> = DomainResult.Failure(DomainError.Network)
}
