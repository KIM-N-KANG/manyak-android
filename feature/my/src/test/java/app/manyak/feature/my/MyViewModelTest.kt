package app.manyak.feature.my

import app.manyak.core.domain.auth.AccountLinkRepository
import app.manyak.core.domain.auth.AuthProvider
import app.manyak.core.domain.credit.AttendanceResult
import app.manyak.core.domain.credit.CreditRepository
import app.manyak.core.domain.credit.CreditTransactionPage
import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.session.SessionRepository
import app.manyak.core.domain.session.SessionState
import app.manyak.core.domain.settings.ThemeMode
import app.manyak.core.domain.settings.ThemePreferenceRepository
import app.manyak.core.domain.user.AccountStatus
import app.manyak.core.domain.user.UserProfile
import app.manyak.core.domain.user.UserProfileRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import org.junit.Assert.assertTrue
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

    /** 재인증이 현재 제공자로 진행된다는 예고가 확인의 핵심이라 버튼만으로 시작하지 않는다. */
    @Test
    fun `연동 버튼은 확인 다이얼로그만 연다`() =
        runTest {
            val fixture = fixture()

            fixture.viewModel.onIntent(MyIntent.RequestAccountLink(AuthProvider.KAKAO))
            advanceUntilIdle()

            assertEquals(AuthProvider.KAKAO, fixture.viewModel.uiState.value.accountLinkTarget)
            assertEquals(emptyList<Pair<AuthProvider, AuthProvider>>(), fixture.accountLink.calls)
        }

    @Test
    fun `확인하면 이미 연동된 제공자로 재인증하고 대상을 연동한다`() =
        runTest {
            val fixture = fixture()

            confirmLink(fixture, AuthProvider.KAKAO)
            advanceUntilIdle()

            assertEquals(listOf(AuthProvider.GOOGLE to AuthProvider.KAKAO), fixture.accountLink.calls)
            assertNull(fixture.viewModel.uiState.value.accountLinkTarget)
        }

    /** 연동 결과의 정본은 프로필이다. 칩을 낙관적으로 더하지 않고 다시 읽는다. */
    @Test
    fun `연동에 성공하면 성공 안내와 함께 프로필을 다시 읽는다`() =
        runTest {
            val fixture = fixture()
            val effects = mutableListOf<MyEffect>()
            val collection = launch { fixture.viewModel.uiEffect.collect { effects += it } }

            confirmLink(fixture, AuthProvider.KAKAO)
            advanceUntilIdle()

            assertEquals(listOf(MyEffect.AccountLinked(AuthProvider.KAKAO)), effects)
            assertEquals(1, fixture.profileRepository.refreshCount)
            collection.cancel()
        }

    @Test
    fun `이미 연동된 계정은 토스트와 재조회로 끝난다`() =
        runTest {
            val fixture = fixture()
            fixture.accountLink.result = conflict("PROVIDER_ALREADY_LINKED")
            val effects = mutableListOf<MyEffect>()
            val collection = launch { fixture.viewModel.uiEffect.collect { effects += it } }

            confirmLink(fixture, AuthProvider.KAKAO)
            advanceUntilIdle()

            assertEquals(listOf(MyEffect.AccountAlreadyLinked), effects)
            assertEquals(1, fixture.profileRepository.refreshCount)
            collection.cancel()
        }

    /** 사라지는 토스트로는 "그럼 어떻게 하나"에 답할 수 없어 다이얼로그로 안내한다. */
    @Test
    fun `다른 마냑 계정에 연동된 계정은 다이얼로그로 안내한다`() =
        runTest {
            val fixture = fixture()
            fixture.accountLink.result = conflict("SOCIAL_ACCOUNT_LINKED_TO_OTHER_USER")
            val effects = mutableListOf<MyEffect>()
            val collection = launch { fixture.viewModel.uiEffect.collect { effects += it } }

            confirmLink(fixture, AuthProvider.KAKAO)
            advanceUntilIdle()

            assertTrue(fixture.viewModel.uiState.value.showsLinkedToOtherUserNotice)
            assertEquals(emptyList<MyEffect>(), effects)
            collection.cancel()
        }

    @Test
    fun `제공자 창을 스스로 닫으면 실패 안내를 띄우지 않는다`() =
        runTest {
            val fixture = fixture()
            fixture.accountLink.result = DomainResult.Failure(DomainError.ProviderCancelled)
            val effects = mutableListOf<MyEffect>()
            val collection = launch { fixture.viewModel.uiEffect.collect { effects += it } }

            confirmLink(fixture, AuthProvider.KAKAO)
            advanceUntilIdle()

            assertEquals(emptyList<MyEffect>(), effects)
            collection.cancel()
        }

    @Test
    fun `그 밖의 실패는 실패 안내를 낸다`() =
        runTest {
            val fixture = fixture()
            fixture.accountLink.result = DomainResult.Failure(DomainError.Network)
            val effects = mutableListOf<MyEffect>()
            val collection = launch { fixture.viewModel.uiEffect.collect { effects += it } }

            confirmLink(fixture, AuthProvider.KAKAO)
            advanceUntilIdle()

            assertEquals(listOf(MyEffect.AccountLinkFailed), effects)
            collection.cancel()
        }

    /** 빠른 연속 확인이 제공자 창을 두 번 열면 안 된다. */
    @Test
    fun `진행 중에는 연동을 다시 시작하지 않는다`() =
        runTest {
            val fixture = fixture()
            val gate = CompletableDeferred<Unit>()
            fixture.accountLink.gate = gate

            fixture.viewModel.onIntent(MyIntent.RequestAccountLink(AuthProvider.KAKAO))
            advanceUntilIdle()
            fixture.viewModel.onIntent(MyIntent.ConfirmAccountLink)
            fixture.viewModel.onIntent(MyIntent.ConfirmAccountLink)
            advanceUntilIdle()

            assertEquals(1, fixture.accountLink.calls.size)
            gate.complete(Unit)
            advanceUntilIdle()
        }

    private class Fixture(
        val profileRepository: FakeUserProfileRepository,
        val accountLink: FakeAccountLinkRepository,
        val viewModel: MyViewModel,
    )

    /** 사용자는 다이얼로그가 뜬 뒤에 확인을 누른다. 그 사이를 비우지 않으면 대상이 아직 상태에 없다. */
    private fun TestScope.confirmLink(
        fixture: Fixture,
        target: AuthProvider,
    ) {
        fixture.viewModel.onIntent(MyIntent.RequestAccountLink(target))
        advanceUntilIdle()
        fixture.viewModel.onIntent(MyIntent.ConfirmAccountLink)
    }

    private fun conflict(code: String): DomainResult<Unit> =
        DomainResult.Failure(DomainError.Server(status = 409, code = code, requestId = null))

    private fun fixture(): Fixture {
        val profileRepository = FakeUserProfileRepository()
        val accountLink = FakeAccountLinkRepository()
        return Fixture(
            profileRepository = profileRepository,
            accountLink = accountLink,
            viewModel =
                MyViewModel(
                    FakeSessionRepository(),
                    profileRepository,
                    FakeCreditRepository(),
                    FakeThemePreferenceRepository(),
                    accountLink,
                ),
        )
    }
}

private class FakeAccountLinkRepository : AccountLinkRepository {
    val calls = mutableListOf<Pair<AuthProvider, AuthProvider>>()
    var result: DomainResult<Unit> = DomainResult.Success(Unit)

    /** 진행 중 재확인을 검증할 때만 채운다. 완료 전까지 연동이 끝나지 않는다. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun link(
        current: AuthProvider,
        target: AuthProvider,
    ): DomainResult<Unit> {
        calls += current to target
        gate?.await()
        return result
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

    override suspend fun getTransactions(cursor: String?): DomainResult<CreditTransactionPage> =
        error("이프 내역은 이 테스트의 대상이 아니다")
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
