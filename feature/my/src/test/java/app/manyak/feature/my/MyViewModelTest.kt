package app.manyak.feature.my

import app.manyak.core.domain.auth.AuthProvider
import app.manyak.core.domain.credit.AttendanceResult
import app.manyak.core.domain.credit.CreditRepository
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.session.SessionRepository
import app.manyak.core.domain.session.SessionState
import app.manyak.core.domain.settings.ThemeMode
import app.manyak.core.domain.settings.ThemePreferenceRepository
import app.manyak.core.domain.user.AccountStatus
import app.manyak.core.domain.user.UserProfile
import app.manyak.core.domain.user.UserProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 만들자마자 읽지 않는다 — 탭에 살아남는 ViewModel 이라 갱신은 화면이 시작한다. */
    @Test
    fun `생성만으로는 프로필을 읽지 않는다`() =
        runTest {
            val fixture = fixture()

            advanceUntilIdle()

            assertEquals(0, fixture.profileRepository.refreshCount)
        }

    @Test
    fun `화면이 보일 때마다 프로필을 다시 읽는다`() =
        runTest {
            val fixture = fixture()

            fixture.viewModel.onIntent(MyIntent.Refresh)
            advanceUntilIdle()

            assertEquals(1, fixture.profileRepository.refreshCount)
        }

    /** 회전 같은 구성 변경이 같은 요청을 연달아 만든다. 방금 읽었으면 다시 읽지 않는다. */
    @Test
    fun `곧바로 이어진 갱신 요청은 건너뛴다`() =
        runTest {
            val fixture = fixture()

            fixture.viewModel.onIntent(MyIntent.Refresh)
            fixture.viewModel.onIntent(MyIntent.Refresh)
            advanceUntilIdle()

            assertEquals(1, fixture.profileRepository.refreshCount)
        }

    /** 지급 결과는 반드시 보여야 하므로 방금 읽었더라도 간격을 보지 않는다. */
    @Test
    fun `출석 보상 뒤에는 간격과 무관하게 다시 읽는다`() =
        runTest {
            val fixture = fixture()

            fixture.viewModel.onIntent(MyIntent.Refresh)
            fixture.viewModel.onIntent(MyIntent.ClaimAttendance)
            advanceUntilIdle()

            assertEquals(2, fixture.profileRepository.refreshCount)
        }

    private class Fixture(
        val profileRepository: FakeUserProfileRepository,
        val viewModel: MyViewModel,
    )

    private fun fixture(): Fixture {
        val profileRepository = FakeUserProfileRepository()
        return Fixture(
            profileRepository = profileRepository,
            viewModel =
                MyViewModel(
                    FakeSessionRepository(),
                    profileRepository,
                    FakeCreditRepository(),
                    FakeThemePreferenceRepository(),
                ),
        )
    }
}

private class FakeUserProfileRepository : UserProfileRepository {
    var refreshCount: Int = 0
        private set

    private val cached = MutableStateFlow<UserProfile?>(sampleProfile())

    override val profile: StateFlow<UserProfile?> = cached.asStateFlow()

    override suspend fun refresh(): DomainResult<UserProfile> {
        refreshCount++
        val refreshed = sampleProfile()
        cached.value = refreshed
        return DomainResult.Success(refreshed)
    }
}

private class FakeCreditRepository : CreditRepository {
    override suspend fun claimAttendance(): DomainResult<AttendanceResult> =
        DomainResult.Success(AttendanceResult(rewarded = true, amount = 250))
}

private class FakeSessionRepository : SessionRepository {
    override val sessionState: StateFlow<SessionState> = MutableStateFlow(SessionState.Member)

    override val signInInProgress: StateFlow<AuthProvider?> = MutableStateFlow(null)

    override suspend fun signIn(provider: AuthProvider) = error("로그인은 이 테스트의 대상이 아니다")

    override suspend fun signOut() = Unit

    override suspend fun withdraw() = error("탈퇴는 이 테스트의 대상이 아니다")

    override suspend fun acknowledgeSessionEndNotice() = Unit
}

private class FakeThemePreferenceRepository : ThemePreferenceRepository {
    override val themeMode: Flow<ThemeMode> = MutableStateFlow(ThemeMode.SYSTEM)

    override suspend fun setThemeMode(mode: ThemeMode) = Unit
}

private fun sampleProfile(): UserProfile =
    UserProfile(
        id = "user-1",
        nickname = "낭만적인 표류자",
        profileImageUrl = null,
        profileThumbnailBase64 = null,
        status = AccountStatus.ACTIVE,
        creditBalance = 1_630,
        attendedToday = false,
        linkedProviders = listOf(AuthProvider.GOOGLE),
    )
