package app.manyak.feature.my

import app.manyak.core.domain.credit.AttendanceResult
import app.manyak.core.domain.credit.CreditRepository
import app.manyak.core.domain.credit.CreditTransactionPage
import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.user.AccountStatus
import app.manyak.core.domain.user.UserProfile
import app.manyak.core.domain.user.UserProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/** 마이에서 옮겨 온 출석 계약. 지급 결과를 잔액에 직접 더하지 않고 프로필을 다시 읽는지 본다. */
@OptIn(ExperimentalCoroutinesApi::class)
class CreditChargeViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `출석 보상을 받으면 프로필을 다시 읽어 잔액을 맞춘다`() =
        runTest {
            val fixture = fixture()

            fixture.viewModel.onIntent(CreditChargeIntent.ClaimAttendance)
            advanceUntilIdle()

            assertEquals(1, fixture.profileRepository.refreshCount)
            assertFalse(fixture.viewModel.uiState.value.isClaimingAttendance)
        }

    @Test
    fun `출석 요청이 실패하면 다시 누를 수 있다`() =
        runTest {
            val fixture = fixture(attendance = DomainResult.Failure(DomainError.Unknown))

            fixture.viewModel.onIntent(CreditChargeIntent.ClaimAttendance)
            advanceUntilIdle()

            assertEquals(0, fixture.profileRepository.refreshCount)
            assertFalse(fixture.viewModel.uiState.value.isClaimingAttendance)
        }

    @Test
    fun `오늘 이미 출석했으면 버튼을 누를 수 없다`() =
        runTest {
            val fixture = fixture(attendedToday = true)
            advanceUntilIdle()

            assertFalse(fixture.viewModel.uiState.value.canClaimAttendance)
        }

    @Test
    fun `프로필을 읽으면 오늘 출석 여부가 상태에 실린다`() =
        runTest {
            val fixture = fixture()
            advanceUntilIdle()

            assertTrue(fixture.viewModel.uiState.value.canClaimAttendance)
        }

    @Test
    fun `무료 충전 탭을 당기면 프로필만 다시 읽어 출석 여부를 맞춘다`() =
        runTest {
            val fixture = fixture(attendedToday = false)
            fixture.viewModel.onIntent(CreditChargeIntent.RefreshProfile)
            advanceUntilIdle()

            // 내역 조회 없이 프로필만 다시 읽고, 갱신된 출석 여부가 상태에 실린다.
            assertEquals(1, fixture.profileRepository.refreshCount)
            assertEquals(true, fixture.viewModel.uiState.value.attendedToday)
            assertEquals(false, fixture.viewModel.uiState.value.isRefreshingProfile)
            assertEquals(false, fixture.viewModel.uiState.value.isRefreshing)
        }

    private class Fixture(
        val profileRepository: FakeChargeProfileRepository,
        val viewModel: CreditChargeViewModel,
    )

    private fun fixture(
        attendedToday: Boolean = false,
        attendance: DomainResult<AttendanceResult> =
            DomainResult.Success(AttendanceResult(rewarded = true, amount = 700)),
    ): Fixture {
        val profileRepository = FakeChargeProfileRepository(attendedToday)
        return Fixture(
            profileRepository = profileRepository,
            viewModel = CreditChargeViewModel(FakeChargeCreditRepository(attendance), profileRepository),
        )
    }
}

private class FakeChargeCreditRepository(
    private val attendance: DomainResult<AttendanceResult>,
) : CreditRepository {
    override suspend fun claimAttendance(): DomainResult<AttendanceResult> = attendance

    override suspend fun getTransactions(cursor: String?): DomainResult<CreditTransactionPage> =
        DomainResult.Success(CreditTransactionPage(items = emptyList(), nextCursor = null))
}

private class FakeChargeProfileRepository(
    attendedToday: Boolean,
) : UserProfileRepository {
    var refreshCount = 0
        private set

    private val cached = MutableStateFlow<UserProfile?>(profile(attendedToday))

    override val profile: StateFlow<UserProfile?> = cached.asStateFlow()

    override suspend fun refresh(): DomainResult<UserProfile> {
        refreshCount++
        val refreshed = profile(attendedToday = true)
        cached.value = refreshed
        return DomainResult.Success(refreshed)
    }

    private fun profile(attendedToday: Boolean) =
        UserProfile(
            id = "user-1",
            nickname = "낭만적인 표류자",
            profileImageUrl = null,
            profileThumbnailBase64 = null,
            status = AccountStatus.ACTIVE,
            creditBalance = 3230,
            attendedToday = attendedToday,
            linkedProviders = emptyList(),
        )
}
