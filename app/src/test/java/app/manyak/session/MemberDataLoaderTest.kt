package app.manyak.session

import app.manyak.auth.domain.SessionEndSignal
import app.manyak.auth.domain.SessionRepository
import app.manyak.auth.entity.SessionState
import app.manyak.auth.entity.SignInOutcome
import app.manyak.common.domain.credit.TrialsRepository
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.session.MemberConsent
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.common.entity.credit.Trials
import app.manyak.common.entity.session.SessionEndNotice
import app.manyak.common.entity.user.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MemberDataLoaderTest {
    @Test
    fun `동의 전 자동 조회는 없고 확인 뒤 한 번 조회하며 다음 계정은 다시 기다린다`() =
        runTest {
            val fixture = DataFixture(backgroundScope)
            fixture.loader.start()
            testScheduler.runCurrent()
            assertEquals(0, fixture.profile.calls)
            assertEquals(0, fixture.trials.calls)

            fixture.consent.isSatisfied.value = true
            testScheduler.runCurrent()
            assertEquals(1, fixture.profile.calls)
            assertEquals(1, fixture.trials.calls)
            fixture.consent.isSatisfied.value = true
            testScheduler.runCurrent()
            assertEquals(1, fixture.profile.calls)

            fixture.session.sessionState.value = SessionState.SignedOut(null)
            fixture.consent.isSatisfied.value = false
            testScheduler.runCurrent()
            fixture.session.sessionState.value = SessionState.Member
            testScheduler.runCurrent()
            assertEquals(1, fixture.profile.calls)
            fixture.consent.isSatisfied.value = true
            testScheduler.runCurrent()
            assertEquals(2, fixture.profile.calls)
            assertEquals(2, fixture.trials.calls)
        }

    @Test
    fun `동의 상태가 초기화되면 진행 중인 자동 조회를 취소한다`() =
        runTest {
            val fixture = DataFixture(backgroundScope)
            fixture.profile.wait = true
            fixture.trials.wait = true
            fixture.consent.isSatisfied.value = true
            fixture.loader.start()
            testScheduler.runCurrent()
            fixture.consent.isSatisfied.value = false
            testScheduler.runCurrent()
            assertEquals(1, fixture.profile.cancelled)
            assertEquals(1, fixture.trials.cancelled)
        }

    @Test
    fun `세션 종료도 조회를 취소하고 프로필 네트워크 오류는 세션을 끝내지 않는다`() =
        runTest {
            val fixture = DataFixture(backgroundScope)
            fixture.trials.wait = true
            fixture.consent.isSatisfied.value = true
            fixture.loader.start()
            testScheduler.runCurrent()
            assertEquals(emptyList<SessionEndNotice>(), fixture.notices)
            fixture.session.sessionState.value = SessionState.Undetermined
            testScheduler.runCurrent()
            assertEquals(1, fixture.trials.cancelled)
        }

    @Test
    fun `동의 후 프로필 조회의 정지 계정 응답은 중앙 종료로 전달한다`() =
        runTest {
            val fixture = DataFixture(backgroundScope)
            fixture.profile.error = DomainError.AccountSuspended
            fixture.consent.isSatisfied.value = true
            fixture.loader.start()
            testScheduler.runCurrent()
            assertEquals(listOf(SessionEndNotice.ACCOUNT_SUSPENDED), fixture.notices)
        }
}

private class DataFixture(
    scope: CoroutineScope,
) {
    val session = DataSession()
    val consent = DataConsent()
    val profile = DataProfile()
    val trials = DataTrials()
    val notices = mutableListOf<SessionEndNotice>()
    val loader =
        MemberDataLoader(
            session,
            consent,
            profile,
            trials,
            object : SessionEndSignal {
                override fun onSessionInvalidated(
                    notice: SessionEndNotice,
                    serverLogoutToken: String?,
                ) {
                    notices += notice
                }
            },
            scope,
        )
}

private class DataConsent : MemberConsent {
    override val isSatisfied = MutableStateFlow(false)
}

private class DataSession : SessionRepository {
    override val sessionState = MutableStateFlow<SessionState>(SessionState.Member)
    override val signInInProgress = MutableStateFlow<AuthProvider?>(null)

    override suspend fun signIn(provider: AuthProvider): DomainResult<SignInOutcome> = error("not used")

    override suspend fun signOut() = Unit

    override suspend fun withdraw(): DomainResult<Unit> = error("not used")

    override suspend fun acknowledgeSessionEndNotice() = Unit
}

private class DataProfile : UserProfileRepository {
    override val profile = MutableStateFlow<UserProfile?>(null)
    var calls = 0
    var cancelled = 0
    var wait = false
    var error: DomainError = DomainError.Network

    override suspend fun refresh(): DomainResult<UserProfile> {
        calls++
        if (wait) {
            try {
                awaitCancellation()
            } finally {
                cancelled++
            }
        }
        return DomainResult.Failure(error)
    }
}

private class DataTrials : TrialsRepository {
    override val trials = MutableStateFlow<Trials?>(null)
    var calls = 0
    var cancelled = 0
    var wait = false

    override suspend fun refresh() {
        calls++
        if (wait) {
            try {
                awaitCancellation()
            } finally {
                cancelled++
            }
        }
    }
}
